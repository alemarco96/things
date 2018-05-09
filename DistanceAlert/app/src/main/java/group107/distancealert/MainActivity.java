package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

// classe view
public class MainActivity extends Activity {
    DistanceController myController;
    int id;
    boolean alert;
    int maxUserDistance;
    int measuredDistance;

    //TODO (1) button collegato a AllertDialog il quale mostra gli id disponibili
    //TODO (1) button che mostra misura su TextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        final TextView distanceView = (TextView) findViewById(R.id.distance);

        //sceglie canale di comunicazione UART o SPI
        try {
            myController = new DistanceController("SPI0.0");
        } catch (Exception e) {
            Log.e(MainActivity.class.getCanonicalName(), "Errore:\n", e);
        }
        
         alert = false;

        //TODO Bottone per la connessione ad un modulo specifico
        final Button button = findViewById(R.id.connection);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {



            }
        });

        // Visualizza dato di distanza
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                        measuredDistance = myController.getDistance(id);
                        distanceView.setText(R.string.distance + measuredDistance);

                        //controllo distanza superata
                        if (measuredDistance > maxUserDistance && !alert) {
                            //Distanza superata e allarme non ancora attivo
                            // quindi attivo allarme
                            new Thread(new Runnable() {
                                public void run() {
                                    while (alert) {
                                        // TODO allarme
                                        Toast.makeText(MainActivity.this,R.string.alarm_toast,Toast.LENGTH_LONG);
                                    }
                                }
                            }).start();
                        }

                }
            }
        }).start();


    }
        /*TODO lifecycle activity
        protected void onStart();

        protected void onRestart();

        protected void onResume();

        protected void onPause();

        protected void onStop();

        protected void onDestroy();*/
    }

}
