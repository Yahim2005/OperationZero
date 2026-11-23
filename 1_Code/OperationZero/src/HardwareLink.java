/**
 * Strategy pattern: the real link to the bomb. The serial port here is the
 * XBee Explorer plugged into the laptop, so every line goes over ZigBee.
 */
public class HardwareLink implements SerialLink {

    private final SerialPortHandle port;

    public HardwareLink(String portName) {
        port = new SerialPortHandle(portName);   // Lab 8 serial helper class (9600 baud)
    }

    public void send(String line) {
        port.printLine(line);
    }

    /** Blocks until a full line arrives from the bomb. */
    public String readLine() {
        return port.readLine();
    }

    public void close() {
        try {
            port.sp.closePort();
        } catch (Exception e) {
            // ignore: the port may already be closed
        }
    }
}
