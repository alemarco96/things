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
import android.widget.Toast;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//TODO dopo crash classi continuano ad inviare dati, gestire eccezioni in modo da prevenire crash
//TODO risolvere switch UART - SPI

public class MainActivity extends Activity {
    //Stringa utile per il TAG dei logs
    public static final String TAG = "107G";

    /**
     * Stringhe costanti usate per identificare le periferiche
     */
    private static final String PWM_BUZZER = "PWM1";
    private static final String GPIO_LED = "BCM16";
    private static final String GPIO_PULSANTE = "BCM26";
    private static final String RPI3_UART = "MINIUART";
    private static final String RPI3_SPI = "SPI0.0";

    private Gpio pulsante;
    private DistanceController myController;
    private DistanceAlarm myAlarm;
    private int id = -1;
    private int maxDistance = 2000;
    final private List<RadioButton> item = new ArrayList<>();
    private boolean nextSpi = true;

    //Inizializzazione Elementi grafici
    private final LinearLayout idLayout = findViewById(R.id.idLayout);
    private final RadioGroup listIDsGroup = new RadioGroup(getApplicationContext());
    private final TextView connectedToId = findViewById(R.id.connectedTo_id);
    private final TextView distanceView = findViewById(R.id.distance);
    private final TextView maxDistanceView = findViewById(R.id.maxDistance);


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //periodo polling
        final long update = 300L;
        
        //Collegamento RadioGroup al LinearLayout ospitante
        idLayout.addView(listIDsGroup);

