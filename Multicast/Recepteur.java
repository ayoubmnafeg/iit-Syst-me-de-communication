import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;

public class Recepteur extends JFrame {
    private JTextPane messageArea;
    private StyledDocument doc;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JLabel receivedCountLabel;
    private ByteArrayOutputStream imageBuffer;
    private boolean receivingImage = false;
    private String currentImageSender;
    private String currentImageName;

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

        messageArea = new JTextPane();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        doc = messageArea.getStyledDocument();
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(500, 350));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

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
            socket.joinGroup(new InetSocketAddress(group, 0), null);

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
        byte[] buffer = new byte[65535]; // Increased buffer size for image data

        while (isRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = packet.getData();
                int length = packet.getLength();

                if (!receivingImage) {
                    // Try to parse as text message
                    String message = new String(data, 0, length);

                    if (message.startsWith("IMG:")) {
                        // Start of image transfer
                        String[] parts = message.split(":");
                        if (parts.length == 3) {
                            receivingImage = true;
                            currentImageSender = parts[1];
                            currentImageName = parts[2];
                            imageBuffer = new ByteArrayOutputStream();
                            appendMessage("System", "Receiving image from " + currentImageSender + ": " + currentImageName);
                        }
                    } else if (!message.startsWith("END:")) {
                        // Regular text message
                        displayReceivedMessage(message);
                        messageCount++;
                        updateMessageCount();
                    }
                } else {
                    // Check if this is the end marker
                    String possibleEnd = new String(data, 0, Math.min(20, length));
                    if (possibleEnd.startsWith("END:")) {
                        // Image transfer complete
                        try {
                            byte[] imageData = imageBuffer.toByteArray();
                            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                            BufferedImage image = ImageIO.read(bis);

                            if (image != null) {
                                displayReceivedImage(image);
                                appendMessage("System", "Image received successfully from " + currentImageSender);
                            } else {
                                appendMessage("ERROR", "Failed to decode received image");
                            }
                        } catch (Exception e) {
                            appendMessage("ERROR", "Error processing received image: " + e.getMessage());
                        }

                        // Reset image receiving state
                        receivingImage = false;
                        imageBuffer = null;
                        currentImageSender = null;
                        currentImageName = null;
                        messageCount++;
                        updateMessageCount();
                    } else {
                        // Append image data
                        imageBuffer.write(data, 0, length);
                    }
                }

            } catch (IOException e) {
                if (isRunning) {
                    appendMessage("ERROR", "Error receiving message: " + e.getMessage());
                }
            }
        }
    }

    private void displayReceivedImage(BufferedImage image) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Add timestamp and sender info
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                String header = "[" + timestamp + "] Image from " + currentImageSender + " (" + currentImageName + "):\n";
                doc.insertString(doc.getLength(), header, null);

                // Scale the image if it's too large for inline display
                int maxWidth = 400;
                int maxHeight = 300;

                int width = image.getWidth();
                int height = image.getHeight();

                if (width > maxWidth || height > maxHeight) {
                    double scale = Math.min((double)maxWidth/width, (double)maxHeight/height);
                    width = (int)(width * scale);
                    height = (int)(height * scale);
                }

                // Create a scaled image icon
                ImageIcon imageIcon = new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));

                // Insert the image into the document
                messageArea.setCaretPosition(doc.getLength());
                messageArea.insertIcon(imageIcon);

                // Add a newline after the image
                doc.insertString(doc.getLength(), "\n", null);

                // Auto-scroll to bottom
                messageArea.setCaretPosition(doc.getLength());

            } catch (BadLocationException e) {
                appendMessage("ERROR", "Failed to display image: " + e.getMessage());
            }
        });
    }

    private void displayReceivedMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                doc.insertString(doc.getLength(), "[" + timestamp + "] " + message + "\n", null);
                messageArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                System.err.println("Error displaying message: " + e.getMessage());
            }
        });
    }

    private void appendMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                doc.insertString(doc.getLength(), "[" + timestamp + "] " + sender + ": " + message + "\n", null);
                messageArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                System.err.println("Error appending message: " + e.getMessage());
            }
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
                    socket.leaveGroup(new InetSocketAddress(group, 0), null);
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
