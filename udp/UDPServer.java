import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UDPServer extends JFrame {
    private JTextArea messageArea;
    private JTextField portField;
    private JButton startButton, stopButton;
    private JLabel statusLabel;
    private DatagramSocket socket;
    private boolean isRunning = false;
    private Thread serverThread;
    
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
                        
                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        appendMessage("[" + timestamp + "] FROM " + clientAddress.getHostAddress() + ":" + clientPort + "\n");
                        appendMessage("Message: " + message + "\n\n");
                        
                        // Send acknowledgment back to client
                        String ackMessage = "Server received: " + message;
                        byte[] sendData = ackMessage.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(
                            sendData, sendData.length, clientAddress, clientPort);
                        socket.send(sendPacket);
                        
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
