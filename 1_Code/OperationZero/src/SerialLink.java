/**
 * Strategy pattern: the game talks to the bomb through this interface and does not
 * care whether the other end is the real ZigBee radio (HardwareLink) or the
 * built-in simulator (SimLink).
 */
public interface SerialLink {
    void send(String line);     // send one line to the bomb
    String readLine();          // blocks until one line arrives
    void close();
}
