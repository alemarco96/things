package group107.distancealert;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

// classe view
public class MainActivity extends Activity {
    public static final String TAG = "107G";
    private DistanceController myController;
    private int id = 53774;
    private boolean alert;
    private int maxUserDistance;
    private int measuredDistance;

    //TODO (1) button collegato a AllertDialog il quale mostra gli id disponibili
    //TODO (1) button che mostra misura su TextView

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //riferimento alla TextView che mostra la distanza ricevuta
        final TextView distanceView = findViewById(R.id.distance);

        //sceglie canale di comunicazione UART o SPI
        try {
            myController = new DistanceController("SPI0.0");
        } catch (Exception e) {
            Log.e(TAG, "Errore:\n", e);
        }

        //allarme attiva?
        alert = false;

        //Start polling
        myController.startUpdate(1000L); //*10^(-6)s

        //collegamento a listeners di un solo tag id
        myController.addTagListener(id, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG, "Connessione a " + id + " avvenuta.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(getString(R.string.distance) +
                                " " + (tagDistance/1000) + "." + (tagDistance%1000));
                    }
                });
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(TAG, id + " disconnesso.");
                distanceView.setText(R.string.noConnection);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(R.string.noConnection);
                    }
                });
            }

            @Override
            public void onTagDataAvailable(final int tagDistance) {
                Log.i(TAG, "Distanza ricevuta da " + id + " = " + tagDistance);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(getString(R.string.distance) +
                                " " + (tagDistance/1000) + "." + (tagDistance%1000));
                    }
                });
            }
        });



    }

    @Override
    public void onPause() {
        //chiusura controller
        myController.close();

        //passaggio di stato
        super.onPause();
    }

}
