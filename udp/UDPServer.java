import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
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
            
            serverThread = new Thread(() -> {
                byte[] receiveData = new byte[1024];

                while (isRunning) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);

                        String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();

                        // Handle connection message: CONNECT:username
                        if (message.startsWith("CONNECT:")) {
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
                                // Update existing user's connection info
                                userInfo.address = clientAddress;
                                userInfo.port = clientPort;
                                userInfo.updateLastSeen();
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

                                // Broadcast message to all connected users
                                String broadcastMsg = username + ": " + msgContent;
                                broadcastToAllUsers(broadcastMsg);

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
        byte[] sendData = message.getBytes();

        for (UserInfo user : connectedUsers.values()) {
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
