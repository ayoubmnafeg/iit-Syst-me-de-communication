import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TCPServer extends JFrame {
    private ServerSocket serverSocket;
    private JTextArea textArea;
    private JButton startButton, stopButton;
    private JTextField portField;
    private JLabel statusLabel;
    private boolean running = false;
    private int port = 9876;

    // Map of username -> ClientHandler
    private ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public TCPServer() {
        setTitle("TCP Server");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top panel with controls
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("9876", 5);
        topPanel.add(portField);
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        topPanel.add(startButton);
        topPanel.add(stopButton);

        statusLabel = new JLabel("Server stopped");
        statusLabel.setForeground(Color.RED);
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);

        // Text area for messages
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);

        // Button actions
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        setVisible(true);
    }

    private void startServer() {
        try {
            port = Integer.parseInt(portField.getText());
            serverSocket = new ServerSocket(port);
            running = true;

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);
            statusLabel.setText("Server running on port " + port);
            statusLabel.setForeground(Color.GREEN);

            log("Server started on port " + port);

            // Start accepting clients in a new thread
            new Thread(this::acceptClients).start();

        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
        }
    }

    private void stopServer() {
        running = false;

        // Close all client connections
        for (ClientHandler client : clients.values()) {
            client.close();
        }
        clients.clear();

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("Error closing server: " + e.getMessage());
        }

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);
        statusLabel.setText("Server stopped");
        statusLabel.setForeground(Color.RED);

        log("Server stopped");
    }

    private void acceptClients() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                log("New client connected from " + clientSocket.getInetAddress());

                // Create handler for this client (username will be set when CONNECT message is received)
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();

            } catch (IOException e) {
                if (running) {
                    log("Error accepting client: " + e.getMessage());
                }
            }
        }
    }

    private void broadcast(String message, String excludeUser) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (excludeUser == null || !entry.getKey().equals(excludeUser)) {
                entry.getValue().send(message);
            }
        }
    }

    private void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USERLIST:");
        for (String username : clients.keySet()) {
            userList.append(username).append(",");
        }
        if (userList.length() > 9) {
            userList.setLength(userList.length() - 1); // Remove trailing comma
        }
        broadcast(userList.toString(), null);
    }

    private synchronized void log(String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());
        SwingUtilities.invokeLater(() -> {
            textArea.append("[" + timestamp + "] " + message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    // Inner class to handle each client connection
    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                log("Error creating client handler: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                // Client disconnected
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String message) {
            // CONNECT message
            if (message.startsWith("CONNECT:")) {
                username = message.substring(8);
                clients.put(username, this);
                log("User connected: " + username + " (Total users: " + clients.size() + ")");
                broadcast(username + " has joined the chat", username);
                broadcastUserList();
            }
            // DISCONNECT message
            else if (message.startsWith("DISCONNECT:")) {
                String user = message.substring(11);
                log("User disconnected: " + user);
                clients.remove(user);
                broadcast(user + " has left the chat", null);
                broadcastUserList();
                close();
            }
            // HEARTBEAT message
            else if (message.startsWith("HEARTBEAT:")) {
                // TCP doesn't really need heartbeat, but we can acknowledge it
                // The connection itself tells us if the client is alive
            }
            // Private message (TO:recipient|FROM:sender|MSG:message)
            else if (message.startsWith("TO:")) {
                int firstPipe = message.indexOf('|');
                if (firstPipe != -1) {
                    String recipient = message.substring(3, firstPipe);
                    ClientHandler recipientHandler = clients.get(recipient);
                    if (recipientHandler != null) {
                        recipientHandler.send("PRIVATE:" + message.substring(firstPipe + 1));
                    }
                }
            }
            // Broadcast message or chunked data
            else {
                // Check if it's a chunk message (FILECHUNK, IMGCHUNK, VOICECHUNK)
                if (message.startsWith("FILECHUNK|") || message.startsWith("IMGCHUNK|") || message.startsWith("VOICECHUNK|")) {
                    // Parse the message to check if it's private or broadcast
                    if (message.contains("|TO:")) {
                        // Extract recipient
                        int toIndex = message.indexOf("|TO:");
                        int nextPipe = message.indexOf('|', toIndex + 4);
                        if (nextPipe == -1) nextPipe = message.length();
                        String recipient = message.substring(toIndex + 4, nextPipe);

                        ClientHandler recipientHandler = clients.get(recipient);
                        if (recipientHandler != null) {
                            recipientHandler.send(message);
                        }
                    } else {
                        // Broadcast chunk
                        broadcast(message, username);
                    }
                } else {
                    // Regular broadcast message
                    log("Broadcasting from " + (username != null ? username : "unknown") + ": " +
                        (message.length() > 100 ? message.substring(0, 100) + "..." : message));
                    broadcast(message, username);
                }
            }
        }

        public void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        public void close() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                log("Error closing client socket: " + e.getMessage());
            }
        }

        private void cleanup() {
            if (username != null) {
                clients.remove(username);
                log("User disconnected: " + username);
                broadcast(username + " has left the chat", null);
                broadcastUserList();
            }
            close();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TCPServer::new);
    }
}
