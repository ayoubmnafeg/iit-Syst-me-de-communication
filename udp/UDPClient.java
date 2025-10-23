import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class UDPClient extends JFrame {
    private static final String DEFAULT_SERVER = "localhost";
    private static final int DEFAULT_PORT = 9876;

    private JTextArea messageArea;
    private JTextField messageField;
    private JButton sendButton, clearButton, connectButton;
    private JLabel statusLabel;
    private DatagramSocket socket;
    private boolean isConnected = false;
    private Thread receiveThread;
    private String username;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    
    public UDPClient() {
        setTitle("UDP Client");
        setSize(800, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top Panel - Connection Settings
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("Connection"));

        connectButton = new JButton("Connect");
        topPanel.add(connectButton);

        add(topPanel, BorderLayout.NORTH);

        // Center Panel - Message Display and User List
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Message display on the left
        JPanel messagePanel = new JPanel(new BorderLayout(5, 5));
        messagePanel.setBorder(BorderFactory.createTitledBorder("Messages"));

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messagePanel.add(messageScrollPane, BorderLayout.CENTER);

        centerPanel.add(messagePanel, BorderLayout.CENTER);

        // Connected Users list on the right
        JPanel usersPanel = new JPanel(new BorderLayout(5, 5));
        usersPanel.setBorder(BorderFactory.createTitledBorder("Connected Users"));
        usersPanel.setPreferredSize(new Dimension(200, 0));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane userScrollPane = new JScrollPane(userList);
        usersPanel.add(userScrollPane, BorderLayout.CENTER);

        centerPanel.add(usersPanel, BorderLayout.EAST);

        add(centerPanel, BorderLayout.CENTER);
        
        // Bottom Panel - Message Input
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Send Message"));
        
        messageField = new JTextField();
        messageField.setEnabled(false);
        inputPanel.add(messageField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        clearButton = new JButton("Clear");
        
        buttonPanel.add(sendButton);
        buttonPanel.add(clearButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        
        // Status Panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.RED);
        statusPanel.add(statusLabel);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Button Actions
        connectButton.addActionListener(e -> toggleConnection());
        sendButton.addActionListener(e -> sendMessage());
        clearButton.addActionListener(e -> messageArea.setText(""));
        
        messageField.addActionListener(e -> sendMessage());
        
        setLocationRelativeTo(null);
    }
    
    private void toggleConnection() {
        if (!isConnected) {
            connect();
        } else {
            disconnect();
        }
    }
    
    private void connect() {
        // Prompt for username
        username = JOptionPane.showInputDialog(this, "Enter your username:", "Login", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username is required!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        username = username.trim();


        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000); // 5 second timeout for receiving
            isConnected = true;

            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            connectButton.setText("Disconnect");

            statusLabel.setText("Connected as '" + username + "' (" + DEFAULT_SERVER + ":" + DEFAULT_PORT + ")");
            statusLabel.setForeground(new Color(0, 150, 0));

            appendMessage("=== Connected as '" + username + "' ===\n\n");

            // Send connection message to server immediately
            try {
                InetAddress serverAddress = InetAddress.getByName(DEFAULT_SERVER);
                String connectMsg = "CONNECT:" + username;
                byte[] sendData = connectMsg.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, serverAddress, DEFAULT_PORT);
                socket.send(sendPacket);
            } catch (Exception e) {
                appendMessage("Error sending connection message: " + e.getMessage() + "\n");
            }

            // Start receive thread
            receiveThread = new Thread(() -> {
                byte[] receiveData = new byte[1024];

                while (isConnected) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);

                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                        // Check if this is a user list update
                        if (response.startsWith("USERLIST:")) {
                            String userListStr = response.substring(9);
                            updateUserList(userListStr);
                        } else {
                            // Regular message
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] " + response + "\n\n");
                        }

                    } catch (SocketTimeoutException e) {
                        // Normal timeout, continue listening
                    } catch (IOException e) {
                        if (isConnected) {
                            appendMessage("Error receiving: " + e.getMessage() + "\n");
                        }
                        break;
                    }
                }
            });
            receiveThread.start();
            
        } catch (SocketException e) {
            JOptionPane.showMessageDialog(this, "Could not connect: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void disconnect() {
        isConnected = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        connectButton.setText("Connect");

        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.RED);

        appendMessage("=== Disconnected from server ===\n\n");

        username = null;
    }
    
    private void sendMessage() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Not connected to server!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        try {
            InetAddress serverAddress = InetAddress.getByName(DEFAULT_SERVER);

            // Format message with sender (broadcast to all users)
            String formattedMessage = "FROM:" + username + "|MSG:" + message;

            byte[] sendData = formattedMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                sendData, sendData.length, serverAddress, DEFAULT_PORT);

            socket.send(sendPacket);

            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            appendMessage("[" + timestamp + "] You (broadcast):\n");
            appendMessage(message + "\n\n");

            messageField.setText("");

        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Unknown host: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error sending message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(message);
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    private void updateUserList(String userListStr) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            if (!userListStr.isEmpty()) {
                String[] users = userListStr.split(",");
                for (String user : users) {
                    userListModel.addElement(user.trim());
                }
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UDPClient client = new UDPClient();
            client.setVisible(true);
        });
    }
}
