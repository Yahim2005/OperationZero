import java.awt.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.*;

/**
 * Strategy pattern: a fake bomb on screen, so the game can be developed and
 * demonstrated without the hardware. Sliders stand for the sensors and buttons
 * stand for the keypad, the RFID cards and the DEFUSE button. It sends the same
 * text lines the real firmware sends (see SensorEvent).
 */
public class SimLink implements SerialLink {

    // lines "received from the bomb"; readLine() takes from this queue
    private final BlockingQueue<String> incoming = new LinkedBlockingQueue<String>();
    private volatile boolean streaming = false;   // true after the game sends "G"
    private final JSlider enc = new JSlider(-40, 40, 0);
    private final JSlider jx = new JSlider(0, 1023, 512);
    private final JSlider jy = new JSlider(0, 1023, 512);
    private final JSlider ax = new JSlider(250, 430, 337);
    private final JSlider ay = new JSlider(250, 430, 337);
    private final JSlider cm = new JSlider(0, 100, 0);

    public SimLink(final String[] cardIds) {
        SwingUtilities.invokeLater(new Runnable() { public void run() { buildWindow(cardIds); } });
        // sampler thread: like the firmware, one sensor line every 100 ms while streaming
        Thread sampler = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                    if (streaming) {
                        incoming.offer("S," + enc.getValue() + "," + jx.getValue() + "," + jy.getValue()
                                + "," + ax.getValue() + "," + ay.getValue() + "," + cm.getValue());
                    }
                }
            }
        });
        sampler.setDaemon(true);
        sampler.start();
    }

    // builds the simulator window: sliders in the centre, keypad and buttons on the right
    private void buildWindow(String[] cardIds) {
        JFrame f = new JFrame("Bomb simulator (fake hardware)");
        f.setLayout(new BorderLayout());
        JPanel sliders = new JPanel(new GridLayout(0, 1));
        addSlider(sliders, enc, "Encoder");
        addSlider(sliders, jx, "Joystick X");
        addSlider(sliders, jy, "Joystick Y");
        addSlider(sliders, ax, "Accelerometer X");
        addSlider(sliders, ay, "Accelerometer Y");
        addSlider(sliders, cm, "Distance cm");
        f.add(sliders, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());
        // 4x3 keypad: each button sends "K,<key>"
        JPanel pad = new JPanel(new GridLayout(4, 3));
        for (String k : new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"}) {
            final String key = k;
            JButton b = new JButton(k);
            b.addActionListener(e -> incoming.offer("K," + key));
            pad.add(b);
        }
        buttons.add(pad, BorderLayout.CENTER);
        // one button per RFID card: sends "R,<tag>"
        JPanel cards = new JPanel(new GridLayout(1, 3));
        for (int i = 0; i < 3; i++) {
            final String id = cardIds[i];
            JButton b = new JButton(BombConfiguration.CARD_NAMES[i] + " card");
            b.addActionListener(e -> incoming.offer("R," + id));
            cards.add(b);
        }
        // the DEFUSE push button sends "B"
        JButton defuse = new JButton("DEFUSE");
        defuse.addActionListener(e -> incoming.offer("B"));
        JPanel south = new JPanel(new GridLayout(2, 1));
        south.add(cards);
        south.add(defuse);
        buttons.add(south, BorderLayout.SOUTH);
        f.add(buttons, BorderLayout.EAST);
        f.pack();
        f.setLocation(850, 0);
        f.setVisible(true);
    }

    private void addSlider(JPanel p, JSlider s, String title) {
        s.setBorder(BorderFactory.createTitledBorder(title));
        s.setPaintLabels(false);
        p.add(s);
    }

    // commands from the game, same as the firmware: G = start streaming, X = stop, Z = calibrate
    public void send(String line) {
        if (line.equals("G")) {
            streaming = true;
            incoming.offer("OK,G");
        } else if (line.equals("X")) {
            streaming = false;
        } else if (line.equals("Z")) {
            SwingUtilities.invokeLater(new Runnable() { public void run() { enc.setValue(0); } });
        }
    }

    /** Blocks until the simulator has a line for the game. */
    public String readLine() {
        try {
            return incoming.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    public void close() { }
}
