import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Emetteur extends JFrame {
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton startButton;
    private JLabel statusLabel;

    private MulticastSocket socket;
    private InetAddress group;
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;
    private boolean isActive = false;
    private String emetteurName;

    public Emetteur() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("Émetteur (Sender)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top Panel - Status and Control
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        statusLabel = new JLabel("Status: Not Active");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        statusLabel.setForeground(Color.RED);

        startButton = new JButton("Start Émetteur");
        startButton.addActionListener(e -> startEmetteur());

        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(startButton, BorderLayout.EAST);

        // Center Panel - Message Display Area
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel messageLabel = new JLabel("Messages Sent:");
        messageLabel.setFont(new Font("Arial", Font.BOLD, 12));

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(500, 300));

        centerPanel.add(messageLabel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Panel - Input Area
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 12));
        inputField.setEnabled(false);
        inputField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Send to All Récepteurs");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        bottomPanel.add(new JLabel("Message:"), BorderLayout.WEST);
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        // Add all panels to frame
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Window closing event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void startEmetteur() {
        if (isActive) {
            JOptionPane.showMessageDialog(this,
                "Émetteur is already active!",
                "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Ask for émetteur name
        emetteurName = JOptionPane.showInputDialog(this,
            "Enter your Émetteur name:",
            "Émetteur Setup",
            JOptionPane.QUESTION_MESSAGE);

        if (emetteurName == null || emetteurName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Name cannot be empty!",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        emetteurName = emetteurName.trim();

        try {
            // Create multicast socket
            socket = new MulticastSocket(PORT);
            group = InetAddress.getByName(MULTICAST_ADDRESS);

            // Join the multicast group
            socket.joinGroup(group);

            isActive = true;
            statusLabel.setText("Status: Active - " + emetteurName);
            statusLabel.setForeground(new Color(0, 150, 0));
            startButton.setEnabled(false);
            inputField.setEnabled(true);
            sendButton.setEnabled(true);
            inputField.requestFocus();

            appendMessage("System", "Émetteur '" + emetteurName + "' started successfully!");
            appendMessage("System", "Multicast Address: " + MULTICAST_ADDRESS + ":" + PORT);
            appendMessage("System", "Ready to send messages to all Récepteurs...\n");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Error starting émetteur: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
            cleanup();
        }
    }

    private void sendMessage() {
        if (!isActive) {
            return;
        }

        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        try {
            // Format: [ÉMETTEUR_NAME] message
            String formattedMessage = "[" + emetteurName + "] " + message;
            byte[] buffer = formattedMessage.getBytes();

            // Send the message to the multicast group
            DatagramPacket packet = new DatagramPacket(
                buffer,
                buffer.length,
                group,
                PORT
            );
            socket.send(packet);

            // Display in message area
            appendMessage(emetteurName, message);

            // Clear input field
            inputField.setText("");

        } catch (IOException e) {
            appendMessage("ERROR", "Failed to send message: " + e.getMessage());
        }
    }

    private void appendMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            messageArea.append("[" + timestamp + "] " + sender + ": " + message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    private void cleanup() {
        if (socket != null && isActive) {
            try {
                socket.leaveGroup(group);
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Emetteur emetteur = new Emetteur();
            emetteur.setVisible(true);
        });
    }
}
