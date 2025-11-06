import javax.swing.*;
import java.awt.*;

public class run {
    public static void main(String[] args) {
        // Launch applications on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Get screen dimensions
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int screenWidth = screenSize.width;
                int screenHeight = screenSize.height - 40;

                // Calculate quarter dimensions
                int quarterWidth = screenWidth / 2;
                int quarterHeight = screenHeight / 2;

                // Create and position the TCP Server - Top Left (Quarter 1)
                TCPServer server = new TCPServer();
                server.setTitle("TCP Server");
                server.setSize(quarterWidth, quarterHeight);
                server.setLocation(0, 0);
                server.setVisible(true);

                // Give server a moment to initialize
                Thread.sleep(500);

                // Client 1 - Top Right (Quarter 2)
                TCPClient client1 = new TCPClient("Alice");
                client1.setTitle("TCP Client 1 - Alice");
                client1.setSize(quarterWidth, quarterHeight);
                client1.setLocation(quarterWidth, 0);
                client1.setVisible(true);

                // Small delay between client launches
                Thread.sleep(300);

                // Client 2 - Bottom Left (Quarter 3)
                TCPClient client2 = new TCPClient("Bob");
                client2.setTitle("TCP Client 2 - Bob");
                client2.setSize(quarterWidth, quarterHeight);
                client2.setLocation(0, quarterHeight);
                client2.setVisible(true);

                Thread.sleep(300);

                // Client 3 - Bottom Right (Quarter 4)
                TCPClient client3 = new TCPClient("Charlie");
                client3.setTitle("TCP Client 3 - Charlie");
                client3.setSize(quarterWidth, quarterHeight);
                client3.setLocation(quarterWidth, quarterHeight);
                client3.setVisible(true);

                System.out.println("=== TCP Chat Application Started ===");
                System.out.println("Server: Top-left quarter");
                System.out.println("Client 1 (Alice): Top-right quarter");
                System.out.println("Client 2 (Bob): Bottom-left quarter");
                System.out.println("Client 3 (Charlie): Bottom-right quarter");
                System.out.println("\nInstructions:");
                System.out.println("1. Click 'Start' on the server window");
                System.out.println("2. Click 'Connect' on each client");
                System.out.println("3. Start chatting!");
                System.out.println("\nFeatures:");
                System.out.println("- Text messaging (broadcast and private)");
                System.out.println("- Image sharing");
                System.out.println("- File transfer");
                System.out.println("- Voice messaging");

            } catch (InterruptedException e) {
                System.err.println("Error launching applications: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
