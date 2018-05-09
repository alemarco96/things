package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

//Nulla in questa classe è definitivo
//Sono prove al momento.

// classe view
public class MainActivity extends Activity {
    DistanceController myController;
    int id;
    boolean alert;
    int maxUserDistance;
    int measuredDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView distanceView = (TextView) findViewById(R.id.distance);

        //sceglie canale di comunicazione UART o SPI
        final DistanceController myController = new DistanceController("SPI0.0");
        alert = false;

        /*TODO Bottone per la connessione ad un modulo specifico
        final Button button = findViewById(R.id.connection);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {



            }
        });*/

        // Visualizza dato di distanza
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
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
                                        Toast.makeText(MainActivity.this, R.string.alarm_toast, Toast.LENGTH_LONG).show();
                                    }
                                }
                            }).start();
                        }

                    } catch (IOException e) {
                        Log.i("getDistance(id)", "Errore nella ricezione della distanza");
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
