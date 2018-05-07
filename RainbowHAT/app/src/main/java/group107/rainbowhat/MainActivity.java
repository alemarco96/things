package group107.rainbowhat;

/*Documentazione per RainbowHAT
https://github.com/pimoroni/rainbow-hat/blob/master/documentation/Technical-reference.md
https://github.com/androidthings/contrib-drivers/tree/master/rainbowhat
*/

/*Documentazione libreria SPI per Apa102
https://github.com/androidthings/contrib-drivers/tree/master/apa102
https://github.com/androidthings/contrib-drivers/blob/master/apa102/src/main/java/com/google/android/things/contrib/driver/apa102/Apa102.java
*/

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static android.content.ContentValues.TAG;
import static com.google.android.things.contrib.driver.apa102.Apa102.MAX_BRIGHTNESS;

// RainbowHat driver
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;

//apa102 LED
import com.google.android.things.contrib.driver.apa102.Apa102;


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

        /*//Capire quali sono tutti i nomi dei devices SPI
        PeripheralManager manager = PeripheralManager.getInstance();
        List<String> deviceList = manager.getSpiBusList();
        if (deviceList.isEmpty()) {
            Log.i("SPI", "Nessun bus SPI disponibile sul dispositivo.");
        } else {
            Log.i("SPI", "Lista dei devices disponibili: " + deviceList);
        }
        // rapberryPi output: "Lista dei devices disponibili: [SPI0.0, SPI0.1]"
        */

        try {
            // Display a string on the segment display.
            AlphanumericDisplay segment = RainbowHat.openDisplay();
            segment.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            segment.display("107G");
            segment.setEnabled(true);
            // Close the device when done.
            segment.close();
        } catch (IOException e) {
            Log.i("RainbowHAT", "IOException");
        }

        Apa102 mApa102;
        int[] colors = new int[]{Color.RED, Color.GREEN, Color.BLUE, Color.RED, Color.GREEN, Color.BLUE, Color.RED};

        try {

            mApa102 = new Apa102("SPI0.0", Apa102.Mode.BGR);
            mApa102.setBrightness(1);
            int[] ledOff = new int[] {0, 0, 0, 0, 0, 0, 0};

            for(int i = 0; i < 7; i++) {
                ledOff[i] = colors[i];
                mApa102.write(ledOff);
                TimeUnit.SECONDS.sleep(1);
            }

            for(int i = 6; i >= 0; i--) {
                ledOff[i] = 0;
                mApa102.write(ledOff);
                TimeUnit.SECONDS.sleep(1);
            }

            for(int i = 2; i < 5; i++) {mApa102.write(colors);

                mApa102.setBrightness(i);

                TimeUnit.SECONDS.sleep(1);

            }

            mApa102.close();
        } catch (IOException e) {
            Log.i("Apa102", "IOException nella chiusura di Apa102");
        } catch ( InterruptedException f) {
            Log.i("Apa102", "InterruptedException durante il while dei led");
        }

        try {
            // Display a string on the segment display.
            AlphanumericDisplay segment = RainbowHat.openDisplay();
            segment.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            segment.display("YEAH");
            segment.setEnabled(true);
            // Close the device when done.
            segment.close();
        } catch (IOException e) {
            Log.i("RainbowHAT", "IOException");
        }
    }
}
