import java.awt.*;
import javax.swing.*;
import javax.swing.border.LineBorder;

/**
 * The game window. Observer pattern: it implements GameObserver, so GameController
 * calls update() with a new Snapshot whenever the game changes.
 * Layout: a name field and START button on top, the arcade screen below.
 */
public class GameView extends JFrame implements GameObserver {

    private final GameController controller;       // used only to request a start
    private final JTextField nameField = new JTextField(14);
    private final RetroButton startButton = new RetroButton("START");
    private final ArcadePanel screen = new ArcadePanel();   // draws the 2-bit arcade picture

    public GameView(GameController controller) {
        super("Operation Zero");
        this.controller = controller;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.BLACK);
        setLayout(new BorderLayout());

        // top bar: label, name field and START button, in the arcade colours
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        top.setBackground(Color.BLACK);
        JLabel label = new JLabel("PLAYER NAME:");
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        nameField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        nameField.setBackground(Color.BLACK);
        nameField.setForeground(ArcadeRenderer.YELLOW);
        nameField.setCaretColor(ArcadeRenderer.YELLOW);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ArcadeRenderer.BLUE, 2), BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        top.add(label);
        top.add(nameField);
        top.add(startButton);
        add(top, BorderLayout.NORTH);
        add(screen, BorderLayout.CENTER);

        // START button or Enter in the name field begins an attempt
        startButton.addActionListener(e -> controller.requestStart(nameField.getText()));
        nameField.addActionListener(e -> {
            if (startButton.isEnabled()) controller.requestStart(nameField.getText());
        });
        pack();
        setLocation(0, 0);
    }

    // may be called from the game thread, so hop onto the Swing thread before touching the screen
    public void update(final Snapshot s) {
        SwingUtilities.invokeLater(new Runnable() { public void run() { apply(s); } });
    }

    private void apply(Snapshot s) {
        screen.show(s);
        // the name field and START are only usable between attempts
        boolean idle = s.state == GameState.READY || s.state == GameState.DEFUSED
                || s.state == GameState.FAILED || s.state == GameState.INTERRUPTED;
        startButton.setEnabled(idle);
        nameField.setEnabled(idle);
    }

    // a button drawn in the same pixel style as the screen
    private static class RetroButton extends JButton {
        RetroButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(150, 40));
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(isEnabled() ? ArcadeRenderer.YELLOW : ArcadeRenderer.BLUE);
            g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            g.drawRect(3, 3, getWidth() - 7, getHeight() - 7);
            BitmapFont.drawCentered(g, getText(), getWidth() / 2, getHeight() / 2 - 7, 2,
                    isEnabled() ? ArcadeRenderer.YELLOW : ArcadeRenderer.BLUE);
        }
    }
}
