import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;

public class UDPClient extends JFrame {
    private static final String DEFAULT_SERVER = "localhost";
    private static final int DEFAULT_PORT = 9876;

    private JTextPane messageArea;
    private JTextField messageField;
    private JButton sendButton, clearButton, connectButton, sendImageButton, sendFileButton;
    private JLabel statusLabel;
    private DatagramSocket socket;
    private boolean isConnected = false;
    private Thread receiveThread;
    private String username;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private String selectedUser = "All";

    private static final int MAX_IMAGE_WIDTH = 150;
    private static final int MAX_IMAGE_HEIGHT = 150;
    private static final int MAX_IMAGE_SIZE = 50000; // 50KB max for UDP

    // Image chunk reassembly
    private Map<String, ImageChunkBuffer> imageChunks = new HashMap<>();

    // File chunk reassembly
    private Map<String, FileChunkBuffer> fileChunks = new HashMap<>();

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
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedUser = userList.getSelectedValue();
                updateSendButtonLabel();
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
        clearButton = new JButton("Clear");

        buttonPanel.add(sendButton);
        buttonPanel.add(sendImageButton);
        buttonPanel.add(sendFileButton);
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
        sendImageButton.addActionListener(e -> sendImage());
        sendFileButton.addActionListener(e -> sendFile());
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
            sendImageButton.setEnabled(true);
            sendFileButton.setEnabled(true);
            connectButton.setEnabled(false);

            setTitle("UDP Client - " + username);

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
                byte[] receiveData = new byte[1024 * 100]; // Larger buffer for images (100KB)

