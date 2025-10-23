import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * FileLinkLabel - A custom JLabel component that displays file links
 * Users can click on the link to download/save the file
 */
public class FileLink extends JLabel {
    private byte[] fileData;
    private String filename;
    private FileLinkClickListener clickListener;

    public interface FileLinkClickListener {
        void onFileLinkClicked(String filename, byte[] fileData);
    }

    public FileLink(String filename, byte[] fileData, FileLinkClickListener listener) {
        this.filename = filename;
        this.fileData = fileData;
        this.clickListener = listener;

        // Create HTML link-style text
        String sizeStr = getFileSizeString(fileData.length);
        setText("<html><u><font color='blue'>" + filename + " (" + sizeStr + ")</font></u></html>");

        // Set pointer cursor on hover
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Add mouse listener for click and hover effects
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (clickListener != null) {
                    clickListener.onFileLinkClicked(filename, fileData);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // Change text color on hover
                setText("<html><u><font color='darkblue'>" + filename + " (" + sizeStr + ")</font></u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Restore original color
                setText("<html><u><font color='blue'>" + filename + " (" + sizeStr + ")</font></u></html>");
            }
        });
    }

    private String getFileSizeString(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
