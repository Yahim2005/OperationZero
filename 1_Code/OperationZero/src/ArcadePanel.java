import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Swing panel that shows the arcade picture. The game is drawn on a small 420x240
 * image and then scaled up by a whole number with nearest-neighbour, which keeps
 * the pixels sharp (the retro look).
 */
public class ArcadePanel extends JPanel {

    // small off-screen picture the renderer draws on
    private final BufferedImage screen =
            new BufferedImage(ArcadeRenderer.W, ArcadeRenderer.H, BufferedImage.TYPE_INT_RGB);
    private volatile Snapshot snapshot;   // latest game picture, written by the game thread

    public ArcadePanel() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(ArcadeRenderer.W * 3, ArcadeRenderer.H * 3));
        // repaint about 20 times a second so the blinking and the animation keep moving
        new Timer(50, e -> repaint()).start();
    }

    // stores the newest snapshot; the timer repaints it
    public void show(Snapshot s) {
        snapshot = s;
    }

    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Snapshot s = snapshot;
        if (s == null) return;
        // draw the game on the small image
        Graphics2D g = screen.createGraphics();
        ArcadeRenderer.render(g, s, System.currentTimeMillis());
        g.dispose();

        // biggest whole-number scale that fits the window, centred
        int scale = Math.max(1, Math.min(getWidth() / ArcadeRenderer.W, getHeight() / ArcadeRenderer.H));
        int w = ArcadeRenderer.W * scale, h = ArcadeRenderer.H * scale;
        Graphics2D out = (Graphics2D) g0;
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        out.drawImage(screen, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
    }
}
