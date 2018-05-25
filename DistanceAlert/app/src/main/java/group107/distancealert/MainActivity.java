package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.util.List;

// classe view
public class MainActivity extends Activity {
    public static final String TAG = "107G";
    private DistanceController myController;
    private int id = -1;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //riferimento alla TextView che mostra la distanza ricevuta
        final LinearLayout idLayout = findViewById(R.id.idLayout);

        //Creazione di RadioGroup, ospiterà i RadioButtons
        final RadioGroup listIDsGroup = new RadioGroup(getApplicationContext());

        final TextView connectedToId = findViewById(R.id.connectedTo_id);

        //riferimento alla TextView che mostra la distanza ricevuta
        final TextView distanceView = findViewById(R.id.distance);

        try {
            //sceglie canale di comunicazione UART o SPI
            myController = new DistanceController("SPI0.0");
            //Start polling
            myController.startUpdate(1000L);
            //Connessione ai listener generali
            myController.addAllTagsListener(new AllTagsListener() {
                @Override
                public void onTagHasConnected(List<DistanceController.Entry> tags) {
                    Log.i(TAG,"MainActivity -> addAllTagListener -> onTagHasConnected");
                    regenerateRagioGroup(listIDsGroup, idLayout, distanceView);
                }

                @Override
                public void onTagHasDisconnected(List<DistanceController.Entry> tags) {
                    Log.i(TAG,"MainActivity -> addAllTagListener -> onTagHasDisconnected");
                    regenerateRagioGroup(listIDsGroup, idLayout, distanceView);
                }

                @Override
                public void onTagDataAvailable(List<DistanceController.Entry> tags) {
                    Log.i(TAG,"MainActivity -> addAllTagListener -> onTagDataAvailable");
                }
            });
        } catch (java.io.IOException | InterruptedException e) {
            Log.e(TAG, "Errore:\n", e);
            connectedToId.setText(R.string.noDwm);
        }
    }

    /**
     * Rigenera la lista che gestisce gli IDs disponibili
     * @param listIDsGroup RadioGroup da aggiornare
     * @param idLayout LinearLayout contenente il RadioGroup da aggiornare
     */

    private void regenerateRagioGroup(final RadioGroup listIDsGroup, final LinearLayout idLayout, final TextView distanceView){
        Log.i(TAG, "MainActivity -> regenerateRadioGroup");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG,"MainActivity -> regenerateRadioGroup: running thread");
                List<Integer> ids = myController.getTagIDs();
                final RadioButton[] item = new RadioButton[ids.size()];
                for(int i = 0; i < ids.size(); i++) {
                    Log.i(TAG, "MainActivity -> regenerateRadioGroup: ciclo for, i = " + i);
                    item[i] = new RadioButton(getApplicationContext());
                    final int singleId = ids.get(i);
                    String idText = Integer.toString(singleId);
                    item[i].setText(idText);

                    //Click specifico di ogni singolo RadioButton
                    item[i].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG, "MainActivity -> regenerateRadioGroup:"
                                    + " onClick " + singleId);
                            id = singleId;
                            connectToSpecificListener(distanceView);
                        }
                    });
                    //Aggiunta del bottone in fondo alla lista
                    listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                //pubblicazione RadioGroup sul layout
                idLayout.addView(listIDsGroup);
            }
        });
    }

    private void connectToSpecificListener(final TextView distanceView) {
        //collegamento a listeners di un solo tag id
        myController.addTagListener(id, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG, "Connessione a " + id + " avvenuta.");
                setDistanceText(tagDistance, distanceView);
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
                setDistanceText(tagDistance, distanceView);
            }
        });
    }

    public void setDistanceText (final int tagDistance, final TextView distanceView) {
        Log.i(TAG, "Distanza ricevuta da " + id + " = " + tagDistance);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String newText =    (tagDistance/1000) +
                        "." + (tagDistance%1000);
                distanceView.setText(newText);
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
