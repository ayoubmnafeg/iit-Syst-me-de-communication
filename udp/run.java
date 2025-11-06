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

                // Create and position the UDP Server - Top Left (Quarter 1)
                UDPServer server = new UDPServer();
                server.setTitle("UDP Server");
                server.setSize(quarterWidth, quarterHeight);
                server.setLocation(0, 0);
                server.setVisible(true);

                // Give server a moment to initialize
                Thread.sleep(500);

                // Client 1 - Top Right (Quarter 2)
                UDPClient client1 = new UDPClient();
                client1.setTitle("UDP Client 1");
                client1.setSize(quarterWidth, quarterHeight);
                client1.setLocation(quarterWidth, 0);
                client1.setVisible(true);

                // Small delay between client launches
                Thread.sleep(300);

                // Client 2 - Bottom Left (Quarter 3)
                UDPClient client2 = new UDPClient();
                client2.setTitle("UDP Client 2");
                client2.setSize(quarterWidth, quarterHeight);
                client2.setLocation(0, quarterHeight);
                client2.setVisible(true);

                Thread.sleep(300);

                // Client 3 - Bottom Right (Quarter 4)
                UDPClient client3 = new UDPClient();
                client3.setTitle("UDP Client 3");
                client3.setSize(quarterWidth, quarterHeight);
                client3.setLocation(quarterWidth, quarterHeight);
                client3.setVisible(true);

                System.out.println("=== UDP Chat Application Started ===");
                System.out.println("Server: Top-left quarter");
                System.out.println("Client 1: Top-right quarter");
                System.out.println("Client 2: Bottom-left quarter");
                System.out.println("Client 3: Bottom-right quarter");
                System.out.println("\nInstructions:");
                System.out.println("1. Click 'Start Server' on the server window");
                System.out.println("2. Click 'Connect' on each client and enter a username");
                System.out.println("3. Start chatting!");

            } catch (InterruptedException e) {
                System.err.println("Error launching applications: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
