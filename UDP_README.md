# UDP Chat System Documentation

## Overview

This is a **UDP-based chat application** that provides connectionless, low-latency communication between multiple clients through a central server. The system supports text messaging (broadcast and private), file transfer, image sharing, and voice messaging with a graphical user interface built using Java Swing.

### Key Characteristics
- **Protocol**: UDP (User Datagram Protocol)
- **Connection Type**: Connectionless, datagram-based
- **Reliability**: Best-effort delivery (no guarantees)
- **Architecture**: Client-Server with heartbeat-based session management

---

## System Architecture

The UDP chat system consists of 8 Java files organized as follows:

```
udp/
├── UDPServer.java          # UDP server with heartbeat monitoring
├── UDPClient.java          # Client GUI with messaging and media features
├── ImageMessage.java       # Data class for image encapsulation
├── FileTransfer.java       # Utility class for file operations
├── FileLink.java           # Clickable file link component
├── VoiceRecorder.java      # Audio recording utility
├── VoiceLink.java          # Clickable voice message component
└── run.java                # Launcher for server + 3 clients
```

---

## File Descriptions and Imports

### 1. UDPServer.java (598 lines)

**Purpose**: UDP server that manages connectionless communication, tracks users via heartbeat mechanism, routes messages, and handles automatic timeout for inactive users.

**Import Statements**:
```java
// GUI Components
import javax.swing.*;
import java.awt.*;

// Networking
import java.net.*;
import java.io.*;

// Date/Time
import java.text.SimpleDateFormat;

// Collections
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
```

**Key Classes**:
- `UDPServer extends JFrame` - Main server class with GUI
- `UserInfo` - Inner class storing user connection information (username, InetAddress, port, lastSeen timestamp)

**Key Methods**:
- `startServer()` - Starts UDP server on specified port
- `stopServer()` - Closes socket and stops heartbeat monitoring thread
- `broadcastToAllUsers()` - Sends datagram to all active users
- `broadcastUserList()` - Sends USERLIST message to all clients
- `checkHeartbeats()` - Background thread checking for timed-out users (runs every 5 seconds)
- `handleMessage()` - Processes CONNECT, DISCONNECT, HEARTBEAT, private messages, and media chunks

**Heartbeat Configuration**:
- **Check Interval**: Every 5 seconds
- **Timeout Duration**: 60 seconds of inactivity
- **Automatic Cleanup**: Removes and notifies when users timeout

---

### 2. UDPClient.java (1468 lines)

**Purpose**: UDP client application with comprehensive GUI for connectionless communication, supporting all media types with automatic heartbeat transmission.

