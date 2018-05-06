package group107.rainbowhat;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.List;

import static android.content.ContentValues.TAG;

// RainbowHat driver
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Capire quali sono tutti i nomi dei devices SPI
        PeripheralManager manager = PeripheralManager.getInstance();
        List<String> deviceList = manager.getSpiBusList();
        if (deviceList.isEmpty()) {
            Log.i(TAG, "Nessun bus SPI disponibile sul dispositivo.");
        } else {
            Log.i(TAG, "Lista dei devices disponibili: " + deviceList);
        }
        // rapberryPi output: "Lista dei devices disponibili: [SPI0.0, SPI0.1]"

        /*try {
            // Light up the Red LED.
            Gpio led = RainbowHat.openLedRed();
            led.setValue(true);
            // Close the device when done.
            led.close();
        } catch (IOException e) {
            Log.i(TAG, "IOException");
        }*/

        try {
            // Display a string on the segment display.
            AlphanumericDisplay segment = RainbowHat.openDisplay();
            segment.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            segment.display("107G");
            segment.setEnabled(true);
// Close the device when done.
            segment.close();
        } catch (IOException e) {
            Log.i(TAG, "IOException");
        }
    }
}
