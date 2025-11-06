import java.io.*;
import java.util.Base64;

/**
 * FileTransfer utility class for handling file compression and encoding
 * Provides methods to compress files and encode them for TCP transmission
 */
public class FileTransfer {
    public static final int CHUNK_SIZE = 400; // 400 bytes per chunk for compatibility
    public static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB max file size

    /**
     * Read file from disk and return as byte array
     */
    public static byte[] readFile(File file) throws IOException {
        if (file.length() > MAX_FILE_SIZE) {
            throw new IOException("File too large! Max size is " + MAX_FILE_SIZE + " bytes");
        }

        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }
        return data;
    }

    /**
     * Write byte array to file
     */
    public static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    /**
     * Encode file data to Base64 string
     */
    public static String encodeToBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decode Base64 string to file data
     */
    public static byte[] decodeFromBase64(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }

    /**
     * Get file size in human-readable format
     */
    public static String getFileSizeString(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    /**
     * Get file extension (e.g., "jpg", "pdf", "txt")
     */
    public static String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    /**
     * Get file name without extension
     */
    public static String getFileNameWithoutExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
}
