import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Recepteur extends JFrame {
    private JTextArea messageArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JLabel receivedCountLabel;

    private MulticastSocket socket;
    private InetAddress group;
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;
    private boolean isRunning = false;
    private Thread receiverThread;
    private String recepteurName;
    private int messageCount = 0;

    public Recepteur() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("Récepteur (Receiver)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top Panel - Status and Control
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        statusLabel = new JLabel("Status: Not Listening");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        statusLabel.setForeground(Color.RED);

        receivedCountLabel = new JLabel("Messages Received: 0");
        receivedCountLabel.setFont(new Font("Arial", Font.PLAIN, 11));

        statusPanel.add(statusLabel);
        statusPanel.add(receivedCountLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startButton = new JButton("Start Listening");
        startButton.addActionListener(e -> startRecepteur());

        stopButton = new JButton("Stop Listening");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopRecepteur());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        topPanel.add(statusPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // Center Panel - Message Display Area
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel messageLabel = new JLabel("Received Messages:");
        messageLabel.setFont(new Font("Arial", Font.BOLD, 12));

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(500, 350));

        centerPanel.add(messageLabel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Panel - Info
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JLabel infoLabel = new JLabel("This récepteur receives all messages from émetteurs");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        infoLabel.setForeground(Color.GRAY);

        bottomPanel.add(infoLabel, BorderLayout.CENTER);

        // Add all panels to frame
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Window closing event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopRecepteur();
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void startRecepteur() {
        if (isRunning) {
            JOptionPane.showMessageDialog(this,
                "Récepteur is already listening!",
                "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Ask for récepteur name
        recepteurName = JOptionPane.showInputDialog(this,
            "Enter your Récepteur name:",
            "Récepteur Setup",
            JOptionPane.QUESTION_MESSAGE);

        if (recepteurName == null || recepteurName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Name cannot be empty!",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        recepteurName = recepteurName.trim();

        try {
            // Create multicast socket
            socket = new MulticastSocket(PORT);
            group = InetAddress.getByName(MULTICAST_ADDRESS);

            // Join the multicast group
            socket.joinGroup(group);

            isRunning = true;
            statusLabel.setText("Status: Listening - " + recepteurName);
            statusLabel.setForeground(new Color(0, 150, 0));
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            appendMessage("System", "Récepteur '" + recepteurName + "' started successfully!");
            appendMessage("System", "Listening on Multicast Address: " + MULTICAST_ADDRESS + ":" + PORT);
            appendMessage("System", "Waiting for messages from Émetteurs...\n");

            // Start receiver thread
            receiverThread = new Thread(this::receiveMessages);
            receiverThread.setDaemon(true);
            receiverThread.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Error starting récepteur: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
            cleanup();
        }
    }

    private void stopRecepteur() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        cleanup();

        statusLabel.setText("Status: Not Listening");
        statusLabel.setForeground(Color.RED);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        appendMessage("System", "Récepteur stopped.");
    }

    private void receiveMessages() {
        byte[] buffer = new byte[1024];

        while (isRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                // Display received message
                displayReceivedMessage(message);

                messageCount++;
                updateMessageCount();

            } catch (IOException e) {
                if (isRunning) {
                    appendMessage("ERROR", "Error receiving message: " + e.getMessage());
                }
            }
        }
    }

    private void displayReceivedMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            messageArea.append("[" + timestamp + "] " + message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    private void appendMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            messageArea.append("[" + timestamp + "] " + sender + ": " + message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    private void updateMessageCount() {
        SwingUtilities.invokeLater(() -> {
            receivedCountLabel.setText("Messages Received: " + messageCount);
        });
    }

    private void cleanup() {
        if (socket != null && !socket.isClosed()) {
            try {
                if (group != null) {
                    socket.leaveGroup(group);
                }
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Recepteur recepteur = new Recepteur();
            recepteur.setVisible(true);
        });
    }
}
