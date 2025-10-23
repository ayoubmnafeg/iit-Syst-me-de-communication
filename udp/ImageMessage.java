import java.io.*;
import java.util.Base64;

/**
 * ImageMessage class for sending compressed image data over UDP
 * Images are compressed and encoded as Base64 strings for transmission
 */
public class ImageMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_IMAGE_SIZE = 50000; // Max 50KB for UDP transmission

    private String sender;
    private String recipient; // null for broadcast
    private byte[] compressedImageData;
    private String imageBase64;

    public ImageMessage(String sender, byte[] compressedImageData, String recipient) {
        this.sender = sender;
        this.compressedImageData = compressedImageData;
        this.recipient = recipient;
        // Encode to Base64 for safe transmission
        this.imageBase64 = Base64.getEncoder().encodeToString(compressedImageData);
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public byte[] getCompressedImageData() {
        return compressedImageData;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public int getSize() {
        return compressedImageData.length;
    }

    public boolean isWithinSize() {
        return compressedImageData.length <= MAX_IMAGE_SIZE;
    }

    public static byte[] decodeBase64(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }

    public boolean isPrivate() {
        return recipient != null && !recipient.isEmpty();
    }
}
