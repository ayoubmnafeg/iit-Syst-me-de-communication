# TCP Chat System Documentation

## Overview

This is a **TCP-based chat application** that provides reliable, connection-oriented communication between multiple clients through a central server. The system supports text messaging (broadcast and private), file transfer, image sharing, and voice messaging with a graphical user interface built using Java Swing.

### Key Characteristics
- **Protocol**: TCP (Transmission Control Protocol)
- **Connection Type**: Persistent, connection-oriented
- **Reliability**: Guaranteed delivery and ordering
- **Architecture**: Client-Server with multi-threaded client handling

---

## System Architecture

The TCP chat system consists of 8 Java files organized as follows:

```
tcp/
├── TCPServer.java          # Multi-threaded server managing all connections
├── TCPClient.java          # Client GUI with messaging and media features
├── ImageMessage.java       # Data class for image encapsulation
├── FileTransfer.java       # Utility class for file operations
├── FileLink.java           # Clickable file link component
├── VoiceRecorder.java      # Audio recording utility
├── VoiceLink.java          # Clickable voice message component
└── run.java                # Launcher for server + 3 clients
```

---

## File Descriptions and Imports

### 1. TCPServer.java (277 lines)

**Purpose**: Multi-threaded TCP server that manages client connections, broadcasts messages to all users, routes private messages, and handles heartbeat monitoring.

**Import Statements**:
```java
// GUI Components
import javax.swing.*;
import java.awt.*;

// Networking
import java.io.*;
import java.net.*;

// Date/Time
import java.text.SimpleDateFormat;

// Collections
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
```

**Key Classes**:
- `TCPServer extends JFrame` - Main server class with GUI
- `ClientHandler implements Runnable` - Inner class handling individual client connections

**Key Methods**:
- `startServer()` - Starts server on specified port
- `stopServer()` - Closes all connections and stops server
- `acceptClients()` - Accepts incoming client connections
- `broadcast(String message, String excludeUser)` - Sends message to all clients
- `broadcastUserList()` - Sends updated user list to all clients
- `handleMessage(String message)` - Processes CONNECT, DISCONNECT, HEARTBEAT, private messages, and chunks

---

### 2. TCPClient.java (976 lines)

**Purpose**: TCP client application with comprehensive GUI for text messaging, image sharing, file transfer, and voice recording/playback.

**Import Statements**:
```java
// GUI Components
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;

// Image Processing
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;

// Audio
import javax.sound.sampled.LineUnavailableException;

// Networking
import java.net.*;
import java.io.*;

// File Operations
import java.nio.file.Files;

// Date/Time
import java.text.SimpleDateFormat;

// Collections and Encoding
import java.util.*;
import java.util.Base64;
```

**Key Classes**:
- `TCPClient extends JFrame` - Main client class with GUI
- `UserStatus` - Inner class representing user connection status
- `ImageChunkBuffer` - Buffers image chunks for reassembly
- `FileChunkBuffer` - Buffers file chunks with filename metadata
- `VoiceChunkBuffer` - Buffers voice message chunks

**Key Methods**:
- `connect()` - Connects to TCP server and sends CONNECT message
- `disconnect()` - Sends DISCONNECT message and closes connection
- `receiveMessages()` - Background thread receiving messages from server
- `sendMessage()` - Sends text messages (broadcast or private)
- `sendImage()` - Resizes, compresses, and sends images in chunks
- `sendFile()` - Reads and sends files in chunks with metadata
- `startVoiceRecording()` / `stopVoiceRecording()` - Records and sends voice messages
- `handleImageChunk()` / `handleFileChunk()` / `handleVoiceChunk()` - Reassembles received chunks
- `displayImageMessage()` / `displayFileMessage()` / `displayVoiceMessage()` - Displays received media

---

### 3. ImageMessage.java (57 lines)

**Purpose**: Data class for encapsulating image messages with sender, recipient, and compressed image data.

**Import Statements**:
```java
// Serialization
import java.io.*;

// Encoding
import java.util.Base64;
```

**Key Classes**:
- `ImageMessage implements Serializable` - Image message data structure

**Key Methods**:
- `getSender()`, `getRecipient()`, `getCompressedImageData()`, `getImageBase64()`
- `isWithinSize()` - Checks if image is under 50KB limit
- `isPrivate()` - Determines if message is private
- `static decodeBase64()` - Decodes Base64 string to byte array

---

### 4. FileLink.java (151 lines)

**Purpose**: Custom JLabel component that displays clickable file links with Save/Open functionality.

**Import Statements**:
```java
// GUI Components
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// File Operations
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.awt.Desktop;
```

**Key Classes**:
- `FileLink extends JLabel` - Custom clickable file link component

**Key Methods**:
- `showSaveOpenDialog()` - Shows Save/Open/Cancel dialog
- `saveFile()` - Saves file to disk with file chooser
- `openFile()` - Opens file with system default application
- `getFileSizeString()` - Formats file size (B, KB, MB, GB)

---

### 5. VoiceRecorder.java (163 lines)

**Purpose**: Records audio from the microphone and converts raw audio data to WAV format.