                while (isConnected) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);

                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                        // Check if this is a file chunk
                        if (response.startsWith("FILECHUNK|")) {
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
                                appendMessage("[" + timestamp + "] (private from " + sender + "):\n");
                                appendMessage(msgContent + "\n\n");
                            }
                        } else {
                            // Regular broadcast message
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

            String formattedMessage;
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Check if a user is selected for private messaging
            if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                // Format for private message: TO:recipient|FROM:sender|MSG:message
                formattedMessage = "TO:" + selectedUser + "|FROM:" + username + "|MSG:" + message;
                appendMessage("[" + timestamp + "] You (private to " + selectedUser + "):\n");
            } else {
                // Format for broadcast: FROM:sender|MSG:message
                formattedMessage = "FROM:" + username + "|MSG:" + message;
                appendMessage("[" + timestamp + "] You (broadcast to all):\n");
            }

            byte[] sendData = formattedMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                sendData, sendData.length, serverAddress, DEFAULT_PORT);

            socket.send(sendPacket);
            appendMessage(message + "\n\n");
            messageField.setText("");

        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Unknown host: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error sending message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
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
            InetAddress serverAddress = InetAddress.getByName(DEFAULT_SERVER);
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Encode image to base64
            String imageBase64 = Base64.getEncoder().encodeToString(imageData);

            // Split into chunks (500 bytes per chunk to stay well below UDP limit)
            int chunkSize = 500;
            int totalChunks = (imageBase64.length() + chunkSize - 1) / chunkSize;
            String sessionId = Long.toHexString(System.currentTimeMillis()); // Unique ID for this transfer

            if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                appendMessage("[" + timestamp + "] You (private image to " + selectedUser + ") [" + imageData.length + " bytes, " + totalChunks + " chunks]\n");
            } else {
                appendMessage("[" + timestamp + "] You (broadcast image to all) [" + imageData.length + " bytes, " + totalChunks + " chunks]\n");
            }

            // Send each chunk
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, imageBase64.length());
                String chunk = imageBase64.substring(start, end);

                String formattedMessage;
                if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                    // IMGCHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|TO:recipient|FROM:sender|DATA:chunkData
                    formattedMessage = "IMGCHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|TO:" + selectedUser + "|FROM:" + username + "|DATA:" + chunk;
                } else {
                    // IMGCHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|FROM:sender|DATA:chunkData
                    formattedMessage = "IMGCHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FROM:" + username + "|DATA:" + chunk;
                }

                byte[] sendData = formattedMessage.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length, serverAddress, DEFAULT_PORT);
                socket.send(sendPacket);

                // Small delay between chunks to avoid network flooding
                Thread.sleep(10);
            }

            appendMessage("[Image sent successfully]\n\n");

        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Unknown host: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error sending image: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
            InetAddress serverAddress = InetAddress.getByName(DEFAULT_SERVER);
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // Encode file to base64
            String fileBase64 = FileTransfer.encodeToBase64(fileData);

            // Split into chunks (400 bytes per chunk for safer UDP transmission)
            int chunkSize = FileTransfer.CHUNK_SIZE;
            int totalChunks = (fileBase64.length() + chunkSize - 1) / chunkSize;
            String sessionId = Long.toHexString(System.currentTimeMillis()); // Unique ID for this transfer

            if (selectedUser != null && !selectedUser.isEmpty() && !selectedUser.equals("All")) {
                appendMessage("[" + timestamp + "] You (private file to " + selectedUser + "): \n");
            } else {
                appendMessage("[" + timestamp + "] You (broadcast file to all): \n");
            }

            // Create and insert clickable file link for sent file
            FileLink sentFileLink = new FileLink(filename, fileData, (fname, fdata) -> {
                // User clicked their own sent file - show info
                JOptionPane.showMessageDialog(this,
                    "File: " + fname + "\nSize: " + FileTransfer.getFileSizeString(fdata.length) + "\n\nThis is your sent file.",
                    "File Info", JOptionPane.INFORMATION_MESSAGE);
            });

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
                    // FILECHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|FILENAME:name|TO:recipient|FROM:sender|DATA:chunkData
                    formattedMessage = "FILECHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FILENAME:" + filename + "|TO:" + selectedUser + "|FROM:" + username + "|DATA:" + chunk;
                } else {
                    // FILECHUNK|SESSION:id|CHUNK:chunkNum|TOTAL:totalChunks|FILENAME:name|FROM:sender|DATA:chunkData
                    formattedMessage = "FILECHUNK|SESSION:" + sessionId + "|CHUNK:" + i + "|TOTAL:" + totalChunks + "|FILENAME:" + filename + "|FROM:" + username + "|DATA:" + chunk;
                }

                byte[] sendData = formattedMessage.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length, serverAddress, DEFAULT_PORT);
                socket.send(sendPacket);

                // Small delay between chunks to avoid network flooding
                Thread.sleep(10);
            }

            appendMessage("[File sent successfully]\n\n");

        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Unknown host: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error sending file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(this, "File transfer interrupted: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
            userListModel.clear();
            // Add "All" option at the top
            userListModel.addElement("All");
            if (!userListStr.isEmpty()) {
                String[] users = userListStr.split(",");
                for (String user : users) {
                    userListModel.addElement(user.trim());
                }
            }
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

                    // Create clickable image label
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

                    // Add click listener to save image
                    imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent e) {
                            JFileChooser fileChooser = new JFileChooser();
                            fileChooser.setSelectedFile(new File("image_" + System.currentTimeMillis() + ".jpg"));
                            int result = fileChooser.showSaveDialog(UDPClient.this);
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
                    FileLink fileLink = new FileLink(filename, fileData, (fname, fdata) -> {
                        // Called when user clicks the file link
                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setSelectedFile(new File(fname));
                        int result = fileChooser.showSaveDialog(this);

                        if (result == JFileChooser.APPROVE_OPTION) {
                            try {
                                File saveFile = fileChooser.getSelectedFile();
                                FileTransfer.writeFile(saveFile, fdata);
                                String saveTimestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + saveTimestamp + "] File saved to: " + saveFile.getAbsolutePath() + "\n\n");
                            } catch (IOException e) {
                                String saveTimestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                appendMessage("[" + saveTimestamp + "] Error saving file: " + e.getMessage() + "\n\n");
                            }
                        }
                    });

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UDPClient client = new UDPClient();
            client.setVisible(true);
        });
    }
}
