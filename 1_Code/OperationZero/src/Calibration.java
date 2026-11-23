/**
 * Resting values of the joystick and the accelerometer, measured at the start of
 * each attempt (CALIBRATING state). Modules compare live readings with these values.
 */
public class Calibration {
    public int jx0 = 512, jy0 = 512;   // joystick centre (10-bit ADC, 0..1023)
    public int ax0 = 337, ay0 = 337;   // accelerometer at rest
}
