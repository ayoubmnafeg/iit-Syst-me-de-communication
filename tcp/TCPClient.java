import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;

public class TCPClient extends JFrame {
    private static final String DEFAULT_SERVER = "localhost";
    private static final int DEFAULT_PORT = 9876;

    private JTextPane messageArea;
    private JTextField messageField;
    private JTextField serverIpField;
    private JButton sendButton, clearButton, connectButton, disconnectButton, sendImageButton, sendFileButton, recordMicButton, stopMicButton;
    private JLabel statusLabel;
    private String serverIp = DEFAULT_SERVER;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isConnected = false;
    private Thread receiveThread;
    private Thread heartbeatThread;
    private String username;
    private DefaultListModel<UserStatus> userListModel;
    private JList<UserStatus> userList;
    private String selectedUser = "All";

    // Voice recording
    private VoiceRecorder voiceRecorder;
    private boolean isRecordingVoice = false;

    // Inner class to represent user status
    private static class UserStatus {
        String username;
        boolean isConnected;

        UserStatus(String username, boolean isConnected) {
            this.username = username;
            this.isConnected = isConnected;
        }

        @Override
        public String toString() {
            return username;
        }
    }

    private static final int MAX_IMAGE_WIDTH = 150;
    private static final int MAX_IMAGE_HEIGHT = 150;
    private static final int MAX_IMAGE_SIZE = 50000; // 50KB max

    // Image chunk reassembly
    private Map<String, ImageChunkBuffer> imageChunks = new HashMap<>();

    // File chunk reassembly
    private Map<String, FileChunkBuffer> fileChunks = new HashMap<>();

    // Voice chunk reassembly
    private Map<String, VoiceChunkBuffer> voiceChunks = new HashMap<>();

    // Inner class for buffering voice chunks
    private static class VoiceChunkBuffer {
        String sender;
        String recipient;
        String[] chunks;
        boolean[] received;
        long createdTime;

        VoiceChunkBuffer(int totalChunks, String sender, String recipient) {
            this.sender = sender;
            this.recipient = recipient;
            this.chunks = new String[totalChunks];
            this.received = new boolean[totalChunks];
            this.createdTime = System.currentTimeMillis();
        }

        void setChunk(int index, String data) {
            if (index < chunks.length) {
                chunks[index] = data;
                received[index] = true;
            }
        }

        boolean isComplete() {
            for (boolean r : received) {
                if (!r) return false;
            }
            return true;
        }

        String getCompleteData() {
            StringBuilder sb = new StringBuilder();
            for (String chunk : chunks) {
                sb.append(chunk);
            }
            return sb.toString();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdTime > 120000; // 120 second timeout for voice
        }
    }

    // Inner class for buffering file chunks
    private static class FileChunkBuffer {
        String sender;
        String recipient;
        String filename;
        String[] chunks;
        boolean[] received;
        long createdTime;

        FileChunkBuffer(int totalChunks, String sender, String recipient, String filename) {
            this.sender = sender;
            this.recipient = recipient;
            this.filename = filename;
            this.chunks = new String[totalChunks];
            this.received = new boolean[totalChunks];
            this.createdTime = System.currentTimeMillis();
        }

        void setChunk(int index, String data) {
            if (index < chunks.length) {
                chunks[index] = data;
                received[index] = true;
            }
        }

        boolean isComplete() {
            for (boolean r : received) {
                if (!r) return false;
            }
            return true;
        }

        String getCompleteData() {
            StringBuilder sb = new StringBuilder();
            for (String chunk : chunks) {
                sb.append(chunk);
            }
            return sb.toString();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdTime > 60000; // 60 second timeout
        }
    }

    // Inner class for buffering image chunks
    private static class ImageChunkBuffer {
        String sender;
        String recipient;
        String[] chunks;
        boolean[] received;
        long createdTime;

        ImageChunkBuffer(int totalChunks, String sender, String recipient) {
            this.sender = sender;
            this.recipient = recipient;
            this.chunks = new String[totalChunks];
            this.received = new boolean[totalChunks];
            this.createdTime = System.currentTimeMillis();
        }

        void setChunk(int index, String data) {
            if (index < chunks.length) {
                chunks[index] = data;
                received[index] = true;
            }
        }

        boolean isComplete() {
            for (boolean r : received) {
                if (!r) return false;
            }
            return true;
        }

        String getCompleteData() {
            StringBuilder sb = new StringBuilder();
            for (String chunk : chunks) {
                sb.append(chunk);
            }
            return sb.toString();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdTime > 30000; // 30 second timeout
        }
    }

