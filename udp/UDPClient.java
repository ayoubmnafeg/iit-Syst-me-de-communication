import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UDPClient extends JFrame {
    private JTextArea messageArea;
    private JTextField serverAddressField, serverPortField, messageField;
    private JButton sendButton, clearButton, connectButton;
    private JLabel statusLabel;
    private DatagramSocket socket;
    private boolean isConnected = false;
    private Thread receiveThread;
    
    public UDPClient() {
        setTitle("UDP Client");
        setSize(600, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Top Panel - Connection Settings
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Connection Settings"));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        row1.add(new JLabel("Server Address:"));
        serverAddressField = new JTextField("localhost", 15);
        row1.add(serverAddressField);
        
        row1.add(new JLabel("Port:"));
        serverPortField = new JTextField("9876", 8);
        row1.add(serverPortField);
        
        connectButton = new JButton("Connect");
        row1.add(connectButton);
        
        topPanel.add(row1);
        
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
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000); // 5 second timeout for receiving
            isConnected = true;
            
            serverAddressField.setEnabled(false);
            serverPortField.setEnabled(false);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            connectButton.setText("Disconnect");
            
            statusLabel.setText("Connected to " + serverAddressField.getText() + ":" + serverPortField.getText());
            statusLabel.setForeground(new Color(0, 150, 0));
            
            appendMessage("=== Connected to server ===\n\n");
            
            // Start receive thread
            receiveThread = new Thread(() -> {
                byte[] receiveData = new byte[1024];
                
                while (isConnected) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);
                        
                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        
                        appendMessage("[" + timestamp + "] SERVER RESPONSE:\n");
                        appendMessage(response + "\n\n");
                        
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
        
        serverAddressField.setEnabled(true);
        serverPortField.setEnabled(true);
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        connectButton.setText("Connect");
        
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.RED);
        
        appendMessage("=== Disconnected from server ===\n\n");
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
            InetAddress serverAddress = InetAddress.getByName(serverAddressField.getText());
            int serverPort = Integer.parseInt(serverPortField.getText());
            
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                sendData, sendData.length, serverAddress, serverPort);
            
            socket.send(sendPacket);
            
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            appendMessage("[" + timestamp + "] SENT:\n");
            appendMessage(message + "\n\n");
            
            messageField.setText("");
            
        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Unknown host: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number!", "Error", JOptionPane.ERROR_MESSAGE);
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
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UDPClient client = new UDPClient();
            client.setVisible(true);
        });
    }
}