**Import Statements**:
```java
// GUI Components
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

// Image Processing
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

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
- `UDPClient extends JFrame` - Main client class with GUI
- `UserStatus` - Inner class representing user connection status
- `ImageChunkBuffer` - Buffers image chunks with 30-second timeout
- `FileChunkBuffer` - Buffers file chunks with 60-second timeout
- `VoiceChunkBuffer` - Buffers voice message chunks with 120-second timeout

**Key Methods**:
- `connect()` - Creates UDP socket, prompts for username, sends CONNECT datagram
- `disconnect()` - Sends DISCONNECT datagram and closes socket
- `sendMessage()` - Sends text via UDP datagram (broadcast or private)
- `sendImage()` - Compresses and sends images in 500-byte chunks
- `sendFile()` - Sends files in 400-byte chunks with filename
- `startVoiceRecording()` / `stopVoiceRecording()` - Records and sends voice in 400-byte chunks
- `sendChunkedData()` - Generic method for sending chunked media with 10ms delay between chunks
- `handleImageChunk()` / `handleFileChunk()` / `handleVoiceChunk()` - Reassembles received chunks
- `updateUserList()` - Updates connected users list (excludes self)
- `startHeartbeat()` - Background thread sending heartbeat every 30 seconds

**Heartbeat Configuration**:
- **Send Interval**: Every 30 seconds
- **Socket Timeout**: 5 seconds for receive operations

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

### 4. FileTransfer.java (76 lines)

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

**Constants**:
- `CHUNK_SIZE`: 400 bytes
- `MAX_FILE_SIZE`: 10MB (10 * 1024 * 1024 bytes)

---

### 5. FileLink.java (151 lines)

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

### 6. VoiceRecorder.java (163 lines)

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
- Sample Rate: 16000 Hz
- Bit Depth: 16-bit
- Channels: Mono
- Encoding: PCM signed little-endian

---

### 7. VoiceLink.java (213 lines)

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
- `playVoice()` - Plays voice message using Java Sound API (Clip)
- `saveVoice()` - Saves WAV file to disk
- `estimateDuration()` - Calculates duration from WAV header
- `readIntLittleEndian()`, `readShortLittleEndian()` - Reads WAV header data

---

### 8. run.java (71 lines)

**Purpose**: Application launcher that starts the UDP server and 3 clients in a tiled screen layout.

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

**Note**: Unlike TCP version, clients prompt for username on connection (no pre-configured names)

---

## Features

### 1. Connection Management (Heartbeat-Based)
- **Connectionless Communication**: No persistent connection maintained
- **Heartbeat Mechanism**:
  - Clients send heartbeat every 30 seconds
  - Server monitors and times out after 60 seconds of inactivity
  - Automatic cleanup of inactive users
- **User Tracking**: Server tracks users by InetAddress + port + username
- **User List Broadcasting**: Server broadcasts updated user list to all active clients

### 2. Text Messaging
- **Broadcast Messages**: Send messages to all connected users
- **Private Messages**: Send messages to specific users using `TO:username` format
- **Timestamped Messages**: All messages include timestamps
- **HTML Formatting**: Messages displayed with rich HTML formatting
- **Best-Effort Delivery**: No guarantee of message delivery or ordering

### 3. Image Transfer
- **Automatic Resizing**: Images resized to maximum 150x150 pixels
- **Compression**: Images compressed to fit within 50KB limit
- **Chunked Transfer**: Images sent in 500-byte chunks with 10ms delay
- **Base64 Encoding**: Images encoded for safe transmission
- **Chunk Timeout**: 30-second timeout for incomplete image reassembly
- **Preview Display**: Received images displayed inline in chat
- **Save/Open Options**: Click image links to save or open

### 4. File Transfer
- **Maximum Size**: Files up to 10MB supported
- **Chunked Transfer**: Files sent in 400-byte chunks with 10ms delay
- **Filename Preservation**: Original filename maintained in first chunk
- **Chunk Timeout**: 60-second timeout for incomplete file reassembly
- **Progress Indication**: File size displayed
- **Save/Open Options**: Click file links to save or open with default application

### 5. Voice Messages
- **Recording**: Record voice messages using microphone
- **Format**: WAV format (16kHz, 16-bit, mono, PCM)
- **Chunked Transfer**: Voice messages sent in 400-byte chunks with 10ms delay
- **Chunk Timeout**: 120-second timeout for incomplete voice reassembly
- **Playback**: Click voice links to play audio
- **Duration Display**: Voice message duration displayed
- **Save Option**: Save voice messages as WAV files

---

## Protocol Message Format

The UDP chat system uses text-based protocol messages sent as datagrams:

### Connection Messages
```
CONNECT:<username>          # Client connects with username
DISCONNECT:<username>       # Client disconnects
HEARTBEAT:<username>        # Client heartbeat signal (every 30s)
```

### Text Messages
```
<username>: <message>       # Broadcast message to all users
TO:<recipient>:<message>    # Private message to specific user
```

### User List
```
USERLIST:<user1>,<user2>,<user3>,...    # List of active users
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
- **Connection Type**: UDP DatagramSocket
- **Max Datagram Size**: 100KB (102400 bytes)
- **Socket Timeout**: 5 seconds for receive operations
- **Chunk Send Delay**: 10ms between chunks (prevents network flooding)

### Heartbeat System
- **Client Send Interval**: 30 seconds
- **Server Check Interval**: 5 seconds
- **Timeout Duration**: 60 seconds of inactivity
- **Automatic Cleanup**: Yes, with notification to all clients

