package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//TODO dopo crash classi continuano ad inviare dati, gestire eccezioni in modo da prevenire crash
//TODO gestire eccezione se non connesso modulo ma si clicca sullo switch
//TODO attenzione alla rimozione quando si disconnette e all'aggiunta alla riconnessione

public class MainActivity extends Activity {
    public static final String TAG = "107G";

    private static final String GPIO_PULSANTE = "BCM26";
    private static final String RPI3_UART = "MINIUART";
    private static final String RPI3_SPI = "SPI0.0";

    private Gpio pulsante;
    private DistanceController myController;
    private int id = -1;
    private int maxDistance = 2000;
    private List<RadioButton> item = new ArrayList<>();
    private boolean nextSpi = true;

    //riferimento alla TextView che mostra la distanza ricevuta
    LinearLayout idLayout;
    //Creazione di RadioGroup, ospiterà i RadioButtons
    RadioGroup listIDsGroup;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //periodo polling
        long update = 300L;

        //riferimento alla TextView che mostra la distanza ricevuta
        idLayout = findViewById(R.id.idLayout);
        //Creazione di RadioGroup, ospiterà i RadioButtons
        listIDsGroup = new RadioGroup(getApplicationContext());
        //Collegamento RadioGroup al LinearLayout ospitante
        idLayout.addView(listIDsGroup);

        //riferimento alla TextView che mostra l'ID al quale si è connessi.
        final TextView connectedToId = findViewById(R.id.connectedTo_id);
        //riferimento alla TextView che mostra la distanza ricevuta
        final TextView distanceView = findViewById(R.id.distance);

