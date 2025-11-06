import javax.swing.*;
import java.awt.*;

public class MulticastApp {
    public static void main(String[] args) {
        // Launch applications on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Get screen dimensions
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int screenWidth = screenSize.width;
                int screenHeight = screenSize.height - 40;

                // Calculate dimensions for positioning (1 émetteur + 3 récepteurs = 4 windows)
                int halfWidth = screenWidth / 2;
                int halfHeight = screenHeight / 2;

                // Create and position Émetteur - Top Left
                Emetteur emetteur = new Emetteur();
                emetteur.setTitle("Émetteur");
                emetteur.setSize(halfWidth, halfHeight);
                emetteur.setLocation(0, 0);
                emetteur.setVisible(true);

                Thread.sleep(300);

                // Create and position Récepteur 1 - Top Right
                Recepteur recepteur1 = new Recepteur();
                recepteur1.setTitle("Récepteur 1");
                recepteur1.setSize(halfWidth, halfHeight);
                recepteur1.setLocation(halfWidth, 0);
                recepteur1.setVisible(true);

                Thread.sleep(300);

                // Create and position Récepteur 2 - Bottom Left
                Recepteur recepteur2 = new Recepteur();
                recepteur2.setTitle("Récepteur 2");
                recepteur2.setSize(halfWidth, halfHeight);
                recepteur2.setLocation(0, halfHeight);
                recepteur2.setVisible(true);

                Thread.sleep(300);

                // Create and position Récepteur 3 - Bottom Right
                Recepteur recepteur3 = new Recepteur();
                recepteur3.setTitle("Récepteur 3");
                recepteur3.setSize(halfWidth, halfHeight);
                recepteur3.setLocation(halfWidth, halfHeight);
                recepteur3.setVisible(true);

                System.out.println("=== Multicast Application Started ===");
                System.out.println("Émetteur: Top-left quarter");
                System.out.println("Récepteur 1: Top-right quarter");
                System.out.println("Récepteur 2: Bottom-left quarter");
                System.out.println("Récepteur 3: Bottom-right quarter");
                System.out.println("\nInstructions:");
                System.out.println("1. Start all 3 Récepteurs by clicking 'Start Listening' and enter a name");
                System.out.println("2. Start the Émetteur by clicking 'Start Émetteur' and enter a name");
                System.out.println("3. Send messages from the Émetteur - all 3 Récepteurs will receive them!");
                System.out.println("\nMulticast Address: 230.0.0.0:4446");

            } catch (InterruptedException e) {
                System.err.println("Error launching applications: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
