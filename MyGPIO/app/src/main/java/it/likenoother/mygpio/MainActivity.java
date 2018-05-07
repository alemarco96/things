package it.likenoother.mygpio;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String GPIO_LED = "BCM16";
    private static final String GPIO_PULSANTE = "BCM26";

    private Gpio led;
    private Gpio pulsante;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final TextView mTextView;
        final Button mButton;

        mTextView = findViewById(R.id.mytextview);
        mButton = findViewById(R.id.mybutton);

        try {
            PeripheralManager manager = PeripheralManager.getInstance();
            led = manager.openGpio(GPIO_LED);
            pulsante = manager.openGpio(GPIO_PULSANTE);
        } catch (IOException e) {
            Log.e("gne","male");
        }

        try {
            pulsante.setDirection(Gpio.DIRECTION_IN);
            pulsante.setActiveType(Gpio.ACTIVE_HIGH);
            led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            led.setActiveType(Gpio.ACTIVE_HIGH);
        } catch (IOException e) {
            Log.e("gne","malino");
        }

        mButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mTextView.setText("working");
                try {
                    if (pulsante.getValue()) {
                        led.setValue(true);
                    } else {
                        led.setValue(false);
                    }
                } catch (IOException e) {
                    Log.e("gne","poteva andare peggio");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (led != null) {
            try {
                led.close();
                led = null;
            } catch (IOException e) {
                Log.e("gne","pazienza");
            }
        }
        if (pulsante != null) {
            try {
                pulsante.close();
                pulsante = null;
            } catch (IOException e) {
                Log.e("gne","pazienza");
            }
        }
    }
}
