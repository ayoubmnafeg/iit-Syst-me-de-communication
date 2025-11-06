import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class UDPServer extends JFrame {
    private JTextArea messageArea;
    private JTextField portField;
    private JButton startButton, stopButton;
    private JLabel statusLabel;
    private DatagramSocket socket;
    private boolean isRunning = false;
    private Thread serverThread;

    // Track connected users: username -> UserInfo
    private Map<String, UserInfo> connectedUsers = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_TIMEOUT = 60000; // 60 seconds timeout
    private Thread heartbeatThread;

    // Inner class to store user information
    private static class UserInfo {
        String username;
        InetAddress address;
        int port;
        long lastSeen;

        UserInfo(String username, InetAddress address, int port) {
            this.username = username;
            this.address = address;
            this.port = port;
            this.lastSeen = System.currentTimeMillis();
        }

        void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }

        boolean isTimedOut() {
            return System.currentTimeMillis() - lastSeen > HEARTBEAT_TIMEOUT;
        }
    }
    
    public UDPServer() {
        setTitle("UDP Server");
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
            socket = new DatagramSocket(port);
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
                        for (UserInfo user : connectedUsers.values()) {
                            if (user.isTimedOut()) {
                                timedOutUsers.add(user.username);
                            }
                        }

                        // Remove timed out users and notify others
                        for (String username : timedOutUsers) {
                            connectedUsers.remove(username);
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

            serverThread = new Thread(() -> {
                byte[] receiveData = new byte[1024 * 100]; // 100KB buffer for large chunks (voice, images, files)

                while (isRunning) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);

                        String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();

                        // Handle heartbeat message: HEARTBEAT:username
                        if (message.startsWith("HEARTBEAT:")) {
                            String username = message.substring(10);
                            UserInfo userInfo = connectedUsers.get(username);
                            if (userInfo != null) {
                                // Update last seen time to keep connection alive
                                userInfo.updateLastSeen();
                            } else {
                                // User not found, might have timed out - treat as reconnection
                                userInfo = new UserInfo(username, clientAddress, clientPort);
                                connectedUsers.put(username, userInfo);
                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + timestamp + "] User '" + username + "' reconnected from " +
                                        clientAddress.getHostAddress() + ":" + clientPort + "\n");

                                // Notify all users about the reconnection
                                String joinMsg = "*** " + username + " reconnected ***";
                                broadcastToAllUsers(joinMsg);

                                // Send updated user list to all clients
                                broadcastUserList();
                            }
                        }
                        // Handle connection message: CONNECT:username
                        else if (message.startsWith("CONNECT:")) {
                            String username = message.substring(8);
                            UserInfo userInfo = connectedUsers.get(username);
                            if (userInfo == null) {
                                userInfo = new UserInfo(username, clientAddress, clientPort);
                                connectedUsers.put(username, userInfo);
                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + timestamp + "] User '" + username + "' connected from " +
                                        clientAddress.getHostAddress() + ":" + clientPort + "\n");

                                // Notify all users about the new connection
                                String joinMsg = "*** " + username + " joined the chat ***";
                                broadcastToAllUsers(joinMsg);

                                // Send updated user list to all clients
                                broadcastUserList();
                            } else {
                                // Update existing user's connection info (reconnection)
                                userInfo.address = clientAddress;
                                userInfo.port = clientPort;
                                userInfo.updateLastSeen();

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
                            String username = message.substring(11);
                            if (connectedUsers.containsKey(username)) {
                                connectedUsers.remove(username);
                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + timestamp + "] User '" + username + "' disconnected\n");

                                // Notify all users about the disconnection
                                String leaveMsg = "*** " + username + " left the chat ***";
                                broadcastToAllUsers(leaveMsg);

                                // Send updated user list to all remaining clients
                                broadcastUserList();
                            }
                        }
                        // Handle voice chunks: VOICECHUNK|SESSION:id|CHUNK:num|TOTAL:total|[TO:recipient|]FROM:sender|DATA:chunkdata
                        if (message.startsWith("VOICECHUNK|")) {
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
                                // Register or update sender
                                UserInfo senderInfo = connectedUsers.get(sender);
                                if (senderInfo == null) {
                                    senderInfo = new UserInfo(sender, clientAddress, clientPort);
                                    connectedUsers.put(sender, senderInfo);
                                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    appendMessage("[" + timestamp + "] User '" + sender + "' connected from " +
                                            clientAddress.getHostAddress() + ":" + clientPort + "\n");
                                } else {
                                    senderInfo.updateLastSeen();
                                }

                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                                // Handle private voice chunk
                                if (recipient != null && !recipient.isEmpty()) {
                                    appendMessage("[" + timestamp + "] PRIVATE VOICE CHUNK from " + sender + " to " + recipient + "\n");
                                    UserInfo recipientInfo = connectedUsers.get(recipient);
                                    if (recipientInfo != null) {
                                        try {
                                            // Forward chunk to recipient
                                            byte[] sendData = message.getBytes();
                                            DatagramPacket sendPacket = new DatagramPacket(
                                                    sendData, sendData.length, recipientInfo.address, recipientInfo.port);
                                            socket.send(sendPacket);
                                        } catch (IOException e) {
                                            appendMessage("Error sending private voice chunk to " + recipient + ": " + e.getMessage() + "\n");
                                        }
                                    }
                                } else {
                                    // Broadcast voice chunk to all except sender
                                    appendMessage("[" + timestamp + "] BROADCAST VOICE CHUNK from " + sender + "\n");
                                    broadcastToAllUsers(message, sender);
                                }

                                // Send updated user list to all clients
                                broadcastUserList();
                            }
                        }
                        // Handle file chunks: FILECHUNK|SESSION:id|CHUNK:num|TOTAL:total|FILENAME:name|[TO:recipient|]FROM:sender|DATA:chunkdata
                        else if (message.startsWith("FILECHUNK|")) {
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
                                // Register or update sender
                                UserInfo senderInfo = connectedUsers.get(sender);
                                if (senderInfo == null) {
                                    senderInfo = new UserInfo(sender, clientAddress, clientPort);
                                    connectedUsers.put(sender, senderInfo);
                                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    appendMessage("[" + timestamp + "] User '" + sender + "' connected from " +
                                            clientAddress.getHostAddress() + ":" + clientPort + "\n");
                                } else {
                                    senderInfo.updateLastSeen();
                                }

                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                                // Handle private file chunk
                                if (recipient != null && !recipient.isEmpty()) {
                                    appendMessage("[" + timestamp + "] PRIVATE FILE CHUNK from " + sender + " to " + recipient + " [" + filename + "]\n");
                                    UserInfo recipientInfo = connectedUsers.get(recipient);
                                    if (recipientInfo != null) {
                                        try {
                                            // Forward chunk to recipient
                                            byte[] sendData = message.getBytes();
                                            DatagramPacket sendPacket = new DatagramPacket(
                                                    sendData, sendData.length, recipientInfo.address, recipientInfo.port);
                                            socket.send(sendPacket);
                                        } catch (IOException e) {
                                            appendMessage("Error sending private file chunk to " + recipient + ": " + e.getMessage() + "\n");
                                        }
                                    }
                                } else {
                                    // Broadcast file chunk to all except sender
                                    appendMessage("[" + timestamp + "] BROADCAST FILE CHUNK from " + sender + " [" + filename + "]\n");
                                    broadcastToAllUsers(message, sender);
                                }

                                // Send updated user list to all clients
                                broadcastUserList();
                            }
                        }
                        // Handle image chunks: IMGCHUNK|SESSION:id|CHUNK:num|TOTAL:total|[TO:recipient|]FROM:sender|DATA:chunkdata
                        else if (message.startsWith("IMGCHUNK|")) {
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
                                // Register or update sender
                                UserInfo senderInfo = connectedUsers.get(sender);
                                if (senderInfo == null) {
                                    senderInfo = new UserInfo(sender, clientAddress, clientPort);
                                    connectedUsers.put(sender, senderInfo);
                                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    appendMessage("[" + timestamp + "] User '" + sender + "' connected from " +
                                            clientAddress.getHostAddress() + ":" + clientPort + "\n");
                                } else {
                                    senderInfo.updateLastSeen();
                                }

                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                                // Handle private image chunk
                                if (recipient != null && !recipient.isEmpty()) {
                                    appendMessage("[" + timestamp + "] PRIVATE IMAGE CHUNK from " + sender + " to " + recipient + "\n");
                                    UserInfo recipientInfo = connectedUsers.get(recipient);
                                    if (recipientInfo != null) {
                                        try {
                                            // Forward chunk to recipient
                                            byte[] sendData = message.getBytes();
                                            DatagramPacket sendPacket = new DatagramPacket(
                                                    sendData, sendData.length, recipientInfo.address, recipientInfo.port);
                                            socket.send(sendPacket);
                                        } catch (IOException e) {
                                            appendMessage("Error sending private image chunk to " + recipient + ": " + e.getMessage() + "\n");
                                        }
                                    }
                                } else {
                                    // Broadcast image chunk to all except sender
                                    appendMessage("[" + timestamp + "] BROADCAST IMAGE CHUNK from " + sender + "\n");
                                    broadcastToAllUsers(message, sender);
                                }

                                // Send updated user list to all clients
                                broadcastUserList();
                            }
                        }
                        // Parse private message format: TO:recipient|FROM:sender|MSG:message
                        else if (message.startsWith("TO:")) {
                            String[] parts = message.split("\\|");
                            if (parts.length >= 3) {
                                String recipient = parts[0].substring(3);
                                String sender = parts[1].substring(5);
                                String msgContent = parts[2].substring(4);

                                // Register or update sender
                                UserInfo senderInfo = connectedUsers.get(sender);
                                if (senderInfo == null) {
                                    senderInfo = new UserInfo(sender, clientAddress, clientPort);
                                    connectedUsers.put(sender, senderInfo);
                                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    appendMessage("[" + timestamp + "] User '" + sender + "' connected from " +
                                            clientAddress.getHostAddress() + ":" + clientPort + "\n");
                                } else {
                                    senderInfo.updateLastSeen();
                                }

                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + timestamp + "] PRIVATE from " + sender + " to " + recipient + ": " + msgContent + "\n");

                                // Send private message only to the recipient
                                UserInfo recipientInfo = connectedUsers.get(recipient);
                                if (recipientInfo != null) {
                                    try {
                                        String privateMsg = "PRIVATE:" + sender + "|MSG:" + msgContent;
                                        byte[] sendData = privateMsg.getBytes();
                                        DatagramPacket sendPacket = new DatagramPacket(
                                            sendData, sendData.length, recipientInfo.address, recipientInfo.port);
                                        socket.send(sendPacket);
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
                                String username = parts[0].substring(5);
                                String msgContent = parts[1].substring(4);

                                // Register or update user
                                UserInfo userInfo = connectedUsers.get(username);
                                if (userInfo == null) {
                                    userInfo = new UserInfo(username, clientAddress, clientPort);
                                    connectedUsers.put(username, userInfo);
                                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    appendMessage("[" + timestamp + "] User '" + username + "' connected from " +
                                            clientAddress.getHostAddress() + ":" + clientPort + "\n");
                                } else {
                                    userInfo.updateLastSeen();
                                }

                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + timestamp + "] FROM " + username + ": " + msgContent + "\n");

                                // Broadcast message to all connected users except the sender
                                String broadcastMsg = username + ": " + msgContent;
                                broadcastToAllUsers(broadcastMsg, username);

                                // Send updated user list to all clients
                                broadcastUserList();
                            }
                        }

                    } catch (SocketException e) {
                        if (!isRunning) break;
                    } catch (IOException e) {
                        if (isRunning) {
                            appendMessage("Error: " + e.getMessage() + "\n");
                        }
                    }
                }
            });
            serverThread.start();
            
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number!", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (SocketException e) {
            JOptionPane.showMessageDialog(this, "Could not start server: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void stopServer() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
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

        // Clear connected users
        connectedUsers.clear();
    }

    private void broadcastToAllUsers(String message) {
        broadcastToAllUsers(message, null);
    }

    private void broadcastToAllUsers(String message, String excludeUser) {
        byte[] sendData = message.getBytes();

        for (UserInfo user : connectedUsers.values()) {
            // Skip sending to the excluded user (sender)
            if (excludeUser != null && user.username.equals(excludeUser)) {
                continue;
            }

            try {
                DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, user.address, user.port);
                socket.send(sendPacket);
            } catch (IOException e) {
                appendMessage("Error sending to " + user.username + ": " + e.getMessage() + "\n");
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
        byte[] sendData = userListMsg.getBytes();

        for (UserInfo user : connectedUsers.values()) {
            try {
                DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, user.address, user.port);
                socket.send(sendPacket);
            } catch (IOException e) {
                appendMessage("Error sending user list to " + user.username + ": " + e.getMessage() + "\n");
            }
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(message);
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UDPServer server = new UDPServer();
            server.setVisible(true);
        });
    }
}
