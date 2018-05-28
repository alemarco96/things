package group107.distancealert;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;

import java.io.Closeable;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class DistanceAlarm implements Closeable {
    private static final String PWM_BUZZER = "PWM1";
    private static final String GPIO_LED = "BCM16";

    private Gpio led;
    private Pwm buzzer;
    private Timer timer;

    private boolean status;
    private int toneIndex;
    private final int[] tone = {
            400, 400, 400,
            440, 440, 440,
            500, 500, 500,
            400, 440, 500,
            400, 440, 500,
            400, 440, 500
    };

    public DistanceAlarm() throws IOException {
        PeripheralManager manager = PeripheralManager.getInstance();
        led = manager.openGpio(GPIO_LED);
        led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        led.setActiveType(Gpio.ACTIVE_HIGH);

        buzzer = manager.openPwm(PWM_BUZZER);
        buzzer.setPwmFrequencyHz(440);
        buzzer.setPwmDutyCycle(50);
        buzzer.setEnabled(false);

        status = false;
    }

    public boolean getStatus() {
        return status;
    }

    public void start() throws IOException {
        if (status) {
            return;
        }

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                toneIndex = (toneIndex + 1) % tone.length;
                try {
                    buzzer.setPwmFrequencyHz(tone[toneIndex]);
                    led.setValue(!led.getValue());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 400);

        buzzer.setEnabled(true);
        status = true;
    }

    public void stop() throws IOException {
        timer.cancel();
        timer = null;
        buzzer.setEnabled(false);
        status = false;
    }

    public void close() throws IOException {
        if (status) {
            stop();
        }
        if (led != null) {
            led.close();
            led = null;
        }
        if (buzzer != null) {
            buzzer.close();
            buzzer = null;
        }
    }
}