**Import Statements**:
```java
// Audio Recording
import javax.sound.sampled.*;

// I/O Operations
import java.io.*;
```

**Key Classes**:
- `VoiceRecorder` - Audio recording utility

**Key Methods**:
- `startRecording()` - Starts capturing audio from microphone
- `stopRecording()` - Stops recording and returns WAV-formatted bytes
- `convertToWAV()` - Adds WAV header to raw audio data
- `writeIntLittleEndian()`, `writeShortLittleEndian()` - Helper methods for WAV format

**Audio Specifications**:
- Sample Rate: 16kHz
- Bit Depth: 16-bit
- Channels: Mono
- Encoding: PCM signed

---

### 6. VoiceLink.java (221 lines)

**Purpose**: Custom JLabel component that displays clickable voice message links with Play/Save functionality.

**Import Statements**:
```java
// Audio Playback
import javax.sound.sampled.*;

// GUI Components
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// I/O Operations
import java.io.*;
```

**Key Classes**:
- `VoiceLink extends JLabel` - Custom clickable voice message component

**Key Methods**:
- `showVoiceOptionsDialog()` - Shows Play/Save/Cancel dialog
- `playVoice()` - Plays voice message using Java Sound API
- `saveVoice()` - Saves WAV file to disk
- `estimateDuration()` - Calculates duration from WAV header
- `readIntLittleEndian()`, `readShortLittleEndian()` - Reads WAV header data

---

### 7. FileTransfer.java (76 lines)

**Purpose**: Static utility class providing file operations and Base64 encoding/decoding.

**Import Statements**:
```java
// I/O Operations
import java.io.*;

// Encoding
import java.util.Base64;
```

**Key Classes**:
- `FileTransfer` - Static utility class

**Key Methods**:
- `readFile()` - Reads file to byte array (max 10MB)
- `writeFile()` - Writes byte array to file
- `encodeToBase64()`, `decodeFromBase64()` - Base64 conversion
- `getFileSizeString()` - Formats file size for display
- `getFileExtension()`, `getFileNameWithoutExtension()` - File name parsing

---

### 8. run.java (76 lines)

**Purpose**: Application launcher that starts the TCP server and 3 clients in a tiled screen layout.

**Import Statements**:
```java
// GUI Components
import javax.swing.*;
import java.awt.*;
```

**Key Classes**:
- `run` - Main launcher class

**Key Methods**:
- `main()` - Launches server (top-left) and 3 clients (top-right, bottom-left, bottom-right)

**Pre-configured Usernames**:
- Client 1: Alice
- Client 2: Bob
- Client 3: Charlie

---

## Features

### 1. Connection Management
- **Persistent Connections**: TCP maintains open connections between server and clients
- **Automatic Disconnect Detection**: Server detects when clients disconnect
- **Heartbeat Monitoring**: Clients send periodic heartbeat messages
- **User List Broadcasting**: Server broadcasts updated user list to all connected clients

### 2. Text Messaging
- **Broadcast Messages**: Send messages to all connected users
- **Private Messages**: Send messages to specific users using `TO:username` format
- **Timestamped Messages**: All messages include timestamps
- **HTML Formatting**: Messages displayed with rich HTML formatting

### 3. Image Transfer
- **Automatic Resizing**: Images resized to maximum 150x150 pixels
- **Compression**: Images compressed to fit within 50KB limit
- **Chunked Transfer**: Images sent in 500-byte chunks
- **Base64 Encoding**: Images encoded for safe text-based transmission
- **Preview Display**: Received images displayed inline in chat
- **Save/Open Options**: Click image links to save or open

### 4. File Transfer
- **Maximum Size**: Files up to 10MB supported
- **Chunked Transfer**: Files sent in 400-byte chunks
- **Filename Preservation**: Original filename maintained
- **Progress Indication**: File size displayed
- **Save/Open Options**: Click file links to save or open with default application

### 5. Voice Messages
- **Recording**: Record voice messages using microphone
- **Format**: WAV format (16kHz, 16-bit, mono, PCM)
- **Chunked Transfer**: Voice messages sent in 400-byte chunks
- **Playback**: Click voice links to play audio
- **Duration Display**: Voice message duration displayed
- **Save Option**: Save voice messages as WAV files

---

## Protocol Message Format

The TCP chat system uses text-based protocol messages:

### Connection Messages
```
CONNECT:<username>          # Client connects with username
DISCONNECT:<username>       # Client disconnects
HEARTBEAT:<username>        # Client heartbeat signal
HEARTBEAT_ACK               # Server heartbeat acknowledgment
```

### Text Messages
```
<username>: <message>       # Broadcast message to all users
TO:<recipient>:<message>    # Private message to specific user
```

### User List
```
USERLIST:<user1>,<user2>,<user3>,...    # List of connected users
```

### Media Transfer (Chunked)
```
FILECHUNK:<filename>:<base64_data>      # File chunk with filename
IMGCHUNK:<base64_data>                   # Image chunk
VOICECHUNK:<base64_data>                 # Voice chunk
```

