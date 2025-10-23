import javax.sound.sampled.*;
import java.io.*;

/**
 * VoiceRecorder - Utility class for recording audio from microphone
 * Records audio to WAV format in memory, returning bytes for transmission
 */
public class VoiceRecorder {
    private TargetDataLine targetDataLine;
    private boolean isRecording = false;
    private Thread recordingThread;
    private ByteArrayOutputStream recordedAudio;

    // Audio format configuration
    private static final float SAMPLE_RATE = 16000.0f; // 16 kHz
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // Mono
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    private AudioFormat audioFormat;

    public VoiceRecorder() {
        // Initialize audio format
        audioFormat = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    }

    /**
     * Start recording audio from microphone
     * @throws LineUnavailableException if microphone is not available
     */
    public void startRecording() throws LineUnavailableException {
        if (isRecording) {
            return;
        }

        recordedAudio = new ByteArrayOutputStream();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Audio line is not supported");
        }

        targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
        targetDataLine.open(audioFormat);
        targetDataLine.start();

        isRecording = true;

        // Start recording in separate thread
        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int bytesRead;

            try {
                while (isRecording) {
                    bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        recordedAudio.write(buffer, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                System.err.println("Recording error: " + e.getMessage());
            }
        });
        recordingThread.start();
    }

    /**
     * Stop recording audio
     * @return WAV file bytes containing the recorded audio
     */
    public byte[] stopRecording() {
        if (!isRecording) {
            return new byte[0];
        }

        isRecording = false;
        targetDataLine.stop();
        targetDataLine.close();

        try {
            recordingThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Convert raw audio to WAV format
        return convertToWAV(recordedAudio.toByteArray());
    }

    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Convert raw audio bytes to WAV format
     * @param rawAudio Raw audio bytes from microphone
     * @return WAV file bytes with proper header
     */
    private byte[] convertToWAV(byte[] rawAudio) {
        ByteArrayOutputStream wavStream = new ByteArrayOutputStream();

        try {
            // WAV file header parameters
            int sampleRate = (int) audioFormat.getSampleRate();
            int numChannels = audioFormat.getChannels();
            int bitsPerSample = audioFormat.getSampleSizeInBits();
            int byteRate = sampleRate * numChannels * bitsPerSample / 8;
            int blockAlign = numChannels * bitsPerSample / 8;
            int dataSize = rawAudio.length;
            int fileSize = 36 + dataSize;

            // RIFF header
            wavStream.write("RIFF".getBytes());
            writeIntLittleEndian(wavStream, fileSize);
            wavStream.write("WAVE".getBytes());

            // fmt subchunk
            wavStream.write("fmt ".getBytes());
            writeIntLittleEndian(wavStream, 16); // Subchunk1Size (16 for PCM)
            writeShortLittleEndian(wavStream, 1); // AudioFormat (1 for PCM)
            writeShortLittleEndian(wavStream, numChannels);
            writeIntLittleEndian(wavStream, sampleRate);
            writeIntLittleEndian(wavStream, byteRate);
            writeShortLittleEndian(wavStream, blockAlign);
            writeShortLittleEndian(wavStream, bitsPerSample);

            // data subchunk
            wavStream.write("data".getBytes());
            writeIntLittleEndian(wavStream, dataSize);
            wavStream.write(rawAudio);

            return wavStream.toByteArray();

        } catch (IOException e) {
            System.err.println("Error converting to WAV: " + e.getMessage());
            return rawAudio; // Return raw audio as fallback
        }
    }

    /**
     * Write a 4-byte integer in little-endian format
     */
    private void writeIntLittleEndian(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    /**
     * Write a 2-byte short in little-endian format
     */
    private void writeShortLittleEndian(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
