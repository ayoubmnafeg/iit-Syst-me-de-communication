import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.awt.Desktop;

/**
 * FileLinkLabel - A custom JLabel component that displays file links
 * Users can click on the link to Save or Open the file
 */
public class FileLink extends JLabel {
    private byte[] fileData;
    private String filename;

    public FileLink(String filename, byte[] fileData) {
        this.filename = filename;
        this.fileData = fileData;

        // Create HTML link-style text
        String sizeStr = getFileSizeString(fileData.length);
        setText("<html><u><font color='blue'>" + filename + " (" + sizeStr + ")</font></u></html>");

        // Set pointer cursor on hover
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Add mouse listener for click and hover effects
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showSaveOpenDialog();
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

    private void showSaveOpenDialog() {
        // Get parent window
        SwingUtilities.invokeLater(() -> {
            Window parentWindow = SwingUtilities.getWindowAncestor(this);
            if (parentWindow == null) {
                parentWindow = new JFrame();
            }

            // Create option dialog
            String[] options = {"Save", "Open", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                parentWindow,
                "What would you like to do with this file?",
                filename,
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
            );

            if (choice == 0) {
                // Save option
                saveFile();
            } else if (choice == 1) {
                // Open option
                openFile();
            }
            // Cancel or closed: do nothing
        });
    }

    private void saveFile() {
        SwingUtilities.invokeLater(() -> {
            Window parentWindow = SwingUtilities.getWindowAncestor(this);
            if (parentWindow == null) {
                parentWindow = new JFrame();
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(filename));
            int result = fileChooser.showSaveDialog(parentWindow);

            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    File saveFile = fileChooser.getSelectedFile();
                    Files.write(saveFile.toPath(), fileData);
                    JOptionPane.showMessageDialog(
                        parentWindow,
                        "File saved successfully to:\n" + saveFile.getAbsolutePath(),
                        "Save Complete",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                        parentWindow,
                        "Error saving file: " + e.getMessage(),
                        "Save Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }

    private void openFile() {
        try {
            // Create temporary file
            File tempFile = Files.createTempFile("temp_", "_" + filename).toFile();
            Files.write(tempFile.toPath(), fileData);

            // Open file with default application
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(tempFile);
                // Schedule temp file deletion on exit
                tempFile.deleteOnExit();
            } else {
                JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Cannot open file: Desktop operations not supported",
                    "Open Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                "Error opening file: " + e.getMessage(),
                "Open Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private String getFileSizeString(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