    public TCPClient() {
        setTitle("TCP Client");
        setSize(800, 550);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Handle window close event
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                // Disconnect before closing
                if (isConnected) {
                    disconnect();
                }
                System.exit(0);
            }
        });

        // Top Panel - Connection Settings
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("Connection"));

        topPanel.add(new JLabel("Server IP:"));
        serverIpField = new JTextField(DEFAULT_SERVER, 15);
        topPanel.add(serverIpField);

        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        topPanel.add(connectButton);
        topPanel.add(disconnectButton);

        add(topPanel, BorderLayout.NORTH);

        // Center Panel - Message Display and User List
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Message display on the left
        JPanel messagePanel = new JPanel(new BorderLayout(5, 5));
        messagePanel.setBorder(BorderFactory.createTitledBorder("Messages"));

        messageArea = new JTextPane();
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
        // Add "All" option as the first item
        userListModel.addElement(new UserStatus("All", true));
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        // Custom renderer to show connection status with colored circles
        userList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UserStatus) {
                    UserStatus status = (UserStatus) value;
                    String displayText = status.username;
                    // Add colored circle indicator
                    if (status.isConnected) {
                        displayText = "● " + displayText;
                    } else {
                        displayText = "● " + displayText;
                    }
                    label.setText(displayText);
                    // Set text color based on connection status
                    if (status.isConnected) {
                        label.setForeground(new Color(0, 150, 0)); // Green for connected
                    } else {
                        label.setForeground(new Color(200, 0, 0)); // Red for disconnected
                    }
                }
                return label;
            }
        });
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                UserStatus selected = userList.getSelectedValue();
                if (selected != null) {
                    selectedUser = selected.username;
                    updateSendButtonLabel();
                }
            }
        });
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
        sendImageButton = new JButton("Send Image");
        sendImageButton.setEnabled(false);
        sendFileButton = new JButton("Send File");
        sendFileButton.setEnabled(false);
        recordMicButton = new JButton("Record Voice");
        recordMicButton.setEnabled(false);
        stopMicButton = new JButton("Stop Recording");
        stopMicButton.setEnabled(false);
        clearButton = new JButton("Clear");

        buttonPanel.add(sendButton);
        buttonPanel.add(sendImageButton);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(recordMicButton);
        buttonPanel.add(stopMicButton);
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
        disconnectButton.addActionListener(e -> disconnect());
        sendButton.addActionListener(e -> sendMessage());
        sendImageButton.addActionListener(e -> sendImage());
        sendFileButton.addActionListener(e -> sendFile());
        recordMicButton.addActionListener(e -> startVoiceRecording());
        stopMicButton.addActionListener(e -> stopVoiceRecording());
        clearButton.addActionListener(e -> {
            try {
                messageArea.getDocument().remove(0, messageArea.getDocument().getLength());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        messageField.addActionListener(e -> sendMessage());

        setLocationRelativeTo(null);
    }

    private void toggleConnection() {
        if (!isConnected) {
            connect();
        }
    }

    private void disconnect() {
        // Send disconnect message to server before closing connection
        if (isConnected && username != null && !username.isEmpty()) {
            try {
                out.println("DISCONNECT:" + username);
            } catch (Exception e) {
                appendMessage("Error sending disconnect message: " + e.getMessage() + "\n");
            }
        }

        isConnected = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (receiveThread != null && receiveThread.isAlive()) {
            try {
                receiveThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
            try {
                heartbeatThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        sendImageButton.setEnabled(false);
        sendFileButton.setEnabled(false);
        recordMicButton.setEnabled(false);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        serverIpField.setEnabled(true);

        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.RED);

        appendMessage("\n=== Disconnected ===\n");
    }

    private void connect() {
        // Get server IP from the text field
        serverIp = serverIpField.getText().trim();
        if (serverIp.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server IP is required!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Prompt for username
        username = JOptionPane.showInputDialog(this, "Enter your username:", "Login", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username is required!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        username = username.trim();

        try {
            socket = new Socket(serverIp, DEFAULT_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            isConnected = true;

            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            sendImageButton.setEnabled(true);
            sendFileButton.setEnabled(true);
            recordMicButton.setEnabled(true);
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            serverIpField.setEnabled(false);

            // Initialize voice recorder
            voiceRecorder = new VoiceRecorder();

            setTitle("TCP Client - " + username);

            statusLabel.setText("Connected as '" + username + "' (" + serverIp + ":" + DEFAULT_PORT + ")");
            statusLabel.setForeground(new Color(0, 150, 0));

            appendMessage("=== Connected as '" + username + "' to " + serverIp + ":" + DEFAULT_PORT + " ===\n\n");

            // Send connection message to server immediately
            out.println("CONNECT:" + username);

            // Start heartbeat thread to keep connection alive
            heartbeatThread = new Thread(() -> {
                while (isConnected) {
                    try {
                        Thread.sleep(30000); // Send heartbeat every 30 seconds

                        if (isConnected) {
                            out.println("HEARTBEAT:" + username);
                        }
                    } catch (InterruptedException e) {
                        if (isConnected) {
                            appendMessage("Heartbeat thread interrupted\n");
                        }
                        break;
                    }
                }
            });
            heartbeatThread.start();

            // Start receive thread
            receiveThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        // Check if this is a voice chunk
                        if (response.startsWith("VOICECHUNK|")) {
                            handleVoiceChunk(response);
                        }
                        // Check if this is a file chunk
                        else if (response.startsWith("FILECHUNK|")) {
                            handleFileChunk(response);
                        }
                        // Check if this is an image chunk
                        else if (response.startsWith("IMGCHUNK|")) {
                            handleImageChunk(response);
                        }
                        // Check if this is a user list update
                        else if (response.startsWith("USERLIST:")) {
                            String userListStr = response.substring(9);
                            updateUserList(userListStr);
                        } else if (response.startsWith("PRIVATE:")) {
                            // Private message: PRIVATE:sender|MSG:message
                            String[] parts = response.substring(8).split("\\|");
                            if (parts.length >= 2) {
                                String sender = parts[0];
                                String msgContent = parts[1].substring(4);
                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + timestamp + "] (private from " + sender + "):");
                                appendMessage(msgContent + "\n\n");
                            }
                        } else {
                            // Regular broadcast message
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            appendMessage("[" + timestamp + "] " + response + "\n\n");
                        }
                    }
                } catch (SocketException e) {
                    // Socket closed, exit gracefully
                    if (isConnected) {
                        appendMessage("Connection lost: Socket closed\n");
                        // Trigger disconnect on UI thread
                        SwingUtilities.invokeLater(() -> {
                            disconnect();
                            JOptionPane.showMessageDialog(TCPClient.this,
                                    "Connection lost. Please reconnect.",
                                    "Connection Error",
                                    JOptionPane.WARNING_MESSAGE);
                        });
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        appendMessage("Error receiving: " + e.getMessage() + "\n");
                    }
                }
            });
            receiveThread.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not connect: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
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

        String formattedMessage;
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

        // Check if a user is selected for private messaging
        if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
            // Format for private message: TO:recipient|FROM:sender|MSG:message
            formattedMessage = "TO:" + selectedUser + "|FROM:" + username + "|MSG:" + message;
            appendMessage("[" + timestamp + "] You (private to " + selectedUser + "):");
        } else {
            // Format for broadcast: FROM:sender|MSG:message
            formattedMessage = "FROM:" + username + "|MSG:" + message;
            appendMessage("[" + timestamp + "] You (broadcast to all):");
        }

        out.println(formattedMessage);
        appendMessage(message + "\n\n");
        messageField.setText("");
    }

    private void sendImage() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Not connected to server!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Open file chooser for image selection
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Image Files", "jpg", "jpeg", "png", "gif", "bmp");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return; // User cancelled
        }

        File selectedFile = fileChooser.getSelectedFile();

        // Load and compress image
        try {
            BufferedImage originalImage = ImageIO.read(selectedFile);
            if (originalImage == null) {
                JOptionPane.showMessageDialog(this, "Could not read image file!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Resize image to fit within max dimensions
            BufferedImage resizedImage = resizeImage(originalImage, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);

            // Compress and convert to bytes
            byte[] compressedImageData = compressImage(resizedImage);

            if (compressedImageData.length > MAX_IMAGE_SIZE) {
                JOptionPane.showMessageDialog(this,
                        "Image too large! Max size is " + MAX_IMAGE_SIZE + " bytes, got " + compressedImageData.length + " bytes",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Send image
            sendImageData(compressedImageData);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error reading image: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int maxWidth, int maxHeight) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Calculate scaling factor
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);

        if (scale >= 1.0) {
            return originalImage; // Image is already small enough
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    private byte[] compressImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        baos.flush();
        byte[] imageData = baos.toByteArray();
        baos.close();
        return imageData;
    }

    private void sendImageData(byte[] imageData) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Encode image to base64
            String imageBase64 = Base64.getEncoder().encodeToString(imageData);

            // Split into chunks (500 bytes per chunk)
            int chunkSize = 500;
            int totalChunks = (imageBase64.length() + chunkSize - 1) / chunkSize;
            String sessionId = Long.toHexString(System.currentTimeMillis()); // Unique ID for this transfer

            if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                appendMessage("[" + timestamp + "] You (private image to " + selectedUser + ") [" + imageData.length + " bytes, " + totalChunks + " chunks]:\n");
            } else {
                appendMessage("[" + timestamp + "] You (broadcast image to all) [" + imageData.length + " bytes, " + totalChunks + " chunks]:\n");
            }

            // Display the sent image in the sender's chat window
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
                BufferedImage sentImage = ImageIO.read(bais);
                bais.close();

                if (sentImage != null) {
                    // Create clickable image label with Save/Open options
                    ImageIcon imageIcon = new ImageIcon(sentImage);

                    // Scale image for display if too large
                    Image scaledImage = imageIcon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                    ImageIcon scaledIcon = new ImageIcon(scaledImage);

                    JLabel imageLabel = new JLabel(scaledIcon);
                    imageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    // Add click listener to show Save/Open dialog for sent image
                    byte[] finalImageData = imageData; // Final reference for lambda
                    imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent e) {
                            String[] options = {"Save", "Open", "Cancel"};
                            int choice = JOptionPane.showOptionDialog(
                                    TCPClient.this,
                                    "What would you like to do with this image?",
                                    "Image Options",
                                    JOptionPane.YES_NO_CANCEL_OPTION,
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,
                                    options,
                                    options[0]
                            );

                            if (choice == 0) {
                                // Save image
                                JFileChooser fileChooser = new JFileChooser();
                                fileChooser.setSelectedFile(new File("sent_image_" + System.currentTimeMillis() + ".jpg"));
                                int result = fileChooser.showSaveDialog(TCPClient.this);
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    try {
                                        File saveFile = fileChooser.getSelectedFile();
                                        FileTransfer.writeFile(saveFile, finalImageData);
                                        String saveTimestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        appendMessage("[" + saveTimestamp + "] Image saved to: " + saveFile.getAbsolutePath() + "\n\n");
                                    } catch (IOException ex) {
                                        String saveTimestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        appendMessage("[" + saveTimestamp + "] Error saving image: " + ex.getMessage() + "\n\n");
                                    }
                                }
                            } else if (choice == 1) {
                                // Open image
                                try {
                                    File tempImageFile = Files.createTempFile("temp_sent_image_", ".jpg").toFile();
                                    FileTransfer.writeFile(tempImageFile, finalImageData);
                                    if (java.awt.Desktop.isDesktopSupported()) {
                                        java.awt.Desktop.getDesktop().open(tempImageFile);
                                        tempImageFile.deleteOnExit();
                                    } else {
                                        JOptionPane.showMessageDialog(TCPClient.this,
                                                "Desktop operations not supported",
                                                "Open Error", JOptionPane.ERROR_MESSAGE);
                                    }
                                } catch (IOException ex) {
                                    JOptionPane.showMessageDialog(TCPClient.this,
                                            "Error opening image: " + ex.getMessage(),
                                            "Open Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    });

                    // Insert image into text pane
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int pos = messageArea.getDocument().getLength();
                            messageArea.setCaretPosition(pos);
                            messageArea.insertComponent(imageLabel);
                            messageArea.getDocument().insertString(messageArea.getDocument().getLength(), "\n\n", null);
                            messageArea.setCaretPosition(messageArea.getDocument().getLength());
                        } catch (Exception e) {
                            appendMessage("Error displaying sent image: " + e.getMessage() + "\n\n");
                        }
                    });
                }
            } catch (IOException e) {
                appendMessage("Error displaying sent image: " + e.getMessage() + "\n");
            }

            // Send each chunk
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, imageBase64.length());
                String chunk = imageBase64.substring(start, end);

                String formattedMessage;
                if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                    formattedMessage = "IMGCHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|TO:" + selectedUser + "|FROM:" + username + "|DATA:" + chunk;
                } else {
                    formattedMessage = "IMGCHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FROM:" + username + "|DATA:" + chunk;
                }

                out.println(formattedMessage);

                // Small delay between chunks to avoid network flooding
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(this, "Image transfer interrupted: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendFile() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Not connected to server!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Open file chooser for file selection
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return; // User cancelled
        }

        File selectedFile = fileChooser.getSelectedFile();

        // Read and send file
        try {
            byte[] fileData = FileTransfer.readFile(selectedFile);
            sendFileData(selectedFile.getName(), fileData);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error reading file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendFileData(String filename, byte[] fileData) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Encode file to base64
            String fileBase64 = FileTransfer.encodeToBase64(fileData);

            // Split into chunks (400 bytes per chunk)
            int chunkSize = FileTransfer.CHUNK_SIZE;
            int totalChunks = (fileBase64.length() + chunkSize - 1) / chunkSize;
            String sessionId = Long.toHexString(System.currentTimeMillis()); // Unique ID for this transfer

            if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                appendMessage("[" + timestamp + "] You (private file to " + selectedUser + "): \n");
            } else {
                appendMessage("[" + timestamp + "] You (broadcast file to all): \n");
            }

            // Create and insert clickable file link for sent file
            FileLink sentFileLink = new FileLink(filename, fileData);

            SwingUtilities.invokeLater(() -> {
                try {
                    int pos = messageArea.getDocument().getLength();
                    messageArea.setCaretPosition(pos);
                    messageArea.insertComponent(sentFileLink);
                    messageArea.getDocument().insertString(messageArea.getDocument().getLength(), "\n\n", null);
                    messageArea.setCaretPosition(messageArea.getDocument().getLength());
                } catch (Exception e) {
                    appendMessage("Error displaying file link: " + e.getMessage() + "\n\n");
                }
            });

            // Send each chunk
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileBase64.length());
                String chunk = fileBase64.substring(start, end);

                String formattedMessage;
                if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                    formattedMessage = "FILECHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FILENAME:" + filename + "|TO:" + selectedUser + "|FROM:" + username + "|DATA:" + chunk;
                } else {
                    formattedMessage = "FILECHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FILENAME:" + filename + "|FROM:" + username + "|DATA:" + chunk;
                }

                out.println(formattedMessage);

                // Small delay between chunks to avoid network flooding
                Thread.sleep(10);
            }

            appendMessage("[File sent successfully]\n\n");

        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(this, "File transfer interrupted: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startVoiceRecording() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Not connected to server!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            voiceRecorder.startRecording();
            isRecordingVoice = true;
            recordMicButton.setEnabled(false);
            stopMicButton.setEnabled(true);
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            sendImageButton.setEnabled(false);
            sendFileButton.setEnabled(false);
            appendMessage("[Recording voice message...]\n");
        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(this, "Microphone not available: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopVoiceRecording() {
        if (!isRecordingVoice) {
            return;
        }

        try {
            byte[] voiceData = voiceRecorder.stopRecording();
            isRecordingVoice = false;
            recordMicButton.setEnabled(true);
            stopMicButton.setEnabled(false);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            sendImageButton.setEnabled(true);
            sendFileButton.setEnabled(true);

            if (voiceData.length > 0) {
                appendMessage("[Voice recording complete, sending...]\n");
                sendVoiceData(voiceData);
            } else {
                appendMessage("[No audio recorded]\n");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error stopping recording: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendVoiceData(byte[] voiceData) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Encode voice to base64
            String voiceBase64 = Base64.getEncoder().encodeToString(voiceData);

            // Split into chunks (400 bytes per chunk)
            int chunkSize = 400;
            int totalChunks = (voiceBase64.length() + chunkSize - 1) / chunkSize;
            String sessionId = Long.toHexString(System.currentTimeMillis()); // Unique ID for this transfer

            if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                appendMessage("[" + timestamp + "] You (private voice to " + selectedUser + ") [" + voiceData.length + " bytes, " + totalChunks + " chunks]\n");
            } else {
                appendMessage("[" + timestamp + "] You (broadcast voice to all) [" + voiceData.length + " bytes, " + totalChunks + " chunks]\n");
            }

            // Create and insert clickable voice link for sent message
            VoiceLink sentVoiceLink = new VoiceLink(username, voiceData, timestamp);

            SwingUtilities.invokeLater(() -> {
                try {
                    int pos = messageArea.getDocument().getLength();
                    messageArea.setCaretPosition(pos);
                    messageArea.insertComponent(sentVoiceLink);
                    messageArea.getDocument().insertString(messageArea.getDocument().getLength(), "\n\n", null);
                    messageArea.setCaretPosition(messageArea.getDocument().getLength());
                } catch (Exception e) {
                    appendMessage("Error displaying voice link: " + e.getMessage() + "\n\n");
                }
            });

            // Send each chunk
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, voiceBase64.length());
                String chunk = voiceBase64.substring(start, end);

                String formattedMessage;
                if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                    formattedMessage = "VOICECHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|TO:" + selectedUser + "|FROM:" + username + "|DATA:" + chunk;
                } else {
                    formattedMessage = "VOICECHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FROM:" + username + "|DATA:" + chunk;
                }

                out.println(formattedMessage);

                // Small delay between chunks to avoid network flooding
                Thread.sleep(10);
            }

            appendMessage("[Voice sent successfully]\n\n");

        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(this, "Voice transfer interrupted: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSendButtonLabel() {
        if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
            sendButton.setText("Send to " + selectedUser);
        } else {
            sendButton.setText("Send to All");
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                messageArea.getDocument().insertString(messageArea.getDocument().getLength(), message, null);
                messageArea.setCaretPosition(messageArea.getDocument().getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateUserList(String userListStr) {
        SwingUtilities.invokeLater(() -> {
            // Get list of currently connected users from server
            Set<String> connectedUserNames = new HashSet<>();
            if (!userListStr.isEmpty()) {
                String[] users = userListStr.split(",");
                for (String user : users) {
                    String trimmedUser = user.trim();
                    // Don't add the current user to the list
                    if (!trimmedUser.equals(username)) {
                        connectedUserNames.add(trimmedUser);
                    }
                }
            }

            // Update existing users and mark disconnected ones
            for (int i = 0; i < userListModel.getSize(); i++) {
                UserStatus status = userListModel.getElementAt(i);
                if (!status.username.equals("All")) {
                    if (!connectedUserNames.contains(status.username)) {
                        // User is no longer in the connected list, mark as disconnected
                        status.isConnected = false;
                    } else {
                        // User is still connected
                        status.isConnected = true;
                    }
                }
            }

            // Add any new users not already in the list (excluding current user)
            for (String user : connectedUserNames) {
                boolean found = false;
                for (int i = 0; i < userListModel.getSize(); i++) {
                    if (userListModel.getElementAt(i).username.equals(user)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    userListModel.addElement(new UserStatus(user, true));
                }
            }

            // Refresh the list display
            userList.repaint();

            // Select "All" by default
            userList.setSelectedIndex(0);
            selectedUser = "All";
            updateSendButtonLabel();
        });
    }

    private void handleImageChunk(String response) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Parse: IMGCHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|[TO:recipient|]FROM:sender|DATA:chunkData
            String[] parts = response.substring(9).split("\\|");

            String sessionId = null;
            int chunkNum = -1;
            int totalChunks = -1;
            String sender = null;
            String recipient = null;
            String chunkData = null;

            for (String part : parts) {
                if (part.startsWith("SESSION:")) {
                    sessionId = part.substring(8);
                } else if (part.startsWith("CHUNK:")) {
                    chunkNum = Integer.parseInt(part.substring(6));
                } else if (part.startsWith("TOTAL:")) {
                    totalChunks = Integer.parseInt(part.substring(6));
                } else if (part.startsWith("FROM:")) {
                    sender = part.substring(5);
                } else if (part.startsWith("TO:")) {
                    recipient = part.substring(3);
                } else if (part.startsWith("DATA:")) {
                    chunkData = part.substring(5);
                }
            }

            if (sessionId == null || chunkNum < 0 || totalChunks < 0 || sender == null || chunkData == null) {
                appendMessage("[" + timestamp + "] Error: Invalid image chunk\n\n");
                return;
            }

            // Create buffer key (sender + sessionId)
            String bufferKey = sender + "_" + sessionId;

            // Get or create chunk buffer
            ImageChunkBuffer buffer = imageChunks.get(bufferKey);
            if (buffer == null) {
                buffer = new ImageChunkBuffer(totalChunks, sender, recipient);
                imageChunks.put(bufferKey, buffer);
            }

            // Add chunk to buffer
            buffer.setChunk(chunkNum, chunkData);

            // Check if all chunks received
            if (buffer.isComplete()) {
                imageChunks.remove(bufferKey);

                try {
                    // Reassemble complete base64 data
                    String completeBase64 = buffer.getCompleteData();

                    // Decode base64 to get image bytes
                    byte[] imageData = Base64.getDecoder().decode(completeBase64);

                    // Convert bytes to BufferedImage
                    ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
                    BufferedImage receivedImage = ImageIO.read(bais);
                    bais.close();

                    if (receivedImage == null) {
                        appendMessage("[" + timestamp + "] Error: Could not decode image\n\n");
                        return;
                    }

                    // Create clickable image label with Save/Open options
                    ImageIcon imageIcon = new ImageIcon(receivedImage);

                    // Scale image for display if too large
                    Image scaledImage = imageIcon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                    ImageIcon scaledIcon = new ImageIcon(scaledImage);

                    JLabel imageLabel = new JLabel(scaledIcon);
                    imageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    if (recipient != null && !recipient.isEmpty()) {
                        appendMessage("[" + timestamp + "] (private image from " + sender + ") [" + imageData.length + " bytes]:\n");
                    } else {
                        appendMessage("[" + timestamp + "] (image from " + sender + ") [" + imageData.length + " bytes]:\n");
                    }

                    // Add click listener to show Save/Open dialog for image
                    imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent e) {
                            String[] options = {"Save", "Open", "Cancel"};
                            int choice = JOptionPane.showOptionDialog(
                                    TCPClient.this,
                                    "What would you like to do with this image?",
                                    "Image Options",
                                    JOptionPane.YES_NO_CANCEL_OPTION,
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,
                                    options,
                                    options[0]
                            );

                            if (choice == 0) {
                                // Save image
                                JFileChooser fileChooser = new JFileChooser();
                                fileChooser.setSelectedFile(new File("image_" + System.currentTimeMillis() + ".jpg"));
                                int result = fileChooser.showSaveDialog(TCPClient.this);
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    try {
                                        File saveFile = fileChooser.getSelectedFile();
                                        FileTransfer.writeFile(saveFile, imageData);
                                        String saveTimestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        appendMessage("[" + saveTimestamp + "] Image saved to: " + saveFile.getAbsolutePath() + "\n\n");
                                    } catch (IOException ex) {
                                        String saveTimestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        appendMessage("[" + saveTimestamp + "] Error saving image: " + ex.getMessage() + "\n\n");
                                    }
                                }
                            } else if (choice == 1) {
                                // Open image
                                try {
                                    File tempImageFile = Files.createTempFile("temp_image_", ".jpg").toFile();
                                    FileTransfer.writeFile(tempImageFile, imageData);
                                    if (java.awt.Desktop.isDesktopSupported()) {
                                        java.awt.Desktop.getDesktop().open(tempImageFile);
                                        tempImageFile.deleteOnExit();
                                    } else {
                                        JOptionPane.showMessageDialog(TCPClient.this,
                                                "Desktop operations not supported",
                                                "Open Error", JOptionPane.ERROR_MESSAGE);
                                    }
                                } catch (IOException ex) {
                                    JOptionPane.showMessageDialog(TCPClient.this,
                                            "Error opening image: " + ex.getMessage(),
                                            "Open Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    });

                    // Insert image into text pane
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int pos = messageArea.getDocument().getLength();
                            messageArea.setCaretPosition(pos);
                            messageArea.insertComponent(imageLabel);
                            messageArea.getDocument().insertString(messageArea.getDocument().getLength(), "\n\n", null);
                            messageArea.setCaretPosition(messageArea.getDocument().getLength());
                        } catch (Exception e) {
                            appendMessage("Error displaying image: " + e.getMessage() + "\n\n");
                        }
                    });

                } catch (IllegalArgumentException e) {
                    appendMessage("[" + timestamp + "] Error: Could not decode image data - " + e.getMessage() + "\n\n");
                }
            }

        } catch (Exception e) {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            appendMessage("[" + timestamp + "] Error processing image chunk: " + e.getMessage() + "\n\n");
        }
    }

    private void handleFileChunk(String response) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Parse: FILECHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|FILENAME:name|[TO:recipient|]FROM:sender|DATA:chunkData
            String[] parts = response.substring(10).split("\\|");

            String sessionId = null;
            int chunkNum = -1;
            int totalChunks = -1;
            String filename = null;
            String sender = null;
            String recipient = null;
            String chunkData = null;

            for (String part : parts) {
                if (part.startsWith("SESSION:")) {
                    sessionId = part.substring(8);
                } else if (part.startsWith("CHUNK:")) {
                    chunkNum = Integer.parseInt(part.substring(6));
                } else if (part.startsWith("TOTAL:")) {
                    totalChunks = Integer.parseInt(part.substring(6));
                } else if (part.startsWith("FILENAME:")) {
                    filename = part.substring(9);
                } else if (part.startsWith("FROM:")) {
                    sender = part.substring(5);
                } else if (part.startsWith("TO:")) {
                    recipient = part.substring(3);
                } else if (part.startsWith("DATA:")) {
                    chunkData = part.substring(5);
                }
            }

            if (sessionId == null || chunkNum < 0 || totalChunks < 0 || sender == null || filename == null || chunkData == null) {
                appendMessage("[" + timestamp + "] Error: Invalid file chunk\n\n");
                return;
            }

            // Create buffer key (sender + sessionId)
            String bufferKey = sender + "_" + sessionId;

            // Get or create chunk buffer
            FileChunkBuffer buffer = fileChunks.get(bufferKey);
            if (buffer == null) {
                buffer = new FileChunkBuffer(totalChunks, sender, recipient, filename);
                fileChunks.put(bufferKey, buffer);
            }

            // Add chunk to buffer
            buffer.setChunk(chunkNum, chunkData);

            // Check if all chunks received
            if (buffer.isComplete()) {
                fileChunks.remove(bufferKey);

                try {
                    // Reassemble complete base64 data
                    String completeBase64 = buffer.getCompleteData();

                    // Decode base64 to get file bytes
                    byte[] fileData = FileTransfer.decodeFromBase64(completeBase64);

                    if (recipient != null && !recipient.isEmpty()) {
                        appendMessage("[" + timestamp + "] (private file from " + sender + "): \n");
                    } else {
                        appendMessage("[" + timestamp + "] (file from " + sender + "): \n");
                    }

                    // Create and insert clickable file link
                    FileLink fileLink = new FileLink(filename, fileData);

                    // Insert file link into message area
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int pos = messageArea.getDocument().getLength();
                            messageArea.setCaretPosition(pos);
                            messageArea.insertComponent(fileLink);
                            messageArea.getDocument().insertString(messageArea.getDocument().getLength(), "\n\n", null);
                            messageArea.setCaretPosition(messageArea.getDocument().getLength());
                        } catch (Exception e) {
                            appendMessage("Error displaying file link: " + e.getMessage() + "\n\n");
                        }
                    });

                } catch (IllegalArgumentException e) {
                    appendMessage("[" + timestamp + "] Error: Could not decode file data - " + e.getMessage() + "\n\n");
                }
            }

        } catch (Exception e) {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            appendMessage("[" + timestamp + "] Error processing file chunk: " + e.getMessage() + "\n\n");
        }
    }

    private void handleVoiceChunk(String response) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Parse: VOICECHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|[TO:recipient|]FROM:sender|DATA:chunkData
            String[] parts = response.substring(11).split("\\|");

            String sessionId = null;
            int chunkNum = -1;
            int totalChunks = -1;
            String sender = null;
            String recipient = null;
            String chunkData = null;

            for (String part : parts) {
                if (part.startsWith("SESSION:")) {
                    sessionId = part.substring(8);
                } else if (part.startsWith("CHUNK:")) {
                    chunkNum = Integer.parseInt(part.substring(6));
                } else if (part.startsWith("TOTAL:")) {
                    totalChunks = Integer.parseInt(part.substring(6));
                } else if (part.startsWith("FROM:")) {
                    sender = part.substring(5);
                } else if (part.startsWith("TO:")) {
                    recipient = part.substring(3);
                } else if (part.startsWith("DATA:")) {
                    chunkData = part.substring(5);
                }
            }

            if (sessionId == null || chunkNum < 0 || totalChunks < 0 || sender == null || chunkData == null) {
                appendMessage("[" + timestamp + "] Error: Invalid voice chunk\n\n");
                return;
            }

            // Create buffer key (sender + sessionId)
            String bufferKey = sender + "_" + sessionId;

            // Get or create chunk buffer
            VoiceChunkBuffer buffer = voiceChunks.get(bufferKey);
            if (buffer == null) {
                buffer = new VoiceChunkBuffer(totalChunks, sender, recipient);
                voiceChunks.put(bufferKey, buffer);
            }

            // Add chunk to buffer
            buffer.setChunk(chunkNum, chunkData);

            // Check if all chunks received
            if (buffer.isComplete()) {
                voiceChunks.remove(bufferKey);

                try {
                    // Reassemble complete base64 data
                    String completeBase64 = buffer.getCompleteData();

                    // Decode base64 to get voice bytes
                    byte[] voiceData = Base64.getDecoder().decode(completeBase64);

                    if (recipient != null && !recipient.isEmpty()) {
                        appendMessage("[" + timestamp + "] (private voice from " + sender + ") [" + voiceData.length + " bytes]:\n");
                    } else {
                        appendMessage("[" + timestamp + "] (voice from " + sender + ") [" + voiceData.length + " bytes]:\n");
                    }

                    // Create and insert clickable voice link
                    VoiceLink voiceLink = new VoiceLink(sender, voiceData, timestamp);

                    // Insert voice link into message area
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int pos = messageArea.getDocument().getLength();
                            messageArea.setCaretPosition(pos);
                            messageArea.insertComponent(voiceLink);
                            messageArea.getDocument().insertString(messageArea.getDocument().getLength(), "\n\n", null);
                            messageArea.setCaretPosition(messageArea.getDocument().getLength());
                        } catch (Exception e) {
                            appendMessage("Error displaying voice link: " + e.getMessage() + "\n\n");
                        }
                    });

                } catch (IllegalArgumentException e) {
                    appendMessage("[" + timestamp + "] Error: Could not decode voice data - " + e.getMessage() + "\n\n");
                }
            }

        } catch (Exception e) {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            appendMessage("[" + timestamp + "] Error processing voice chunk: " + e.getMessage() + "\n\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TCPClient client = new TCPClient();
            client.setVisible(true);
        });
    }
}
