package group107.distancealert;

import android.os.Build;

// TODO
public class BoardDefaults {
    private static final String DEVICE_RPI3 = "rpi3";

    /**
     * Return the GPIO pin that the LED is connected on.
     * ---> BoardDefaults.getGPIOForLED()
     */
    public static String getGPIOForLED() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "BCM6";
            default:
                throw new IllegalStateException("Unsupported device");
        }
    }

    /**
     * Return the GPIO pin that the Button is connected on.
     */
    public static String getGPIOForButton() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "BCM21";
            default:
                throw new IllegalStateException("Unsupported device");
        }
    }
}
