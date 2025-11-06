import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;

/**
 * VoiceLink - A custom JLabel component for voice messages
 * Users can click to play, save, or delete voice messages
 */
public class VoiceLink extends JLabel {
    private byte[] voiceData;
    private String sender;
    private long duration; // Estimated duration in seconds

    public VoiceLink(byte[] voiceData) {
        this("Unknown", voiceData, "");
    }

    public VoiceLink(String sender, byte[] voiceData, String timestamp) {
        this.sender = sender;
        this.voiceData = voiceData;

        // Estimate duration based on WAV file (rough calculation)
        this.duration = estimateDuration(voiceData);

        // Create HTML link-style text
        String sizeStr = getFileSizeString(voiceData.length);
        setText("<html><u><font color='blue'>🔊 Voice (" + duration + "s, " + sizeStr + ")</font></u></html>");

        // Set pointer cursor on hover
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Add mouse listener for click and hover effects
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showVoiceOptionsDialog();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // Change text color on hover
                setText("<html><u><font color='darkblue'>🔊 Voice (" + duration + "s, " + sizeStr + ")</font></u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Restore original color
                setText("<html><u><font color='blue'>🔊 Voice (" + duration + "s, " + sizeStr + ")</font></u></html>");
            }
        });
    }

    private void showVoiceOptionsDialog() {
        SwingUtilities.invokeLater(() -> {
            Window parentWindow = SwingUtilities.getWindowAncestor(this);
            if (parentWindow == null) {
                parentWindow = new JFrame();
            }

            // Create option dialog
            String[] options = {"Play", "Save", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    parentWindow,
                    "Voice message (" + duration + "s)\n\nWhat would you like to do?",
                    "Voice Message",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) {
                // Play option
                playVoice();
            } else if (choice == 1) {
                // Save option
                saveVoice();
            }
            // Cancel or closed: do nothing
        });
    }

    private void playVoice() {
        new Thread(() -> {
            try {
                // Parse WAV file and play it
                ByteArrayInputStream bais = new ByteArrayInputStream(voiceData);
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais);

                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();

                // Show playing message
                Window parentWindow = SwingUtilities.getWindowAncestor(this);
                if (parentWindow == null) {
                    parentWindow = new JFrame();
                }

                final Window finalWindow = parentWindow;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            finalWindow,
                            "Playing voice message...\nDuration: " + duration + " seconds",
                            "Playing",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                });

                // Wait for playback to finish
                while (clip.isRunning()) {
                    Thread.sleep(100);
                }
                clip.close();

            } catch (Exception e) {
                Window parentWindow = SwingUtilities.getWindowAncestor(this);
                if (parentWindow == null) {
                    parentWindow = new JFrame();
                }

                final Window finalWindow = parentWindow;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            finalWindow,
                            "Error playing voice: " + e.getMessage(),
                            "Play Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }).start();
    }

    private void saveVoice() {
        SwingUtilities.invokeLater(() -> {
            Window parentWindow = SwingUtilities.getWindowAncestor(this);
            if (parentWindow == null) {
                parentWindow = new JFrame();
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("voice_" + System.currentTimeMillis() + ".wav"));
            int result = fileChooser.showSaveDialog(parentWindow);

            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    File saveFile = fileChooser.getSelectedFile();
                    FileOutputStream fos = new FileOutputStream(saveFile);
                    fos.write(voiceData);
                    fos.close();

                    JOptionPane.showMessageDialog(
                            parentWindow,
                            "Voice saved successfully to:\n" + saveFile.getAbsolutePath(),
                            "Save Complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                            parentWindow,
                            "Error saving voice: " + e.getMessage(),
                            "Save Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }

    /**
     * Estimate duration of voice message in seconds
     * WAV format: sample_rate * channels * bytes_per_sample
     * For 16kHz, mono, 16-bit: 16000 * 1 * 2 = 32000 bytes per second
     */
    private long estimateDuration(byte[] wavData) {
        if (wavData.length < 44) {
            return 0; // Invalid WAV file
        }

        try {
            // Read sample rate from WAV header (bytes 24-27)
            int sampleRate = readIntLittleEndian(wavData, 24);
            // Read number of channels (bytes 22-23)
            int channels = readShortLittleEndian(wavData, 22);
            // Read bits per sample (bytes 34-35)
            int bitsPerSample = readShortLittleEndian(wavData, 34);

            int bytesPerSecond = sampleRate * channels * bitsPerSample / 8;
            int audioDataSize = wavData.length - 44; // Skip WAV header (44 bytes)

            return audioDataSize / bytesPerSecond;
        } catch (Exception e) {
            return 0;
        }
    }

    private int readIntLittleEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24));
    }

    private int readShortLittleEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8));
    }

    private String getFileSizeString(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
