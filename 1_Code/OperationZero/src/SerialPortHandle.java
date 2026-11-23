import jssc.SerialPort;
import jssc.SerialPortException;

/**
 * Thin wrapper around a jSSC serial port (Lab 8 helper class).
 * Opens the port at 9600 baud, 8 data bits, 1 stop bit, no parity, which matches the
 * firmware and both XBee radios. Used for lines of text ending in a newline.
 */
class SerialPortHandle {

	SerialPort sp;
	String path;

	public SerialPortHandle(String path) {
		super();
		this.sp = new SerialPort(path);

		this.path = path;
		try {
			sp.openPort();
			sp.setParams(9600, 8, 1, 0);

			// Flush garbage data on initial open
			while (sp.getInputBufferBytesCount() > 0) {
				sp.readBytes();
			}

		} catch (SerialPortException e) {
			// the port could not be opened (wrong name, or another program holds it)
			e.printStackTrace();
		} // Open serial port

	}

	/** Reads bytes one at a time until a full non-empty line has arrived. */
	public String readLine() {
		StringBuilder string = new StringBuilder();
		while (true) {
			try {
				byte[] buffer = sp.readBytes(1);
				if (buffer == null || buffer.length == 0)
					continue;

				char c = (char) buffer[0];

				if (c == '\n' || c == '\r') {
					if (string.length() == 0) {
						continue; // skip empty lines
					} else {
						break; // return non-empty line
					}
				}

				string.append(c);

			} catch (SerialPortException e) {
				e.printStackTrace();
				break;
			}
		}
		return string.toString().trim();
	}

	/** Sends the text followed by a newline, which is how the firmware splits commands. */
	public void printLine(String s) {
		byte byteArray[] = s.getBytes();
		try {
			sp.writeBytes(byteArray);
			sp.writeByte((byte) '\n');
		} catch (SerialPortException e1) {
			// the write failed (cable or radio lost)
			e1.printStackTrace();
		}
	}

}