        setDistanceText(maxDistance, maxDistanceView);
        //Bottone che aumenta la distanza limite
        Button plusMaxDistanceButton = findViewById(R.id.plusMaxDistance);
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "MainActivity -> plusMaxDistanceButton -> onClick");
                if (maxDistance < 5000) {
                    maxDistance += 200;
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
                if (maxDistance > 200) {
                    maxDistance -= 200;
                    setDistanceText(maxDistance, maxDistanceView);
                }
            }
        });

        //Inizializzazione pulsante
        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            pulsante = manager.openGpio(GPIO_PULSANTE);
            pulsante.setActiveType(Gpio.ACTIVE_HIGH);
            pulsante.setDirection(Gpio.DIRECTION_IN);
            pulsante.setEdgeTriggerType(Gpio.EDGE_RISING);
        } catch (IOException e) {
            Log.i(TAG, "MainActivity -> onCreate: Inizializzazione pulsante non riuscita"
                    + ", errore: ", e);
            Toast t = Toast.makeText(getApplicationContext(), R.string.physical_button_problem,
                    Toast.LENGTH_LONG);
            t.show();
        }

        // SOLO SE IL PULSANTE FUNZIONA, istanzio l'allarme
        if (pulsante != null) {
            try {
                myAlarm = new DistanceAlarm(GPIO_LED, PWM_BUZZER);
            } catch (IOException e) {
                Log.i(TAG, "MainActivity -> onCreate: Inizializzazione allarme non riuscita"
                        + ", errore: ", e);
            }
        }

        //Switch che cambia metodo di connessione o UART o SPI
        final Switch switchMethodView = findViewById(R.id.switchMethod);
        switchMethodView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {

                    /*//Utilizzo di switchBus

                    if (nextSpi) {
                        Log.i(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                                " nextSpi = " + nextSpi);
                        if(myController == null){
                            myController = new DistanceController(RPI3_SPI);
                            myController.startUpdate(update);
                            nextSpi = false;
                            return;
                        }
                        nextSpi = false;
                        myController.switchBus(RPI3_SPI);
                        switchMethodView.setChecked(true);
                        startElaboration(myController);
                    } else {
                        Log.i(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                                " nextSpi = " + nextSpi);
                        if(myController == null){
                            myController = new DistanceController(RPI3_UART);
                            myController.startUpdate(update);
                            nextSpi = true;
                            return;
                        }
                        nextSpi = true;
                        myController.switchBus(RPI3_UART);
                        switchMethodView.setChecked(false);
                        startElaboration(myController);
                    }
                     */
                    if (myController != null) {
                        Log.i(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                                "myController == null");
                        myController.stopUpdate();
                        myController.close();
                    } else {
                        Log.i(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                                "myController == null");
                    }
                    if (nextSpi) {
                        nextSpi = false;
                        myController = new DistanceController(RPI3_SPI);
                        myController.startUpdate(update);
                        switchMethodView.setChecked(true);
                        startElaboration(myController);
                    } else {
                        nextSpi = true;
                        myController = new DistanceController(RPI3_UART);
                        myController.startUpdate(update);
                        switchMethodView.setChecked(false);
                        startElaboration(myController);
                    }
                } catch (java.io.IOException | InterruptedException e) {
                    Log.e(TAG, "MainActivity -> onCreate -> onClick switchMethodView:" +
                            " Errore:\n", e);
                    /*Generata un'eccezione al momento della creazione dell'instanza
                    DistanceController quindi lo notifico sullo schermo utilizzato dall'utente*/
                    Toast t = Toast.makeText(getApplicationContext(), R.string.noDwm,
                            Toast.LENGTH_LONG);
                    t.show();
                    if (myController != null) {
                        myController.close();
                        myController = null;
                    }
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
            startElaboration(myController);
        } catch (java.io.IOException | InterruptedException e) {
            Log.e(TAG, "MainActivity -> onCreate Errore:\n", e);
            /*Generata un'eccezione al momento della creazione dell'instanza DistanceController
            quindi lo notifico sullo schermo utilizzato dall'utente*/
            Toast t = Toast.makeText(getApplicationContext(), R.string.noDwm, Toast.LENGTH_LONG);
            t.show();
            if (myController != null) {
                myController.close();
                myController = null;
            }
        }
    }

    /**
     * Comincia l'elaborazione dei dati ricevuti
     * @param myController istanza di DistanceController
     */
    private void startElaboration(final DistanceController myController) {
        Log.i(TAG, "MainActivity -> startElaboration");
            /*Connessione ai listeners generali per creare lista di IDs rilevati
            visualizzabile su schermo e completa di bottoni per la visione dei dati relativi
            allo specifico id selezionato */
        myController.addAllTagsListener(new AllTagsListener() {
            @Override
            public void onTagHasConnected(final List<DistanceController.Entry> tags) {
                Log.i(TAG, "MainActivity -> startElaboration -> " +
                        "addAllTagListener -> onTagHasConnected: tags.size() = "
                        + tags.size());
                Log.v(TAG, "item.size() = " + item.size() + ", tags.size() = " + tags.size());
                regenerateRadioGroup();
            }

            @Override
            public void onTagHasDisconnected(final List<DistanceController.Entry> tags) {
                Log.i(TAG, "MainActivity -> startElaboration -> addAllTagListener" +
                        " -> onTagHasDisconnected: tags.size() = " + tags.size());
                regenerateRadioGroup();
            }

            @Override
            public void onTagDataAvailable(final List<DistanceController.Entry> tags) {
                Log.i(TAG, "MainActivity -> addAllTagListener" +
                        " -> onTagDataAvailable: Lista invariata");
                //se diversi sicuramente c'è da riaggiornare
                Log.v(TAG, "item.size() = " + item.size() + ", tags.size() = " + tags.size());
                if (item.size() != tags.size()) {
                    regenerateRadioGroup();
                    return;
                }
                //Controllo siano presenti i tags giusti
                for (int i = 0; i < tags.size(); i++) {
                    for (int j = 0; j < item.size(); j++) {
                        String itemText = (String) item.get(j).getText();
                        if (!(Integer.toHexString(tags.get(i).tagID)
                                .equals(itemText))) {
                            regenerateRadioGroup();
                            return;
                        }
                    }
                }
            }
        });
    }

    /**
     * Rigenera la lista che gestisce gli IDs disponibili
     */
    private void regenerateRadioGroup() {
        Log.i(TAG, "MainActivity -> regenerateRadioGroup");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "MainActivity -> regenerateRadioGroup: running thread");
                //ricezione IDs connessi
                List<Integer> ids = myController.getTagIDs();
                //pulizia RadioGroup ospitante i RadioButtons
                listIDsGroup.removeAllViews();
                //pulizia RadioButtons
                item.clear();
                //popolazione dell'array di RadioButtons
                for (int i = 0; i < ids.size(); i++) {
                    Log.i(TAG, "MainActivity -> regenerateRadioGroup: ciclo for, i = " + i);
                    item.add(new RadioButton(getApplicationContext()));
                    final int singleId = ids.get(i);
                    final String idText = Integer.toHexString(singleId);
                    item.get(i).setText(idText);

                    //Click specifico di ogni singolo RadioButton
                    item.get(i).setOnClickListener(new View.OnClickListener() {
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
                        item.get(i).toggle();
                    }
                    //Aggiunta del bottone in fondo alla lista
                    listIDsGroup.addView(item.get(i), -1,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
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
                    distanceAlarm();
                }
                setDistanceText(tagDistance, distanceView);
            }
        });
    }

    /**
     * Aggiorna la View con la distanza ricevuta
     *
     * @param distance     distanza ricevuta
     * @param distanceView TextView dove aggiornare la distanza
     */
    private void setDistanceText(final int distance, final TextView distanceView) {
        Log.i(TAG, "MainActivity -> setDistanceText: id = " + Integer.toHexString(id)
                + ", tagDistance = " + distance);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String decimal = Integer.toString(distance % 1000);
                if (decimal.length() < 2) {
                    //un solo numero dopo la virgola, aggiungo uno zero a sinistra
                    decimal += "0";
                } else {
                    //o due o più numeri dopo la virgola, ne considero solo due
                    decimal = decimal.substring(0, 2);
                }
                String newText = (distance / 1000) +
                        "." + decimal + " m";
                distanceView.setText(newText);
            }
        });
    }

    private void distanceAlarm() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (myAlarm == null || pulsante == null) {
                        return;
                    }

                    myAlarm.start();

                    pulsante.registerGpioCallback(
                            new GpioCallback() {
                                @Override
                                public boolean onGpioEdge(Gpio gpio) {
                                    try {
                                        myAlarm.stop();
                                    } catch (IOException e) {
                                        Log.e(TAG, "MainActivity -> distanceAlarm" +
                                                " -> Errore Pulsante:", e);
                                    }
                                    return false;
                                }
                            }
                    );

                } catch (IOException e) {
                    Log.e(TAG, "MainActivity -> distanceAlarm -> Errore:", e);
                }
            }
        });
    }

    @Override
    public void onPause() {
        Log.i(TAG, "MainActivity -> onPause");
        //passaggio di stato
        super.onPause();

        if (pulsante != null) {
            try {
                pulsante.close();
            } catch (IOException e) {
                Log.e(TAG, "MainActivity -> onPause -> Errore pulsante:", e);
            }
            pulsante = null;
        }

        if (myAlarm != null) {
            try {
                myAlarm.close();
            } catch (IOException e) {
                Log.e(TAG, "MainActivity -> onPause -> Errore allarme:", e);
            }
            myAlarm = null;

        }

        if (myController != null) {
            Log.i(TAG, "MainActivity -> onPause -> chiusura controller");
            //chiusura controller
            myController.close();
            myController = null;
        }
    }
}