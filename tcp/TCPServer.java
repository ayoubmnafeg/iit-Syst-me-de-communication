import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TCPServer extends JFrame {
    private JTextArea messageArea;
    private JTextField portField;
    private JButton startButton, stopButton;
    private JLabel statusLabel;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread serverThread;

    // Track connected users: username -> ClientHandler
    private Map<String, ClientHandler> connectedUsers = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_TIMEOUT = 60000; // 60 seconds timeout
    private Thread heartbeatThread;

    public TCPServer() {
        setTitle("TCP Server");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top Panel - Server Controls
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("Server Configuration"));

        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("9876", 8);
        topPanel.add(portField);

        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        topPanel.add(startButton);
        topPanel.add(stopButton);

        add(topPanel, BorderLayout.NORTH);

        // Center Panel - Message Display
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Messages"));

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(messageArea);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Bottom Panel - Status
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Server stopped");
        statusLabel.setForeground(Color.RED);
        bottomPanel.add(statusLabel);

        add(bottomPanel, BorderLayout.SOUTH);

        // Button Actions
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        setLocationRelativeTo(null);
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(portField.getText());
            serverSocket = new ServerSocket(port);
            isRunning = true;

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);
            statusLabel.setText("Server running on port " + port);
            statusLabel.setForeground(new Color(0, 150, 0));

            appendMessage("=== Server started on port " + port + " ===\n");

            // Start heartbeat monitoring thread to detect disconnections
            heartbeatThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Thread.sleep(5000); // Check every 5 seconds

                        // Check for timed out users
                        List<String> timedOutUsers = new ArrayList<>();
                        for (ClientHandler handler : connectedUsers.values()) {
                            if (handler.isTimedOut()) {
                                timedOutUsers.add(handler.getUsername());
                            }
                        }

                        // Remove timed out users and notify others
                        for (String username : timedOutUsers) {
                            ClientHandler handler = connectedUsers.remove(username);
                            if (handler != null) {
                                handler.close();
                            }
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] User '" + username + "' disconnected (timeout)\n");

                            // Notify all remaining users about the disconnection
                            String disconnectMsg = "*** " + username + " left the chat ***";
                            broadcastToAllUsers(disconnectMsg);

                            // Send updated user list to all remaining clients
                            broadcastUserList();
                        }
                    } catch (InterruptedException e) {
                        if (isRunning) {
                            appendMessage("Heartbeat thread interrupted: " + e.getMessage() + "\n");
                        }
                    }
                }
            });
            heartbeatThread.start();

            // Server thread to accept new connections
            serverThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // Create a new handler thread for this client
                        ClientHandler handler = new ClientHandler(clientSocket);
                        new Thread(handler).start();
                    } catch (SocketException e) {
                        if (!isRunning) break;
                    } catch (IOException e) {
                        if (isRunning) {
                            appendMessage("Error accepting connection: " + e.getMessage() + "\n");
                        }
                    }
                }
            });
            serverThread.start();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number!", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not start server: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        isRunning = false;

        // Close all client connections
        for (ClientHandler handler : connectedUsers.values()) {
            handler.close();
        }
        connectedUsers.clear();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Stop heartbeat thread
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            try {
                heartbeatThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);
        statusLabel.setText("Server stopped");
        statusLabel.setForeground(Color.RED);

        appendMessage("=== Server stopped ===\n\n");
    }

    private void broadcastToAllUsers(String message) {
        broadcastToAllUsers(message, null);
    }

    private void broadcastToAllUsers(String message, String excludeUser) {
        for (ClientHandler handler : connectedUsers.values()) {
            // Skip sending to the excluded user (sender)
            if (excludeUser != null && handler.getUsername().equals(excludeUser)) {
                continue;
            }

            try {
                handler.sendMessage(message);
            } catch (IOException e) {
                appendMessage("Error sending to " + handler.getUsername() + ": " + e.getMessage() + "\n");
            }
        }
    }

    private void broadcastUserList() {
        StringBuilder userListBuilder = new StringBuilder("USERLIST:");

        for (String username : connectedUsers.keySet()) {
            if (userListBuilder.length() > 9) {
                userListBuilder.append(",");
            }
            userListBuilder.append(username);
        }

        String userListMsg = userListBuilder.toString();

        for (ClientHandler handler : connectedUsers.values()) {
            try {
                handler.sendMessage(userListMsg);
            } catch (IOException e) {
                appendMessage("Error sending user list to " + handler.getUsername() + ": " + e.getMessage() + "\n");
            }
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(message);
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    // Inner class to handle each client connection
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private long lastSeen;

        ClientHandler(Socket socket) {
            this.socket = socket;
            this.lastSeen = System.currentTimeMillis();
        }

        String getUsername() {
            return username;
        }

        void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }

        boolean isTimedOut() {
            return System.currentTimeMillis() - lastSeen > HEARTBEAT_TIMEOUT;
        }

        void sendMessage(String message) throws IOException {
            if (out != null) {
                out.println(message);
            }
        }

        void close() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while ((message = in.readLine()) != null) {
                    // Handle heartbeat message: HEARTBEAT:username
                    if (message.startsWith("HEARTBEAT:")) {
                        String user = message.substring(10);
                        ClientHandler handler = connectedUsers.get(user);
                        if (handler != null) {
                            handler.updateLastSeen();
                        }
                    }
                    // Handle connection message: CONNECT:username
                    else if (message.startsWith("CONNECT:")) {
                        username = message.substring(8);
                        ClientHandler existingHandler = connectedUsers.get(username);
                        if (existingHandler == null) {
                            connectedUsers.put(username, this);
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] User '" + username + "' connected from " +
                                    socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "\n");

                            // Notify all users about the new connection
                            String joinMsg = "*** " + username + " joined the chat ***";
                            broadcastToAllUsers(joinMsg);

                            // Send updated user list to all clients
                            broadcastUserList();
                        } else {
                            // Update existing user's connection info (reconnection)
                            existingHandler.close();
                            connectedUsers.put(username, this);
                            updateLastSeen();

                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] User '" + username + "' reconnected\n");

                            // Notify about reconnection
                            String reconnectMsg = "*** " + username + " reconnected ***";
                            broadcastToAllUsers(reconnectMsg);

                            // Send updated user list
                            broadcastUserList();
                        }
                    }
                    // Handle disconnection message: DISCONNECT:username
                    else if (message.startsWith("DISCONNECT:")) {
                        String user = message.substring(11);
                        if (connectedUsers.containsKey(user)) {
                            connectedUsers.remove(user);
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] User '" + user + "' disconnected\n");

                            // Notify all users about the disconnection
                            String leaveMsg = "*** " + user + " left the chat ***";
                            broadcastToAllUsers(leaveMsg);

                            // Send updated user list to all remaining clients
                            broadcastUserList();
                        }
                        break; // Exit the handler loop
                    }
                    // Handle voice chunks
                    else if (message.startsWith("VOICECHUNK|")) {
                        handleVoiceChunk(message);
                    }
                    // Handle file chunks
                    else if (message.startsWith("FILECHUNK|")) {
                        handleFileChunk(message);
                    }
                    // Handle image chunks
                    else if (message.startsWith("IMGCHUNK|")) {
                        handleImageChunk(message);
                    }
                    // Parse private message format: TO:recipient|FROM:sender|MSG:message
                    else if (message.startsWith("TO:")) {
                        String[] parts = message.split("\\|");
                        if (parts.length >= 3) {
                            String recipient = parts[0].substring(3);
                            String sender = parts[1].substring(5);
                            String msgContent = parts[2].substring(4);

                            updateLastSeen();

                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] PRIVATE from " + sender + " to " + recipient + ": " + msgContent + "\n");

                            // Send private message only to the recipient
                            ClientHandler recipientHandler = connectedUsers.get(recipient);
                            if (recipientHandler != null) {
                                try {
                                    String privateMsg = "PRIVATE:" + sender + "|MSG:" + msgContent;
                                    recipientHandler.sendMessage(privateMsg);
                                } catch (IOException e) {
                                    appendMessage("Error sending private message to " + recipient + ": " + e.getMessage() + "\n");
                                }
                            } else {
                                appendMessage("[" + timestamp + "] Recipient '" + recipient + "' not found or offline\n");
                            }

                            // Send updated user list to all clients
                            broadcastUserList();
                        }
                    }
                    // Parse message format: FROM:username|MSG:message
                    else if (message.startsWith("FROM:")) {
                        String[] parts = message.split("\\|");
                        if (parts.length >= 2) {
                            String user = parts[0].substring(5);
                            String msgContent = parts[1].substring(4);

                            updateLastSeen();

                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] FROM " + user + ": " + msgContent + "\n");

                            // Broadcast message to all connected users except the sender
                            String broadcastMsg = user + ": " + msgContent;
                            broadcastToAllUsers(broadcastMsg, user);

                            // Send updated user list to all clients
                            broadcastUserList();
                        }
                    }
                }
            } catch (SocketException e) {
                // Connection reset or closed
                if (username != null && connectedUsers.containsKey(username)) {
                    connectedUsers.remove(username);
                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    appendMessage("[" + timestamp + "] User '" + username + "' disconnected (connection lost)\n");

                    String leaveMsg = "*** " + username + " left the chat ***";
                    broadcastToAllUsers(leaveMsg);
                    broadcastUserList();
                }
            } catch (IOException e) {
                if (isRunning && username != null) {
                    appendMessage("Error handling client " + username + ": " + e.getMessage() + "\n");
                }
            } finally {
                close();
            }
        }

        private void handleVoiceChunk(String message) {
            String[] parts = message.split("\\|");

            String sessionId = null;
            String sender = null;
            String recipient = null;
            String chunkData = null;

            for (String part : parts) {
                if (part.startsWith("SESSION:")) {
                    sessionId = part.substring(8);
                } else if (part.startsWith("FROM:")) {
                    sender = part.substring(5);
                } else if (part.startsWith("TO:")) {
                    recipient = part.substring(3);
                } else if (part.startsWith("DATA:")) {
                    chunkData = part.substring(5);
                }
            }

            if (sender != null && chunkData != null) {
                updateLastSeen();

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                // Handle private voice chunk
                if (recipient != null && !recipient.isEmpty()) {
                    appendMessage("[" + timestamp + "] PRIVATE VOICE CHUNK from " + sender + " to " + recipient + "\n");
                    ClientHandler recipientHandler = connectedUsers.get(recipient);
                    if (recipientHandler != null) {
                        try {
                            recipientHandler.sendMessage(message);
                        } catch (IOException e) {
                            appendMessage("Error sending private voice chunk to " + recipient + ": " + e.getMessage() + "\n");
                        }
                    }
                } else {
                    // Broadcast voice chunk to all except sender
                    appendMessage("[" + timestamp + "] BROADCAST VOICE CHUNK from " + sender + "\n");
                    broadcastToAllUsers(message, sender);
                }

                broadcastUserList();
            }
        }

        private void handleFileChunk(String message) {
            String[] parts = message.split("\\|");

            String sessionId = null;
            String sender = null;
            String recipient = null;
            String filename = null;
            String chunkData = null;

            for (String part : parts) {
                if (part.startsWith("SESSION:")) {
                    sessionId = part.substring(8);
                } else if (part.startsWith("FROM:")) {
                    sender = part.substring(5);
                } else if (part.startsWith("TO:")) {
                    recipient = part.substring(3);
                } else if (part.startsWith("FILENAME:")) {
                    filename = part.substring(9);
                } else if (part.startsWith("DATA:")) {
                    chunkData = part.substring(5);
                }
            }

            if (sender != null && filename != null && chunkData != null) {
                updateLastSeen();

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                // Handle private file chunk
                if (recipient != null && !recipient.isEmpty()) {
                    appendMessage("[" + timestamp + "] PRIVATE FILE CHUNK from " + sender + " to " + recipient + " [" + filename + "]\n");
                    ClientHandler recipientHandler = connectedUsers.get(recipient);
                    if (recipientHandler != null) {
                        try {
                            recipientHandler.sendMessage(message);
                        } catch (IOException e) {
                            appendMessage("Error sending private file chunk to " + recipient + ": " + e.getMessage() + "\n");
                        }
                    }
                } else {
                    // Broadcast file chunk to all except sender
                    appendMessage("[" + timestamp + "] BROADCAST FILE CHUNK from " + sender + " [" + filename + "]\n");
                    broadcastToAllUsers(message, sender);
                }

                broadcastUserList();
            }
        }

        private void handleImageChunk(String message) {
            String[] parts = message.split("\\|");

            String sessionId = null;
            String sender = null;
            String recipient = null;
            String chunkData = null;

            for (String part : parts) {
                if (part.startsWith("SESSION:")) {
                    sessionId = part.substring(8);
                } else if (part.startsWith("FROM:")) {
                    sender = part.substring(5);
                } else if (part.startsWith("TO:")) {
                    recipient = part.substring(3);
                } else if (part.startsWith("DATA:")) {
                    chunkData = part.substring(5);
                }
            }

            if (sender != null && chunkData != null) {
                updateLastSeen();

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                // Handle private image chunk
                if (recipient != null && !recipient.isEmpty()) {
                    appendMessage("[" + timestamp + "] PRIVATE IMAGE CHUNK from " + sender + " to " + recipient + "\n");
                    ClientHandler recipientHandler = connectedUsers.get(recipient);
                    if (recipientHandler != null) {
                        try {
                            recipientHandler.sendMessage(message);
                        } catch (IOException e) {
                            appendMessage("Error sending private image chunk to " + recipient + ": " + e.getMessage() + "\n");
                        }
                    }
                } else {
                    // Broadcast image chunk to all except sender
                    appendMessage("[" + timestamp + "] BROADCAST IMAGE CHUNK from " + sender + "\n");
                    broadcastToAllUsers(message, sender);
                }

                broadcastUserList();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TCPServer server = new TCPServer();
            server.setVisible(true);
        });
    }
}
