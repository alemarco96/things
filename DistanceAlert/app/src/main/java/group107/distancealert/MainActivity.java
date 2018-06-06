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
//TODO ogni volta che si esegue startElaboration() vengono creati listeners, bisogna anche toglierli quando non servono più?
//TODO ogni volta che si esegue regenerateRadioGroup() vengono creati listeners, bisogna anche toglierli quando non servono più?
//TODO Gestire analisi codice "aggiungere a dizionario parole come UART, distancealert, GPIO, MINIUART, switchbus, singleid, idlayout" ?

public class MainActivity extends Activity {
    /**
     * Stringa utile per il TAG dei logs del pacchetto group107.distancealert
     */
    public static final String TAG = "107G";
    private final String MainActivityTAG = " -> MainActivity";

    /**
     * Stringhe costanti usate per identificare le periferiche
     */
    private static final String PWM_BUZZER = "PWM1";
    private static final String GPIO_LED = "BCM16";
    private static final String GPIO_PULSANTE = "BCM26";
    private static final String RPI3_UART = "MINIUART";
    private static final String RPI3_SPI = "SPI0.0";

    //Dichiarazione Elementi grafici condivisi da più metodi all'interno di MainActivity
    private LinearLayout idLayout;
    private RadioGroup listIDsGroup;
    private TextView connectedToId;
    private TextView distanceView;
    private TextView maxDistanceView;
    private Switch switchMethodView;

    //Oggetti utili all'activity
    private Gpio pulsante;
    private DistanceController myController;
    private DistanceAlarm myAlarm;
    private int id = -1;
    private int maxDistance = 2000;
    final private List<RadioButton> item = new ArrayList<>();
    private boolean nextSpi = true;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Inizializzazione Elementi grafici
        idLayout = findViewById(R.id.idLayout);
        listIDsGroup = new RadioGroup(getApplicationContext());
        connectedToId = findViewById(R.id.connectedTo_id);
        distanceView = findViewById(R.id.distance);
        maxDistanceView = findViewById(R.id.maxDistance);
        switchMethodView = findViewById(R.id.switchMethod);

        //dichiarazione e inizializzazione Elementi Grafici utilizzati solo in onCreate
        Button plusMaxDistanceButton = findViewById(R.id.plusMaxDistance);
        Button minusMaxDistanceButton = findViewById(R.id.minusMaxDistance);


        //periodo polling
        final long update = 300L;
        
        //Collegamento RadioGroup al LinearLayout ospitante
        idLayout.addView(listIDsGroup);

