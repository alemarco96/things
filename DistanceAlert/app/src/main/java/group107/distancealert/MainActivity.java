package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

// classe view
public class MainActivity extends Activity {
    DistanceController myController;

    //TODO (1) button collegato a AllertDialog il quale mostra gli id disponibili
    //TODO (1) button che mostra misura su TextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //sceglie canale di comunicazione UART o SPI
        try {
            myController = new DistanceController("SPI0.0");
        } catch (Exception e) {
            Log.e(MainActivity.class.getCanonicalName(), "Errore:\n", e);
        }
    }
}