### Transfer Limits
- **Image Size**: Maximum 150x150 pixels, 50KB
- **File Size**: Maximum 10MB
- **Image Chunk Size**: 500 bytes
- **File Chunk Size**: 400 bytes
- **Voice Chunk Size**: 400 bytes

### Chunk Reassembly Timeouts
- **Image Chunks**: 30 seconds
- **File Chunks**: 60 seconds
- **Voice Chunks**: 120 seconds

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
cd udp
javac *.java
java run
```
This will start:
- 1 UDP Server (top-left quadrant)
- 3 UDP Clients (top-right, bottom-left, bottom-right quadrants)
- Each client will prompt for username on connection

### Method 2: Manual Start

**Start the Server:**
```bash
cd udp
javac UDPServer.java
java UDPServer
```
- Enter port number (default: 12345)
- Click "Start Server"
- Server will begin monitoring heartbeats

**Start Client(s):**
```bash
cd udp
javac UDPClient.java
java UDPClient
```
- Enter server IP address (default: localhost)
- Enter port number (default: 12345)
- Click "Connect"
- Enter username when prompted
- Client automatically sends heartbeat every 30 seconds

---

## Usage Guide

### Staying Connected
- **Important**: Keep the client application running
- Heartbeat is sent automatically every 30 seconds
- Server will remove you after 60 seconds of no heartbeat
- Application will notify you if disconnected

### Sending Messages
1. **Broadcast**: Type message and click "Send" (sends to all users)
2. **Private**: Select user from list, type message, click "Send"
3. **Note**: No delivery guarantee with UDP

### Sending Images
1. Click "Send Image" button
2. Select image file (JPG, PNG, GIF)
3. Image will be resized and compressed automatically
4. Sent in 500-byte chunks with 10ms delay
5. Recipient has 30 seconds to receive all chunks

### Sending Files
1. Click "Send File" button
2. Select any file (max 10MB)
3. File sent in 400-byte chunks with 10ms delay
4. Recipient has 60 seconds to receive all chunks

### Voice Messages
1. Click "Start Recording" button
2. Speak into microphone
3. Click "Stop Recording" button
4. Voice sent in 400-byte chunks with 10ms delay
5. Recipient has 120 seconds to receive all chunks

### Receiving Media
- **Images**: Click to view full size, save, or close
- **Files**: Click to save or open with default application
- **Voice**: Click to play or save as WAV file
- **Note**: Incomplete chunks timeout and are discarded

---

## Key Differences from TCP Implementation

| Feature | UDP | TCP |
|---------|-----|-----|
| **Connection** | Connectionless (datagrams) | Persistent, connection-oriented |
| **Reliability** | No delivery guarantee | Guaranteed delivery and ordering |
| **Disconnection** | Heartbeat timeout (60s) | Automatic detection |
| **Client Tracking** | InetAddress + port + username | Socket and ClientHandler |
| **Heartbeat** | Required (30s client, 60s timeout) | Optional monitoring |
| **Buffer Size** | Large 100KB datagram buffer | Standard stream buffers |
| **Message Delivery** | DatagramPacket | PrintWriter/BufferedReader |
| **Overhead** | Lower (no connection maintenance) | Higher (connection state) |
| **Chunk Delay** | 10ms between chunks | No artificial delay |
| **Chunk Timeout** | 30-120s depending on type | No timeout (reliable) |
| **Socket Timeout** | 5 seconds receive timeout | Blocking I/O |

---

## Advantages of UDP Implementation

1. **Lower Latency**: No connection establishment overhead
2. **Lower Resource Usage**: No persistent connections maintained
3. **Broadcast Friendly**: Easier to implement multicast
4. **Simpler Server**: No thread per client required
5. **NAT Traversal**: Often easier through firewalls
6. **Scalability**: Can handle more concurrent users

---

## Challenges of UDP Implementation

1. **No Delivery Guarantee**: Messages can be lost
2. **No Ordering**: Messages can arrive out of order
3. **Manual Timeout Management**: Must implement heartbeat system
4. **Chunk Reassembly**: Must handle incomplete chunk sets
5. **Network Flooding**: Must add delays between chunks
6. **Session Management**: Must manually track user sessions

---

## Use Cases

Choose UDP chat when:
- Low latency is critical (real-time applications)
- Some message loss is acceptable
- You need to support many concurrent users
- Broadcasting/multicasting is important
- Server resources are limited
- Firewall/NAT traversal is needed

Choose TCP instead when:
- Message delivery reliability is critical
- You need guaranteed ordering of messages
- Large file transfers must be complete
- Connection stability is more important than low latency
- You want automatic disconnect detection

---

## Troubleshooting

### Server Won't Start
- Check if port 12345 is already in use
- Try a different port number
- Check firewall allows UDP traffic
- Verify UDP is not blocked by network policy

### Client Can't Connect
- Verify server is running
- Check IP address and port match server
- Ensure firewall allows UDP outbound
- Test with "localhost" for local connections
- Check if UDP port is open

### Connection Keeps Timing Out
- Check network stability
- Verify heartbeat is being sent (every 30s)
- Check server heartbeat monitoring (60s timeout)
- Look for network congestion
- Try reducing file transfer sizes

### Images/Files Won't Send
- Check file size limits (50KB images, 10MB files)
- Verify network allows large UDP packets
- Check for packet loss (try smaller files)
- Ensure recipient is still connected
- Watch for chunk timeout messages

### Chunks Not Reassembling
- Check chunk timeout settings:
  - Images: 30 seconds
  - Files: 60 seconds
  - Voice: 120 seconds
- Verify network is not reordering packets significantly
- Check for high packet loss rate
- Try reducing chunk size if network is unreliable

### Voice Recording Issues
- Check microphone permissions
- Verify microphone is connected and working
- Try adjusting microphone volume
- Ensure audio format is supported (16kHz, 16-bit, mono)

### High Packet Loss
- Reduce chunk send rate (increase delay from 10ms)
- Send smaller files
- Check network quality
- Try using TCP implementation instead

---

## Network Considerations

### UDP Packet Loss
UDP does not guarantee delivery. In networks with:
- **High packet loss**: Consider TCP implementation
- **Moderate packet loss**: Increase chunk timeouts
- **Low packet loss**: UDP works well

### Firewall Configuration
UDP may require firewall configuration:
- Open UDP port 12345 (or chosen port)
- Allow outbound UDP traffic
- Some networks block all UDP traffic

### MTU and Fragmentation
- Chunk sizes (400-500 bytes) are well below typical MTU (1500 bytes)
- This minimizes IP fragmentation
- Larger chunks risk fragmentation and packet loss

---

## Future Enhancements

Potential improvements:
- Forward Error Correction (FEC) for reliability
- Automatic retry for critical messages
- Adaptive chunk sizing based on network conditions
- End-to-end encryption
- Message sequence numbers
- Duplicate detection
- NAT hole punching for P2P
- Multicast group support
- Congestion control
- QoS prioritization
- Message acknowledgments (selective reliability)

---

## Performance Optimization Tips

1. **Adjust Chunk Delay**: Increase from 10ms if network flooding occurs
2. **Monitor Heartbeat**: Ensure 30s interval is sufficient for your network
3. **Tune Timeouts**: Adjust chunk timeouts based on network latency
4. **Buffer Size**: 100KB buffer is conservative; can be adjusted
5. **Socket Timeout**: 5s receive timeout can be tuned based on needs

---

## Security Considerations

Current implementation has no security features:
- **No encryption**: All data sent in plaintext (Base64 is encoding, not encryption)
- **No authentication**: Anyone can connect with any username
- **No validation**: Malicious packets could crash clients
- **No rate limiting**: Vulnerable to flooding attacks

For production use, consider:
- DTLS (Datagram Transport Layer Security)
- Authentication tokens
- Input validation and sanitization
- Rate limiting and throttling
- IP filtering/whitelisting

---

## License

Educational project for IIT System Communication course.

## Author

Generated for TCP/UDP communication systems study.