        setDistanceText(maxDistance, maxDistanceView);
        //Bottone che aumenta la distanza limite
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG + MainActivityTAG, "plusMaxDistanceButton -> onClick");
                if (maxDistance < 5000) {
                    maxDistance += 200;
                    setDistanceText(maxDistance, maxDistanceView);
                }
            }
        });
        //Bottone che diminuisce la distanza limite
        minusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG + MainActivityTAG, "minusMaxDistanceButton -> onClick");
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
            Log.i(TAG + MainActivityTAG, "onCreate:" +
                    "Inizializzazione pulsante non riuscita, Errore: ", e);
            Toast t = Toast.makeText(getApplicationContext(), R.string.physical_button_problem,
                    Toast.LENGTH_LONG);
            t.show();
        }

        // SOLO SE IL PULSANTE FUNZIONA, creo istanza dell'allarme
        if (pulsante != null) {
            try {
                myAlarm = new DistanceAlarm(GPIO_LED, PWM_BUZZER);
            } catch (IOException e) {
                Log.i(TAG + MainActivityTAG, "onCreate: " +
                        "Inizializzazione allarme non riuscita, Errore: ", e);
            }
        }

        //TODO Decidere se implementare un metodo switchbus in DistanceController o risolvere in MainActivity
        //Switch che cambia metodo di connessione o UART o SPI
        switchMethodView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {

                    /*//Utilizzo di switchBus

                    if (nextSpi) {
                        Log.i(TAG + MainActivityTAG, "onCreate -> onClick switchMethodView:" +
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
                        Log.i(TAG + MainActivityTAG, "onCreate -> onClick switchMethodView:" +
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
                    
                    //Risoluzione in MainActivity di "switchBus"
                    if (myController != null) {
                        Log.i(TAG + MainActivityTAG, "onCreate -> " +
                                "onClick switchMethodView: myController != null");
                        myController.stopUpdate();
                        myController.close();
                    } else {
                        Log.i(TAG + MainActivityTAG, "onCreate -> " +
                                "onClick switchMethodView: myController == null");
                    }
                    if (nextSpi) {
                        //Chiudo sessione precedente e avvio SPI
                        Log.i(TAG + MainActivityTAG, "onCreate -> " +
                                "onClick switchMethodView: nextSpi = true");
                        nextSpi = false; //prossimo click a switch si deve avviare UART
                        myController = new DistanceController(RPI3_SPI);
                        myController.startUpdate(update);
                        switchMethodView.setChecked(true);
                        startElaboration();
                        if (id != -1) {
                            //id già selezionato in precedenza
                            connectToSpecificListener(id);
                        }
                    } else {
                        //Chiudo sessione precedente e avvio UART
                        Log.i(TAG + MainActivityTAG, "onCreate -> " +
                                "onClick switchMethodView: nextSpi = false");
                        nextSpi = true; //prossimo click a switch si deve avviare SPI
                        myController = new DistanceController(RPI3_UART);
                        myController.startUpdate(update);
                        switchMethodView.setChecked(false);
                        startElaboration();
                        if (id != -1) {
                            //id già selezionato in precedenza
                            connectToSpecificListener(id);
                        }
                    }
                } catch (java.io.IOException | InterruptedException e) {
                    Log.e(TAG + MainActivityTAG, "onCreate -> onClick switchMethodView:" +
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
            Log.i(TAG + MainActivityTAG, "onCreate: default lancia SPI");
            nextSpi = false;
            myController = new DistanceController(RPI3_SPI);
            myController.startUpdate(update);
            switchMethodView.setChecked(true);
            startElaboration();
        } catch (java.io.IOException | InterruptedException e) {
            Log.e(TAG + MainActivityTAG, "onCreate Errore:\n", e);
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
     */
    private void startElaboration() {
        Log.i(TAG + MainActivityTAG, "startElaboration");
            /*Connessione ai listeners generali per creare lista di IDs rilevati
            visualizzabile su schermo e completa di bottoni per la visione dei dati relativi
            allo specifico id selezionato */
        myController.addAllTagsListener(new AllTagsListener() {
            @Override
            public void onTagHasConnected(final List<DistanceController.Entry> tags) {
                Log.i(TAG + MainActivityTAG, "startElaboration -> " +
                        "addAllTagListener -> onTagHasConnected: tags.size() = "
                        + tags.size());
                Log.v(TAG, "item.size() = " + item.size() + ", tags.size() = " + tags.size());
                regenerateRadioGroup();
            }

            @Override
            public void onTagHasDisconnected(final List<DistanceController.Entry> tags) {
                Log.i(TAG + MainActivityTAG, "startElaboration -> addAllTagListener" +
                        " -> onTagHasDisconnected: tags.size() = " + tags.size());
                regenerateRadioGroup();
            }

            @Override
            public void onTagDataAvailable(final List<DistanceController.Entry> tags) {
                Log.i(TAG + MainActivityTAG, "addAllTagListener" +
                        " -> onTagDataAvailable: Lista invariata");
                //se diversi sicuramente c'è da aggiornare la lista degli ids
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
        Log.i(TAG + MainActivityTAG, "regenerateRadioGroup");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG + MainActivityTAG, "regenerateRadioGroup: running");
                //ricezione IDs connessi
                List<Integer> ids = myController.getTagIDs();
                //pulizia RadioGroup ospitante i RadioButtons
                listIDsGroup.removeAllViews();
                //pulizia RadioButtons
                item.clear();
                //popolazione dell'array di RadioButtons
                for (int i = 0; i < ids.size(); i++) {
                    Log.i(TAG + MainActivityTAG, "regenerateRadioGroup: " + 
                            "ciclo for, i = " + i);
                    item.add(new RadioButton(getApplicationContext()));
                    final int singleId = ids.get(i);
                    final String idText = Integer.toHexString(singleId);
                    item.get(i).setText(idText);

                    //Click specifico di ogni singolo RadioButton
                    item.get(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG + MainActivityTAG, "regenerateRadioGroup:"
                                    + " onClick " + idText);
                            connectToSpecificListener(singleId);
                        }
                    });
                    //Controllo se il bottone era stato premuto in precedenza
                    if (id != -1 && id == singleId) {
                        Log.i(TAG + MainActivityTAG, "regenerateRadioGroup:" +
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
     * @param singleId id specifico al quale ci si vuole connettere
     */
    private void connectToSpecificListener(int singleId) {
        Log.i(TAG + MainActivityTAG, "connectToSpecificListener: connectedToId = "
                + connectedToId);
        id = singleId;
        //visualizzazione a schermo dell'ID al quale si è connessi
        String idText = Integer.toHexString(id);
        connectedToId.setText(idText);
        //collegamento a listeners di un solo tag id
        myController.addTagListener(id, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG + MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagHasConnected: Connesso a " +
                        Integer.toHexString(id));
                setDistanceText(tagDistance, distanceView);
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(TAG + MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagHasDisconnected: disconnesso id = " +
                        Integer.toHexString(id));
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
                Log.i(TAG + MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagDataAvailable: id = " + Integer.toHexString(id) +
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
        Log.i(TAG + MainActivityTAG, "setDistanceText: id = " + Integer.toHexString(id)
                + ", tagDistance = " + distance);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String decimal = Integer.toString(distance % 1000);
                if (decimal.length() < 2) {
                    //un solo numero dopo la virgola, aggiungo uno zero a destra
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

    /**
     * Lancia l'allarme spegnibile dal bottone fisico
     */
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
                                        Log.e(TAG + MainActivityTAG, "distanceAlarm" +
                                                " -> Errore Pulsante:", e);
                                    }
                                    return false;
                                }
                            }
                    );

                } catch (IOException e) {
                    Log.e(TAG + MainActivityTAG, "distanceAlarm -> Errore:", e);
                }
            }
        });
    }

    //TODO decidere se cambiare e farlo diventare onDestroy come sul repo ufficiale git
    @Override
    public void onPause() {
        Log.i(TAG + MainActivityTAG, "onPause");
        //passaggio di stato
        super.onPause();

        //chiusura periferiche
        if (pulsante != null) {
            try {
                pulsante.close();
            } catch (IOException e) {
                Log.e(TAG + MainActivityTAG, "onPause -> Errore pulsante:", e);
            }
            pulsante = null;
        }

        if (myAlarm != null) {
            try {
                myAlarm.close();
            } catch (IOException e) {
                Log.e(TAG + MainActivityTAG, "onPause -> Errore allarme:", e);
            }
            myAlarm = null;

        }

        if (myController != null) {
            Log.i(TAG + MainActivityTAG, "onPause -> chiusura controller");
            //chiusura controller
            myController.close();
            myController = null;
        }
    }
}