### Private Media Transfer
```
PRIVATE:TO:<recipient>:FILECHUNK:<filename>:<base64_data>
PRIVATE:TO:<recipient>:IMGCHUNK:<base64_data>
PRIVATE:TO:<recipient>:VOICECHUNK:<base64_data>
```

---

## Technical Specifications

### Network Configuration
- **Default Port**: 12345
- **Connection Type**: TCP/IP Socket
- **Thread Model**: One thread per client (ClientHandler)
- **Data Structure**: ConcurrentHashMap for thread-safe user storage

### Transfer Limits
- **Image Size**: Maximum 150x150 pixels, 50KB
- **File Size**: Maximum 10MB
- **Image Chunk Size**: 500 bytes
- **File Chunk Size**: 400 bytes
- **Voice Chunk Size**: 400 bytes

### Audio Specifications
- **Sample Rate**: 16000 Hz
- **Bit Depth**: 16-bit
- **Channels**: Mono
- **Encoding**: PCM signed little-endian
- **Format**: WAV

### Data Encoding
- **Binary Data**: Base64 encoding
- **Text**: UTF-8 encoding
- **Timestamps**: SimpleDateFormat with "HH:mm:ss" pattern

---

## How to Run

### Method 1: Using the Launcher
```bash
cd tcp
javac *.java
java run
```
This will start:
- 1 TCP Server (top-left quadrant)
- 3 TCP Clients (top-right, bottom-left, bottom-right quadrants)
- Pre-configured with usernames: Alice, Bob, Charlie

### Method 2: Manual Start

**Start the Server:**
```bash
cd tcp
javac TCPServer.java
java TCPServer
```
- Enter port number (default: 12345)
- Click "Start Server"

**Start Client(s):**
```bash
cd tcp
javac TCPClient.java
java TCPClient
```
- Enter server IP address (default: localhost)
- Enter port number (default: 12345)
- Click "Connect"
- Enter username when prompted

---

## Usage Guide

### Sending Messages
1. **Broadcast**: Type message and click "Send" (sends to all users)
2. **Private**: Select user from list, type message, click "Send"

### Sending Images
1. Click "Send Image" button
2. Select image file (JPG, PNG, GIF)
3. Image will be resized and compressed automatically
4. Sent in chunks to recipient(s)

### Sending Files
1. Click "Send File" button
2. Select any file (max 10MB)
3. File sent in chunks with filename

### Voice Messages
1. Click "Start Recording" button
2. Speak into microphone
3. Click "Stop Recording" button
4. Voice message sent in chunks

### Receiving Media
- **Images**: Click to view full size, save, or close
- **Files**: Click to save or open with default application
- **Voice**: Click to play or save as WAV file

---

## Key Differences from UDP Implementation

| Feature | TCP | UDP |
|---------|-----|-----|
| **Connection** | Persistent, connection-oriented | Connectionless |
| **Reliability** | Guaranteed delivery and ordering | No delivery guarantee |
| **Disconnection** | Automatic detection | Requires heartbeat timeout (60s) |
| **Client Tracking** | By Socket and ClientHandler | By InetAddress + port |
| **Heartbeat** | Optional monitoring | Required (every 30s) |
| **Buffer Size** | Standard stream buffers | Large 100KB datagram buffers |
| **Message Delivery** | PrintWriter/BufferedReader | DatagramPacket |
| **Overhead** | Higher (connection maintenance) | Lower (no connection) |

---

## Advantages of TCP Implementation

1. **Reliability**: Guaranteed message delivery and ordering
2. **Automatic Connection Management**: No need for manual heartbeat timeout tracking
3. **Simpler Code**: No need to handle packet loss or reordering
4. **Better for Large Files**: Reliable streaming without manual retransmission
5. **Connection Status**: Immediate notification when client disconnects

---

## Use Cases

Choose TCP chat when:
- Message delivery reliability is critical
- You need guaranteed ordering of messages
- Large file transfers are common
- Connection stability is more important than low latency
- You want automatic disconnect detection

Choose UDP instead when:
- Low latency is critical (real-time applications)
- Some message loss is acceptable
- Broadcasting to many recipients
- Firewall/NAT traversal is needed

---

## Troubleshooting

### Server Won't Start
- Check if port 12345 is already in use
- Try a different port number
- Check firewall settings

### Client Can't Connect
- Verify server is running
- Check IP address and port match server
- Ensure firewall allows connection
- Test with "localhost" for local connections

### Images Won't Send
- Check file size (must be under 50KB after compression)
- Verify file format (JPG, PNG, GIF supported)
- Check network connection

### Files Won't Send
- Verify file size is under 10MB
- Check disk space on receiving end
- Ensure connection is stable

### Voice Recording Issues
- Check microphone permissions
- Verify microphone is connected and working
- Try adjusting microphone volume

---

## Future Enhancements

Potential improvements:
- End-to-end encryption for security
- Message history persistence
- File transfer progress bars
- Video streaming support
- Group chat rooms
- Emoji support
- File drag-and-drop
- Message search functionality
- User authentication
- SSL/TLS encryption

---

## License

Educational project for IIT System Communication course.

## Author

Generated for TCP/UDP communication systems study.
