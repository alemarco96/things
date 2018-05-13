package group107.distancealert;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
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
    private boolean alert;
    private int maxUserDistance;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //bottone per tornare alla connesione

        final Button connectTo = findViewById(R.id.connection);
        connectTo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Click eseguito in PostActivity");
                Intent toIDActivity = new Intent(getApplicationContext(), IDActivity.class);
                startActivity(toIDActivity);
            }
        });


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
        myController.startUpdate(1000L);

        //collegamento a listeners di un solo tag id
        myController.addTagListener(IDActivity.id, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG, "Connessione a " + IDActivity.id + " avvenuta.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String newText =    getString(R.string.distance) +
                                            " " + (tagDistance/1000) +
                                            "." + (tagDistance%1000);
                        distanceView.setText(newText);
                    }
                });
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(TAG, IDActivity.id + " disconnesso.");
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
                Log.i(TAG, "Distanza ricevuta da " + IDActivity.id + " = " + tagDistance);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String newText =    getString(R.string.distance) +
                                            " " + (tagDistance/1000) +
                                            "." + (tagDistance%1000);
                        distanceView.setText(newText);
                    }
                });
            }
        });

        /*
        //Test funzionamento con metodi richiedenti informazioni a tutti i TAGs
        myController.addAllTagsListener(new AllTagsListener() {
            @Override
            public void onTagHasConnected(final List<DistanceController.Entry> tags) {
                final int idReceived = tags.get(0).tagID;
                final int distanceReceived = tags.get(0).tagDistance;

                Log.i(TAG, "Connessione a " + id + " avvenuta.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Distanza ricevuta da " + idReceived + " = " + distanceReceived);
                        distanceView.setText(getString(R.string.distance) +
                                " " + (distanceReceived / 1000) + "." + (distanceReceived % 1000));
                    }
                });
            }

            @Override
            public void onTagHasDisconnected(final List<DistanceController.Entry> tags) {
                int idReceived = tags.get(0).tagID;
                final int distanceReceived = tags.get(0).tagDistance;
                Log.i(TAG, idReceived + " disconnesso.");
                distanceView.setText(R.string.noConnection);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(R.string.noConnection);
                    }
                });
            }

            @Override
            public void onTagDataAvailable(final List<DistanceController.Entry> tags) {
                int idReceived = tags.get(0).tagID;
                final int distanceReceived = tags.get(0).tagDistance;
                Log.i(TAG, "Distanza ricevuta da " + idReceived + " = " + distanceReceived);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(getString(R.string.distance) +
                                " " + (distanceReceived/1000) + "." + (distanceReceived%1000));
                    }
                });
            }
        });
        */
    }

    @Override
    public void onPause() {
        //chiusura controller
        myController.close();

        //passaggio di stato
        super.onPause();
    }


    /*
    TODO pulsante per tornare alla schermata di iniziale in modo programmatico (utile per configurare connessione e altro)
    Intent startMain = new Intent( ... );
    ...
    finish();
    Poi come tornare alla app senza lanciarla da adb? Dal menu general -> restart device
    valutare...
     */
}
