package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;

// classe view
public class MainActivity extends Activity {
    DistanceController myController;
    int id;

    //TODO (1) button collegato a AllertDialog il quale mostra gli id disponibili
    //TODO (1) button che mostra misura su TextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //sceglie canale di comunicazione UART o SPI
        myController = new DistanceController("SPI0.0");

    }
}
