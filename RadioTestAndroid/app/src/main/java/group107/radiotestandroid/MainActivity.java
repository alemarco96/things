package group107.radiotestandroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    public static final String TAG = "107G";
    //private DistanceController myController;
    private int id = -1;
    private int maxDistance = 2000;
    private boolean nextSpi = true;


    private List<RadioButton> item = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //riferimento alla TextView che mostra la distanza ricevuta
        final LinearLayout idLayout = findViewById(R.id.idLayout);
        //Creazione di RadioGroup, ospiterà i RadioButtons
        final RadioGroup listIDsGroup = new RadioGroup(getApplicationContext());

        idLayout.addView(listIDsGroup);
        //riferimento alla TextView che mostra l'ID al quale si è connessi.
        final TextView connectedToId = findViewById(R.id.connectedTo_id);
        //riferimento alla TextView che mostra la distanza ricevuta
        final TextView distanceView = findViewById(R.id.distance);
        int test = 2752;
        setDistanceText(test, distanceView);

        //TextView che mostra la distanza limite
        final TextView maxDistanceView = findViewById(R.id.maxDistance);
        setDistanceText(maxDistance, maxDistanceView);


        final Switch switchMethodView = findViewById(R.id.switchMethod);
        switchMethodView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nextSpi) {
                    switchMethodView.setChecked(true);
                    nextSpi = false;
                    Log.i(TAG, "switchMethodView.onClick: nextSpi = SPI");
                } else {
                    switchMethodView.setChecked(false);
                    nextSpi = true;
                    Log.i(TAG, "switchMethodView.onClick: UART");
                }
            }
        });

        switchMethodView.setChecked(true);
        nextSpi = false;
        Log.i(TAG, "nextSpi default: SPI");

        //Bottone che aumenta la distanza limite
        Button plusMaxDistanceButton = findViewById(R.id.plusMaxDistance);
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "MainActivity -> plusMaxDistanceButton -> onClick");
                if (maxDistance <= 90500) {
                    maxDistance += 500;
                    setDistanceText(maxDistance, maxDistanceView);
                }
            }
        });
        //Bottone che diminuisce la distanza limite
        Button minusMaxDistanceButton = findViewById(R.id.minusMaxDistance);
        minusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "MainActivity -> minusMaxDistanceButton -> onClick");
                if (maxDistance >= 500) {
                    maxDistance -= 500;
                    setDistanceText(maxDistance, maxDistanceView);
                }
            }
        });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < 5; i++) {
                    item.add(i, new RadioButton(getApplicationContext()));
                    item.get(i).setText(Integer.toHexString(i));
                    final int finalId = i;
                    item.get(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG, "click id = "+ finalId);
                            id = finalId;
                            connectedToId.setText(Integer.toHexString(id));
                        }
                    });
                    listIDsGroup.addView(item.get(i));
                }
                for(int i = 5; i < 11; i++) {
                    item.add(new RadioButton(getApplicationContext()));
                    item.get(i).setText(Integer.toHexString(i));
                    final int finalId = i;
                    item.get(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG, "click id = "+ finalId);
                            id = finalId;
                            connectedToId.setText(Integer.toHexString(id));
                        }
                    });
                    listIDsGroup.addView(item.get(i));
                }
                listIDsGroup.removeView(item.get(6));
                item.remove(6);

            }
        });
    }

    /**
     * Aggiorna la View con la distanza ricevuta
     * @param distance distanza ricevuta
     * @param distanceView TextView dove aggiornare la distanza
     */
    private void setDistanceText (final int distance, final TextView distanceView) {
        Log.i(TAG, "MainActivity -> setDistanceText: id = " + id + ", tagDistance = "
                + distance);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String decimal = Integer.toString(distance%1000);
                if(decimal.length() < 2) {
                    //un solo numero dopo la virgola, aggiungo uno zero a sinistra
                    decimal += "0";
                } else {
                    //o due o più numeri dopo la virgola, ne considero solo due
                    decimal = decimal.substring(0,2);
                }
                String newText =    (distance/1000) +
                        "." + decimal + " m";
                distanceView.setText(newText);
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "MainActivity -> onDestroy");
        //passaggio di stato
        super.onDestroy();
    }
}
