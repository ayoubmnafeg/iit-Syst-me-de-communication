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
import java.awt.event.MouseAdapter;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLDocument;

public class TCPClient extends JFrame {
    private static final String DEFAULT_SERVER = "localhost";
    private static final int DEFAULT_PORT = 9876;

    private JTextPane messageArea;
    private JTextField messageField;
    private JButton sendButton, clearButton, connectButton, disconnectButton, sendImageButton, sendFileButton, recordMicButton, stopMicButton;
    private JLabel statusLabel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;
    private Thread receiveThread;
    private Thread heartbeatThread;
    private String username;
    private DefaultListModel<UserStatus> userListModel;
    private JList<UserStatus> userList;
    private String selectedUser = "All";
    private long lastHeartbeat = 0;

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
    }

    // Inner class for buffering file chunks
    private static class FileChunkBuffer {
        String filename;
        String sender;
        String recipient;
        String[] chunks;
        boolean[] received;
        long createdTime;

        FileChunkBuffer(int totalChunks, String filename, String sender, String recipient) {
            this.filename = filename;
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
    }

    public TCPClient(String username) {
        this.username = username;
        setTitle("TCP Client - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                disconnect();
                System.exit(0);
            }
        });
        setLayout(new BorderLayout());

        // Top panel with connection buttons
        JPanel topPanel = new JPanel();
        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        topPanel.add(connectButton);
        topPanel.add(disconnectButton);
        add(topPanel, BorderLayout.NORTH);

        // Center panel with split layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.7);

        // Message area on the left
        messageArea = new JTextPane();
        messageArea.setEditable(false);
        messageArea.setContentType("text/html");
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        splitPane.setLeftComponent(messageScrollPane);

        // User list on the right
        userListModel = new DefaultListModel<>();
        userListModel.addElement(new UserStatus("All", true));
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setSelectedIndex(0);
        userList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UserStatus) {
                    UserStatus status = (UserStatus) value;
                    setText(status.username);
                    if (status.username.equals(username)) {
                        setForeground(Color.GREEN);
                    } else if (!status.isConnected) {
                        setForeground(Color.RED);
                    } else {
                        setForeground(Color.BLACK);
                    }
                }
                return this;
            }
        });
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                UserStatus selected = userList.getSelectedValue();
                if (selected != null) {
                    selectedUser = selected.username;
                }
            }
        });
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setPreferredSize(new Dimension(150, 0));
        splitPane.setRightComponent(userScrollPane);

        add(splitPane, BorderLayout.CENTER);

        // Bottom panel with input and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());
        inputPanel.add(messageField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        sendImageButton = new JButton("Send Image");
        sendImageButton.setEnabled(false);
        sendImageButton.addActionListener(e -> sendImage());
        sendFileButton = new JButton("Send File");
        sendFileButton.setEnabled(false);
        sendFileButton.addActionListener(e -> sendFile());
        recordMicButton = new JButton("Record Voice");
        recordMicButton.setEnabled(false);
        recordMicButton.addActionListener(e -> startVoiceRecording());
        stopMicButton = new JButton("Stop Recording");
        stopMicButton.setEnabled(false);
        stopMicButton.addActionListener(e -> stopVoiceRecording());
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> messageArea.setText(""));

        buttonPanel.add(sendButton);
        buttonPanel.add(sendImageButton);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(recordMicButton);
        buttonPanel.add(stopMicButton);
        buttonPanel.add(clearButton);

        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.RED);
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());

        setVisible(true);
    }

    private void connect() {
        try {
            socket = new Socket(DEFAULT_SERVER, DEFAULT_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            isConnected = true;
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            sendButton.setEnabled(true);
            sendImageButton.setEnabled(true);
            sendFileButton.setEnabled(true);
            recordMicButton.setEnabled(true);
            messageField.setEnabled(true);
            statusLabel.setText("Connected to " + DEFAULT_SERVER + ":" + DEFAULT_PORT);
            statusLabel.setForeground(Color.GREEN);

            // Send connection message
            out.println("CONNECT:" + username);

            // Start receive thread
            receiveThread = new Thread(this::receiveMessages);
            receiveThread.start();

            appendMessage("Connected to server", Color.BLUE);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to connect: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        if (isConnected) {
            try {
                out.println("DISCONNECT:" + username);
                isConnected = false;
                socket.close();
            } catch (IOException e) {
                // Ignore
            }

            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            sendButton.setEnabled(false);
            sendImageButton.setEnabled(false);
            sendFileButton.setEnabled(false);
            recordMicButton.setEnabled(false);
            stopMicButton.setEnabled(false);
            messageField.setEnabled(false);
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.RED);

            appendMessage("Disconnected from server", Color.BLUE);
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("Connection lost", Color.RED);
                    disconnect();
                });
            }
        }
    }

    private void handleMessage(String message) {
        if (message.startsWith("USERLIST:")) {
            updateUserList(message.substring(9));
        } else if (message.startsWith("PRIVATE:")) {
            // Private message: PRIVATE:FROM:sender|MSG:message
            String privateMsg = message.substring(8);
            handlePrivateMessage(privateMsg);
        } else if (message.startsWith("VOICECHUNK|")) {
            handleVoiceChunk(message);
        } else if (message.startsWith("FILECHUNK|")) {
            handleFileChunk(message);
        } else if (message.startsWith("IMGCHUNK|")) {
            handleImageChunk(message);
        } else if (message.startsWith("FROM:")) {
            // Broadcast message
            handleBroadcastMessage(message);
        } else {
            // System message
            appendMessage(message, Color.GRAY);
        }
    }

    private void handlePrivateMessage(String message) {
        // Format: FROM:sender|MSG:message
        try {
            int fromIndex = message.indexOf("FROM:");
            int msgIndex = message.indexOf("|MSG:");

            if (fromIndex != -1 && msgIndex != -1) {
                String sender = message.substring(fromIndex + 5, msgIndex);
                String msg = message.substring(msgIndex + 5);
                appendMessage("[Private] " + sender + ": " + msg, Color.MAGENTA);
            }
        } catch (Exception e) {
            appendMessage("Error parsing private message", Color.RED);
        }
    }

    private void handleBroadcastMessage(String message) {
        // Format: FROM:sender|MSG:message
        try {
            int msgIndex = message.indexOf("|MSG:");
            if (msgIndex != -1) {
                String sender = message.substring(5, msgIndex);
                String msg = message.substring(msgIndex + 5);
                appendMessage(sender + ": " + msg, Color.BLACK);
            }
        } catch (Exception e) {
            appendMessage("Error parsing message", Color.RED);
        }
    }

    private void handleVoiceChunk(String message) {
        try {
            // Format: VOICECHUNK|SESSION:id|CHUNK:num|TOTAL:total|FROM:sender|DATA:data
            // Or: VOICECHUNK|SESSION:id|CHUNK:num|TOTAL:total|TO:recipient|FROM:sender|DATA:data
            String[] parts = message.split("\\|");
            String sessionId = null;
            int chunkNum = -1;
            int totalChunks = -1;
            String sender = null;
            String recipient = null;
            String data = null;

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
                    data = part.substring(5);
                }
            }

            if (sessionId != null && sender != null && data != null) {
                String key = sender + "_" + sessionId;
                VoiceChunkBuffer buffer = voiceChunks.get(key);

                if (buffer == null) {
                    buffer = new VoiceChunkBuffer(totalChunks, sender, recipient);
                    voiceChunks.put(key, buffer);
                }

                buffer.setChunk(chunkNum, data);

                if (buffer.isComplete()) {
                    String completeData = buffer.getCompleteData();
                    byte[] voiceData = Base64.getDecoder().decode(completeData);
                    displayVoiceMessage(sender, voiceData, recipient != null);
                    voiceChunks.remove(key);
                }
            }
        } catch (Exception e) {
            appendMessage("Error processing voice chunk: " + e.getMessage(), Color.RED);
        }
    }

    private void handleFileChunk(String message) {
        try {
            // Format: FILECHUNK|SESSION:id|CHUNK:num|TOTAL:total|FILENAME:name|FROM:sender|DATA:data
            // Or: FILECHUNK|SESSION:id|CHUNK:num|TOTAL:total|FILENAME:name|TO:recipient|FROM:sender|DATA:data
            String[] parts = message.split("\\|");
            String sessionId = null;
            int chunkNum = -1;
            int totalChunks = -1;
            String filename = null;
            String sender = null;
            String recipient = null;
            String data = null;

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
                    data = part.substring(5);
                }
            }

            if (sessionId != null && sender != null && filename != null && data != null) {
                String key = sender + "_" + sessionId;
                FileChunkBuffer buffer = fileChunks.get(key);

                if (buffer == null) {
                    buffer = new FileChunkBuffer(totalChunks, filename, sender, recipient);
                    fileChunks.put(key, buffer);
                }

                buffer.setChunk(chunkNum, data);

                if (buffer.isComplete()) {
                    String completeData = buffer.getCompleteData();
                    byte[] fileData = Base64.getDecoder().decode(completeData);
                    displayFileMessage(sender, filename, fileData, recipient != null);
                    fileChunks.remove(key);
                }
            }
        } catch (Exception e) {
            appendMessage("Error processing file chunk: " + e.getMessage(), Color.RED);
        }
    }

    private void handleImageChunk(String message) {
        try {
            // Format: IMGCHUNK|SESSION:id|CHUNK:num|TOTAL:total|FROM:sender|DATA:data
            // Or: IMGCHUNK|SESSION:id|CHUNK:num|TOTAL:total|TO:recipient|FROM:sender|DATA:data
            String[] parts = message.split("\\|");
            String sessionId = null;
            int chunkNum = -1;
            int totalChunks = -1;
            String sender = null;
            String recipient = null;
            String data = null;

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
                    data = part.substring(5);
                }
            }

            if (sessionId != null && sender != null && data != null) {
                String key = sender + "_" + sessionId;
                ImageChunkBuffer buffer = imageChunks.get(key);

                if (buffer == null) {
                    buffer = new ImageChunkBuffer(totalChunks, sender, recipient);
                    imageChunks.put(key, buffer);
                }

                buffer.setChunk(chunkNum, data);

                if (buffer.isComplete()) {
                    String completeData = buffer.getCompleteData();
                    byte[] imageData = Base64.getDecoder().decode(completeData);
                    displayImageMessage(sender, imageData, recipient != null);
                    imageChunks.remove(key);
                }
            }
        } catch (Exception e) {
            appendMessage("Error processing image chunk: " + e.getMessage(), Color.RED);
        }
    }

    private void updateUserList(String userListStr) {
        SwingUtilities.invokeLater(() -> {
            String[] users = userListStr.split(",");
            Set<String> newUsers = new HashSet<>(Arrays.asList(users));

            // Keep "All" at the top
            UserStatus allStatus = userListModel.get(0);
            userListModel.clear();
            userListModel.addElement(allStatus);

            // Add users
            for (String user : users) {
                if (!user.isEmpty()) {
                    userListModel.addElement(new UserStatus(user, true));
                }
            }
        });
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && isConnected) {
            if (selectedUser.equals("All")) {
                out.println("FROM:" + username + "|MSG:" + message);
                appendMessage("You: " + message, Color.BLUE);
            } else {
                out.println("TO:" + selectedUser + "|FROM:" + username + "|MSG:" + message);
                appendMessage("[Private to " + selectedUser + "] You: " + message, Color.BLUE);
            }
            messageField.setText("");
        }
    }

    private void sendImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "gif", "bmp"));
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            new Thread(() -> {
                try {
                    BufferedImage originalImage = ImageIO.read(file);
                    if (originalImage == null) {
                        SwingUtilities.invokeLater(() -> appendMessage("Failed to read image", Color.RED));
                        return;
                    }

                    // Resize image
                    BufferedImage resizedImage = resizeImage(originalImage);

                    // Convert to byte array
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(resizedImage, "jpg", baos);
                    byte[] imageData = baos.toByteArray();

                    if (imageData.length > MAX_IMAGE_SIZE) {
                        SwingUtilities.invokeLater(() -> appendMessage("Image too large after compression (max 50KB)", Color.RED));
                        return;
                    }

                    // Encode and send
                    String encodedImage = Base64.getEncoder().encodeToString(imageData);
                    sendChunkedData(encodedImage, "IMGCHUNK", 500);

                    SwingUtilities.invokeLater(() -> {
                        if (selectedUser.equals("All")) {
                            appendMessage("You sent an image", Color.BLUE);
                        } else {
                            appendMessage("[Private to " + selectedUser + "] You sent an image", Color.BLUE);
                        }
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> appendMessage("Error sending image: " + e.getMessage(), Color.RED));
                }
            }).start();
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            new Thread(() -> {
                try {
                    byte[] fileData = Files.readAllBytes(file.toPath());

                    if (fileData.length > 10_000_000) { // 10MB limit
                        SwingUtilities.invokeLater(() -> appendMessage("File too large (max 10MB)", Color.RED));
                        return;
                    }

                    String encodedFile = Base64.getEncoder().encodeToString(fileData);
                    sendChunkedFile(encodedFile, file.getName());

                    SwingUtilities.invokeLater(() -> {
                        if (selectedUser.equals("All")) {
                            appendMessage("You sent file: " + file.getName(), Color.BLUE);
                        } else {
                            appendMessage("[Private to " + selectedUser + "] You sent file: " + file.getName(), Color.BLUE);
                        }
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> appendMessage("Error sending file: " + e.getMessage(), Color.RED));
                }
            }).start();
        }
    }

    private void startVoiceRecording() {
        try {
            voiceRecorder = new VoiceRecorder();
            voiceRecorder.startRecording();
            isRecordingVoice = true;
            recordMicButton.setEnabled(false);
            stopMicButton.setEnabled(true);
            appendMessage("Recording voice...", Color.BLUE);
        } catch (LineUnavailableException e) {
            appendMessage("Error starting voice recording: " + e.getMessage(), Color.RED);
        }
    }

    private void stopVoiceRecording() {
        if (isRecordingVoice && voiceRecorder != null) {
            byte[] voiceData = voiceRecorder.stopRecording();
            isRecordingVoice = false;
            recordMicButton.setEnabled(true);
            stopMicButton.setEnabled(false);

            if (voiceData != null && voiceData.length > 0) {
                new Thread(() -> {
                    try {
                        String encodedVoice = Base64.getEncoder().encodeToString(voiceData);
                        sendChunkedData(encodedVoice, "VOICECHUNK", 400);

                        SwingUtilities.invokeLater(() -> {
                            if (selectedUser.equals("All")) {
                                appendMessage("You sent a voice message", Color.BLUE);
                            } else {
                                appendMessage("[Private to " + selectedUser + "] You sent a voice message", Color.BLUE);
                            }
                        });
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> appendMessage("Error sending voice: " + e.getMessage(), Color.RED));
                    }
                }).start();
            } else {
                appendMessage("No voice data recorded", Color.RED);
            }
        }
    }

    private void sendChunkedData(String data, String chunkType, int chunkSize) {
        String sessionId = Long.toHexString(System.currentTimeMillis());
        int totalChunks = (int) Math.ceil((double) data.length() / chunkSize);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, data.length());
            String chunk = data.substring(start, end);

            StringBuilder message = new StringBuilder();
            message.append(chunkType).append("|")
                    .append("SESSION:").append(sessionId).append("|")
                    .append("CHUNK:").append(i).append("|")
                    .append("TOTAL:").append(totalChunks).append("|");

            if (!selectedUser.equals("All")) {
                message.append("TO:").append(selectedUser).append("|");
            }

            message.append("FROM:").append(username).append("|")
                    .append("DATA:").append(chunk);

            out.println(message.toString());
        }
    }

    private void sendChunkedFile(String data, String filename) {
        String sessionId = Long.toHexString(System.currentTimeMillis());
        int chunkSize = 400;
        int totalChunks = (int) Math.ceil((double) data.length() / chunkSize);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, data.length());
            String chunk = data.substring(start, end);

            StringBuilder message = new StringBuilder();
            message.append("FILECHUNK|")
                    .append("SESSION:").append(sessionId).append("|")
                    .append("CHUNK:").append(i).append("|")
                    .append("TOTAL:").append(totalChunks).append("|")
                    .append("FILENAME:").append(filename).append("|");

            if (!selectedUser.equals("All")) {
                message.append("TO:").append(selectedUser).append("|");
            }

            message.append("FROM:").append(username).append("|")
                    .append("DATA:").append(chunk);

            out.println(message.toString());
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            return originalImage;
        }

        double scale = Math.min((double) MAX_IMAGE_WIDTH / width, (double) MAX_IMAGE_HEIGHT / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resizedImage;
    }

    private void displayImageMessage(String sender, byte[] imageData, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
                BufferedImage image = ImageIO.read(bais);

                if (image != null) {
                    String prefix = isPrivate ? "[Private] " : "";
                    appendImageMessage(prefix + sender + " sent an image:", image, imageData);
                }
            } catch (Exception e) {
                appendMessage("Error displaying image: " + e.getMessage(), Color.RED);
            }
        });
    }

    private void displayFileMessage(String sender, String filename, byte[] fileData, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            String prefix = isPrivate ? "[Private] " : "";
            FileLink fileLink = new FileLink(filename, fileData);
            appendFileMessage(prefix + sender + " sent a file:", fileLink);
        });
    }

    private void displayVoiceMessage(String sender, byte[] voiceData, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String timestamp = sdf.format(new Date());
            String prefix = isPrivate ? "[Private] " : "";
            VoiceLink voiceLink = new VoiceLink(sender, voiceData, timestamp);
            appendVoiceMessage(prefix + sender + " sent a voice message:", voiceLink);
        });
    }

    private void appendMessage(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String timestamp = sdf.format(new Date());
            String colorHex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

            String html = "<div style='color:" + colorHex + ";'>[" + timestamp + "] " + message + "</div>";
            appendToMessageArea(html);
        });
    }

    private void appendImageMessage(String message, BufferedImage image, byte[] imageData) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());

        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("[" + timestamp + "] " + message);
        panel.add(label, BorderLayout.NORTH);

        JLabel imageLabel = new JLabel();
        ImageIcon icon = new ImageIcon(image.getScaledInstance(200, 200, Image.SCALE_SMOOTH));
        imageLabel.setIcon(icon);
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int result = JOptionPane.showOptionDialog(TCPClient.this,
                        "What would you like to do with this image?",
                        "Image Options",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"Save", "Open", "Cancel"},
                        "Save");

                if (result == 0) { // Save
                    JFileChooser fc = new JFileChooser();
                    fc.setSelectedFile(new File("image_" + System.currentTimeMillis() + ".jpg"));
                    if (fc.showSaveDialog(TCPClient.this) == JFileChooser.APPROVE_OPTION) {
                        try {
                            Files.write(fc.getSelectedFile().toPath(), imageData);
                            appendMessage("Image saved to " + fc.getSelectedFile().getAbsolutePath(), Color.GREEN);
                        } catch (IOException e) {
                            appendMessage("Error saving image: " + e.getMessage(), Color.RED);
                        }
                    }
                } else if (result == 1) { // Open
                    try {
                        File tempFile = File.createTempFile("image_", ".jpg");
                        Files.write(tempFile.toPath(), imageData);
                        Desktop.getDesktop().open(tempFile);
                    } catch (IOException e) {
                        appendMessage("Error opening image: " + e.getMessage(), Color.RED);
                    }
                }
            }
        });

        panel.add(imageLabel, BorderLayout.CENTER);
        appendComponentToMessageArea(panel);
    }

    private void appendFileMessage(String message, FileLink fileLink) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("[" + timestamp + "] " + message + " ");
        panel.add(label);
        panel.add(fileLink);

        appendComponentToMessageArea(panel);
    }

    private void appendVoiceMessage(String message, VoiceLink voiceLink) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("[" + timestamp + "] " + message + " ");
        panel.add(label);
        panel.add(voiceLink);

        appendComponentToMessageArea(panel);
    }

    private void appendToMessageArea(String html) {
        try {
            javax.swing.text.Document doc = messageArea.getDocument();
            javax.swing.text.html.HTMLEditorKit kit = (javax.swing.text.html.HTMLEditorKit) messageArea.getEditorKit();
            kit.insertHTML((javax.swing.text.html.HTMLDocument) doc, doc.getLength(), html, 0, 0, null);
            messageArea.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void appendComponentToMessageArea(JComponent component) {
        try {
            javax.swing.text.Document doc = messageArea.getDocument();
            messageArea.setCaretPosition(doc.getLength());
            messageArea.insertComponent(component);
            javax.swing.text.html.HTMLEditorKit kit = (javax.swing.text.html.HTMLEditorKit) messageArea.getEditorKit();
            kit.insertHTML((javax.swing.text.html.HTMLDocument) doc, doc.getLength(), "<br>", 0, 0, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            new TCPClient(args[0]);
        } else {
            String username = JOptionPane.showInputDialog("Enter your username:");
            if (username != null && !username.trim().isEmpty()) {
                new TCPClient(username.trim());
            }
        }
    }
}
