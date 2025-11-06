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

public class Emetteur extends JFrame {
    private JTextPane messageArea;
    private StyledDocument doc;
    private JTextField inputField;
    private JButton sendButton;
    private JButton sendImageButton;
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

        messageArea = new JTextPane();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        doc = messageArea.getStyledDocument();
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

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

        sendImageButton = new JButton("Send Image");
        sendImageButton.setEnabled(false);
        sendImageButton.addActionListener(e -> sendImage());

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.add(sendButton);
        buttonsPanel.add(sendImageButton);

        bottomPanel.add(new JLabel("Message:"), BorderLayout.WEST);
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(buttonsPanel, BorderLayout.EAST);

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
            socket.joinGroup(new InetSocketAddress(group, 0), null);

            isActive = true;
            statusLabel.setText("Status: Active - " + emetteurName);
            statusLabel.setForeground(new Color(0, 150, 0));
            startButton.setEnabled(false);
            inputField.setEnabled(true);
            sendButton.setEnabled(true);
            sendImageButton.setEnabled(true);
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
            try {
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                doc.insertString(doc.getLength(), "[" + timestamp + "] " + sender + ": " + message + "\n", null);
                messageArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                System.err.println("Error appending message: " + e.getMessage());
            }
        });
    }

    private void displaySentImage(BufferedImage image, String filename) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Add timestamp and info
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                String header = "[" + timestamp + "] Sent image (" + filename + "):\n";
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

    private void sendImage() {
        if (!isActive) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".jpg") || 
                       f.getName().toLowerCase().endsWith(".png") || 
                       f.getName().toLowerCase().endsWith(".gif");
            }
            public String getDescription() {
                return "Image files (*.jpg, *.png, *.gif)";
            }
        });

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Read the image file
                BufferedImage image = ImageIO.read(selectedFile);
                if (image == null) {
                    throw new IOException("Failed to read image file");
                }

                // Convert image to byte array
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ImageIO.write(image, "png", byteArrayOutputStream);
                byte[] imageData = byteArrayOutputStream.toByteArray();

                // Prepare header (format: "IMG:[ÉMETTEUR_NAME]:[FILENAME]")
                String header = "IMG:" + emetteurName + ":" + selectedFile.getName();
                byte[] headerBytes = header.getBytes();

                // Send header first
                DatagramPacket headerPacket = new DatagramPacket(
                    headerBytes,
                    headerBytes.length,
                    group,
                    PORT
                );
                socket.send(headerPacket);

                // Send image data in chunks
                int chunkSize = 60000; // Maximum UDP packet size
                int offset = 0;
                while (offset < imageData.length) {
                    int length = Math.min(chunkSize, imageData.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(imageData, offset, chunk, 0, length);
                    
                    DatagramPacket packet = new DatagramPacket(
                        chunk,
                        chunk.length,
                        group,
                        PORT
                    );
                    socket.send(packet);
                    offset += length;
                    
                    // Small delay to prevent overwhelming the network
                    Thread.sleep(10);
                }

                // Send end marker
                String endMarker = "END:" + emetteurName;
                byte[] endMarkerBytes = endMarker.getBytes();
                DatagramPacket endPacket = new DatagramPacket(
                    endMarkerBytes,
                    endMarkerBytes.length,
                    group,
                    PORT
                );
                socket.send(endPacket);

                appendMessage("System", "Image sent: " + selectedFile.getName());

                // Display the sent image inline
                displaySentImage(image, selectedFile.getName());

            } catch (IOException | InterruptedException e) {
                appendMessage("ERROR", "Failed to send image: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Error sending image: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cleanup() {
        if (socket != null && isActive) {
            try {
                socket.leaveGroup(new InetSocketAddress(InetAddress.getByName(MULTICAST_ADDRESS), 0), null);
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