        //TextView che mostra la distanza limite
        final TextView maxDistanceView = findViewById(R.id.maxDistance);
        setDistanceText(maxDistance, maxDistanceView);
        //Bottone che aumenta la distanza limite
        Button plusMaxDistanceButton = findViewById(R.id.plusMaxDistance);
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "MainActivity -> plusMaxDistanceButton -> onClick");
                if (maxDistance <= 99000) {
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

        //Inizializzazione pulsante
        TextView infoView = findViewById(R.id.info);
        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            pulsante = manager.openGpio(GPIO_PULSANTE);
            pulsante.setActiveType(Gpio.ACTIVE_HIGH);
            pulsante.setDirection(Gpio.DIRECTION_IN);
        } catch (IOException e) {
            Log.i(TAG, "MainActivity -> onCreate: Inizializzazione pulsante non riuscita"
                    + ", errore: ", e);
            infoView.setText(R.string.physical_button_problem);
        }

        //Switch che cambia metodo di connessione o UART o SPI
        final Switch switchMethodView = findViewById(R.id.switchMethod);
        switchMethodView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (nextSpi) {
                        Log.i(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                                " nextSpi = " + nextSpi);
                        nextSpi = false;
                        myController.switchBus(RPI3_SPI);
                        switchMethodView.setChecked(true);
                        startElaboration(myController, distanceView, connectedToId);
                    } else {
                        Log.i(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                                " nextSpi = " + nextSpi);
                        nextSpi = true;
                        myController.switchBus(RPI3_UART);
                        switchMethodView.setChecked(false);
                        startElaboration(myController, distanceView, connectedToId);
                    }
                } catch (java.io.IOException | InterruptedException e) {
                    Log.e(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                            " Errore:\n", e);
                    /*Generata un'eccezione al momento della creazione dell'instanza
                    DistanceController quindi lo notifico sullo schermo utilizzato dall'utente*/
                    connectedToId.setText(R.string.noDwm);
                }

            }
        });

        //default lancia SPI
        try {
            Log.i(TAG, "MainActivity -> onCreate: default lancia SPI");
            nextSpi = false;
            myController = new DistanceController(RPI3_SPI);
            myController.startUpdate(update);
            switchMethodView.setChecked(true);
            startElaboration(myController, distanceView, connectedToId);
        } catch (java.io.IOException | InterruptedException e) {
            Log.e(TAG, "MainActivity -> onCreate Errore:\n", e);
            /*Generata un'eccezione al momento della creazione dell'instanza DistanceController
            quindi lo notifico sullo schermo utilizzato dall'utente*/
            connectedToId.setText(R.string.noDwm);
        }
    }

    protected void startElaboration(final DistanceController myController,
                                    final TextView distanceView, final TextView connectedToId) {
        Log.i(TAG, "MainActivity -> startElaboration");
            /*Connessione ai listeners generali per creare lista di IDs rilevati
            visualizzabile su schermo e completa di bottoni per la visione dei dati relativi
            allo specifico id selezionato */
        myController.addAllTagsListener(new AllTagsListener() {
            @Override
            public void onTagHasConnected(final List<DistanceController.Entry> tags) {
                Log.i(TAG,"MainActivity -> startElaboration -> " +
                        "addAllTagListener -> onTagHasConnected: tags.size() = "
                        + tags.size());
                regenerateRadioGroup(listIDsGroup, idLayout, distanceView, connectedToId);
            }

            @Override
            public void onTagHasDisconnected(final List<DistanceController.Entry> tags) {
                Log.i(TAG,"MainActivity -> startElaboration -> addAllTagListener" +
                        " -> onTagHasDisconnected: tags.size() = " + tags.size());
                regenerateRadioGroup(listIDsGroup, idLayout, distanceView, connectedToId);
            }

            @Override
            public void onTagDataAvailable(final List<DistanceController.Entry> tags) {
                Log.i(TAG, "MainActivity -> addAllTagListener" +
                        " -> onTagDataAvailable");
                regenerateRadioGroup(listIDsGroup, idLayout, distanceView, connectedToId);
            }
        });
    }

    /**
     * Rigenera la lista che gestisce gli IDs disponibili
     * @param listIDsGroup RadioGroup da aggiornare
     * @param idLayout LinearLayout contenente il RadioGroup da aggiornare
     * @param distanceView La TextView dove viene visualizzato il dato di distanza rilevato
     * @param connectedToId La TextView dove viene visualizzato l'ID del modulo a distanza
     *                      "distanceView"
     */
    private void regenerateRadioGroup(final RadioGroup listIDsGroup, final LinearLayout idLayout,
                                      final TextView distanceView, final TextView connectedToId) {
        Log.i(TAG, "MainActivity -> regenerateRadioGroup");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "MainActivity -> regenerateRadioGroup: running thread");
                //ricezione IDs connessi
                List<Integer> ids = myController.getTagIDs();
                //creazione di Array contentente i RadioGroups
                final RadioButton[] item = new RadioButton[ids.size()];
                //pulizia RadioGroup ospitante i RadioButtons
                listIDsGroup.removeAllViews();
                //popolazione dell'array di RadioButtons
                for (int i = 0; i < ids.size(); i++) {
                    Log.i(TAG, "MainActivity -> regenerateRadioGroup: ciclo for, i = " + i);
                    item[i] = new RadioButton(getApplicationContext());
                    final int singleId = ids.get(i);
                    final String idText = Integer.toHexString(singleId);
                    item[i].setText(idText);

                    //Click specifico di ogni singolo RadioButton
                    item[i].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG, "MainActivity -> regenerateRadioGroup:"
                                    + " onClick " + idText);
                            id = singleId;
                            connectToSpecificListener(distanceView, connectedToId);
                        }
                    });
                    //Controllo se il bottone era stato premuto in precedenza
                    if (id != -1 && id == singleId) {
                        Log.i(TAG, "MainActivity -> regenerateRadioGroup:" +
                                " ciclo for, i = " + i + ", RadioButton toggled: (id = " + id +
                                ") == (singleid = " + singleId + ")");
                        item[i].toggle();
                    }
                    //Aggiunta del bottone in fondo alla lista
                    listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                //pulizia scorso idLayout, ospitante il RadioGroup
                idLayout.removeAllViews();
                //pubblicazione RadioGroup su idlayout
                idLayout.addView(listIDsGroup);
            }
        });

    }

    /**
     * Connessione ad un id selezionato il quale invia il dato della distanza
     * @param distanceView La TextView dove viene visualizzato il dato di distanza rilevato
     * @param connectedToId La TextView dove viene visualizzato l'ID del modulo a distanza
     *                      "distanceView"
     */
    private void connectToSpecificListener(final TextView distanceView,
                                           final TextView connectedToId) {
        Log.i(TAG, "MainActivity -> connectToSpecificListener: connectedToId = "
                + connectedToId);
        //visualizzazione a schermo dell'ID al quale si è connessi
        String idText = Integer.toHexString(id);
        connectedToId.setText(idText);
        //collegamento a listeners di un solo tag id
        myController.addTagListener(id, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG, "MainActivity -> connectToSpecificListener -> addTagListener" +
                        " -> onTagHasConnected: Connesso a " + Integer.toHexString(id));
                setDistanceText(tagDistance, distanceView);
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(TAG, "MainActivity -> connectToSpecificListener -> addTagListener" +
                        " -> onTagHasDisconnected: disconnesso id = " + Integer.toHexString(id));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(R.string.noConnection);
                        distanceAlarm();
                    }
                });
            }

            @Override
            public void onTagDataAvailable(final int tagDistance) {
                Log.i(TAG, "MainActivity -> connectToSpecificListener -> addTagListener" +
                        " -> onTagDataAvailable: id = " + Integer.toHexString(id) +
                        ", tagDistance = " + tagDistance);
                if (tagDistance > maxDistance) {
                    distanceAlarm();//todo migliorare implementazione?
                }
                //Lista invariata
            }
        });
    }

    /**
     * Aggiorna la View con la distanza ricevuta
     * @param distance distanza ricevuta
     * @param distanceView TextView dove aggiornare la distanza
     */
    private void setDistanceText (final int distance, final TextView distanceView) {
        Log.i(TAG, "MainActivity -> setDistanceText: id = " + Integer.toHexString(id)
                + ", tagDistance = " + distance);
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

    /*TODO controlla questo metodo*/
    private void distanceAlarm() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final DistanceAlarm myAlarm = new DistanceAlarm();
                    myAlarm.start();
                    pulsante.setEdgeTriggerType(Gpio.EDGE_RISING);

                    pulsante.registerGpioCallback(
                            /*todo runtime exception can't create handler inside thread that has not called Looper.prepare() */
                            new GpioCallback() {
                                @Override
                                public boolean onGpioEdge(Gpio gpio) {
                                    try {
                                        myAlarm.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return false;
                                }
                            }
                    );
                } catch (IOException e) {
                    e.printStackTrace();//TODO
                }
            }
        });
    }

    @Override
    public void onPause() {
        Log.i(TAG, "MainActivity -> onPause");
        //passaggio di stato
        super.onPause();

        if (myController != null) {
            Log.i(TAG, "MainActivity -> onPause -> chiusura controller");
            //chiusura controller
            myController.close();
        }
    }
}
