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

public class MainActivity extends Activity {
    //Stringhe utili per TAGs della MainActivity
    private final String MainActivityTAG = "MainActivity";

    /**
     * Stringhe costanti usate per identificare le periferiche
     */
    private static final String PWM_BUZZER = "PWM1";
    private static final String GPIO_LED = "BCM16";
    private static final String GPIO_PULSANTE = "BCM26";
    private static final String RPI3_UART = "MINIUART";
    private static final String RPI3_SPI = "SPI0.0";

    //true: SPI  false: UART
    private static final boolean STARTING_BUS = false;

    //Dichiarazione Elementi grafici condivisi da più metodi all'interno di MainActivity
    private LinearLayout idLayout;
    private RadioGroup listIDsGroup;
    private TextView connectedToId;
    private TextView distanceView;
    private TextView maxDistanceView;
    private Switch switchMethodView;
    private TagListener idTagListener;

    //Oggetti utili all'activity
    private Gpio pulsante;
    private DistanceController myController;
    private DistanceAlarm myAlarm;
    private int id = -1;
    private int maxDistance = 2000;
    final private List<RadioButton> item = new ArrayList<>();
    private boolean nextSpi = STARTING_BUS;
    private boolean alarmMuted = false;
    private boolean alarmStatus = false;

    /*
      oggetto necessario per evitare race-conditions nell'accesso al distanceController
      da vari thread
     */
    private final Object controllerLock = new Object();

    //periodo, in millisecondi, di aggiornamento del distanceController
    private static final long UPDATE_PERIOD = 300L;
    //ritardo, in millisecondi, usato nella gestione della comunicazione
    private static final long BUS_DELAY = 100L;

    final private GpioCallback pulsanteCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                Log.d(MainActivityTAG, "distanceAlarm -> " +
                        "GpioCallback");
                myAlarm.stop();
                pulsante.unregisterGpioCallback(pulsanteCallback);
                alarmMuted = true;
            } catch (IOException e) {
                Log.e(MainActivityTAG, "distanceAlarm" +
                        " -> Errore Pulsante:", e);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(MainActivityTAG, "onCreate");
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

        //Collegamento RadioGroup al LinearLayout ospitante
        idLayout.addView(listIDsGroup);

        setDistanceText(maxDistance, maxDistanceView);
        //Bottone che aumenta la distanza limite
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(MainActivityTAG, "plusMaxDistanceButton -> onClick");
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
                Log.v(MainActivityTAG, "minusMaxDistanceButton -> onClick");
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
            Log.e(MainActivityTAG, "onCreate:" +
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
                Log.e(MainActivityTAG, "onCreate: " +
                        "Inizializzazione allarme non riuscita, Errore: ", e);
            }
        }

        //Switch che cambia metodo di connessione o UART o SPI
        switchMethodView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeController();
                setupCommunication(!nextSpi);
            }
        });

        //inizializzazione comunicazione
        setupCommunication(nextSpi);
    }

    private void closeController()
    {
        synchronized (controllerLock) {
            boolean flag = myController == null;

            //Se istanza di DistanceController già creata allora deve essere chiusa
            if (flag) {
                //nessuna istanza di DistanceController creata
                Log.v(MainActivityTAG, "closeController: myController == null");
                return;
            }
            Log.v(MainActivityTAG, "closeController: chiusura del controller.");
            myController.stopUpdate();
            myController.close();
            myController = null;
        }

        //attesa per favorire la chiusura di DistanceController
        SleepHelper.sleepMillis(BUS_DELAY);
    }

    private void setupCommunication(boolean isNextSpi)
    {
        nextSpi = isNextSpi;
        switchMethodView.setChecked(!nextSpi);
        //TODO: avvia la cosa corretta?
        Log.d(MainActivityTAG,"setupCommunication: now " + (nextSpi ? "SPI" : "UART"));

        try {
            synchronized (controllerLock) {
                myController = new DistanceController(nextSpi ? RPI3_SPI : RPI3_UART, UPDATE_PERIOD);
            }
            startElaboration();
            if (id != -1) {
                //id già selezionato in precedenza
                connectToSpecificListener(id);
            }
        } catch (IOException e) {
            Log.e(MainActivityTAG, "setupCommunication -> Errore nel setup della comunicazione:\n", e);
            /*Generata un'eccezione al momento della creazione dell'instanza DistanceController
            quindi lo notifico sullo schermo utilizzato dall'utente*/
            Toast t = Toast.makeText(getApplicationContext(), R.string.noDwm, Toast.LENGTH_LONG);
            t.show();

            synchronized (controllerLock) {
                boolean flag = myController == null;

                if (flag)
                    return;

                myController.close();
                myController = null;
            }

            //attesa per favorire la chiusura di DistanceController
            SleepHelper.sleepMillis(BUS_DELAY);
        }
    }

    /**
     * Comincia l'elaborazione dei dati ricevuti
     */
    private void startElaboration() {
        Log.d(MainActivityTAG, "startElaboration");
            /*Connessione ai listeners generali per creare lista di IDs rilevati
            visualizzabile su schermo e completa di bottoni per la visione dei dati relativi
            allo specifico id selezionato */
        synchronized (controllerLock) {
            myController.addAllTagsListener(new AllTagsListener() {
                @Override
                public void onTagHasConnected(final List<DistanceController.Entry> tags) {
                    if(tags == null || tags.size() <= 0)
                        return;

                    Log.i(MainActivityTAG, "startElaboration -> " +
                            "addAllTagListener -> onTagHasConnected: item.size() = " + item.size()
                            + ", tags.size() = " + tags.size());
                    regenerateRadioGroup();
                }

                @Override
                public void onTagHasDisconnected(final List<DistanceController.Entry> tags) {
                    if(tags == null || tags.size() <= 0)
                        return;

                    Log.i(MainActivityTAG, "startElaboration -> addAllTagListener" +
                            " -> onTagHasDisconnected: item.size() = " + item.size()
                            + ", tags.size() = " + tags.size());
                    regenerateRadioGroup();
                }

                @Override
                public void onTagDataAvailable(final List<DistanceController.Entry> tags) {
                    if(tags == null || tags.size() <= 0)
                        return;

                    Log.i(MainActivityTAG, "startElaboration -> addAllTagListener" +
                            " -> onTagDataAvailable: item.size() = " + item.size()
                            + ", tags.size() = " + tags.size());
                    //se diversi sicuramente c'è da aggiornare la lista degli ids
                    if (item.size() != tags.size()) {
                        //sicuramente c'è differenza tra la lista mostrata e quella reale
                        regenerateRadioGroup();
                        return;
                    }
                    //Controllo siano presenti i tags giusti
                    for (int i = 0; i < tags.size(); i++) {
                        for (int j = 0; j < item.size(); j++) {
                            String itemText = (String) item.get(j).getText();
                            if (!(Integer.toHexString(tags.get(i).tagID).equals(itemText))) {
                                //trovato id rilevato non presente nella lista, quindi la aggiorno
                                regenerateRadioGroup();
                                return;
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Rigenera la lista che gestisce gli IDs disponibili
     */
    private void regenerateRadioGroup() {
        Log.d(MainActivityTAG, "regenerateRadioGroup");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v(MainActivityTAG, "regenerateRadioGroup: running");
                //ricezione IDs connessi
                List<Integer> ids;
                synchronized (controllerLock) {
                    ids = myController.getTagIDs();
                }
                //pulizia RadioGroup ospitante i RadioButtons
                listIDsGroup.removeAllViews();
                //pulizia RadioButtons
                item.clear();
                //popolazione dell'array di RadioButtons
                for (int i = 0; i < ids.size(); i++) {
                    Log.v(MainActivityTAG, "regenerateRadioGroup: " +
                            "ciclo for, i = " + i);
                    item.add(new RadioButton(getApplicationContext()));
                    final int singleId = ids.get(i);
                    final String idText = Integer.toHexString(singleId);
                    item.get(i).setText(idText);

                    //Click specifico di ogni singolo RadioButton
                    item.get(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(MainActivityTAG, "regenerateRadioGroup:"
                                    + " onClick " + idText);
                            if(id != -1) {
                                //esiste già un tagListener, quindi è da rimuovere
                                Log.v(MainActivityTAG, "regenerateRadioGroup -> " +
                                        "(id == " + id + ") !=-1");
                                synchronized (controllerLock) {
                                    myController.removeTagListener(idTagListener);
                                }
                                idTagListener = null;
                            }
                            connectToSpecificListener(singleId);
                        }
                    });
                    //Controllo se il bottone era stato premuto in precedenza
                    if (id != -1 && id == singleId) {
                        Log.v(MainActivityTAG, "regenerateRadioGroup:" +
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
        Log.d(MainActivityTAG, "connectToSpecificListener: connectedToId = "
                + connectedToId);
        id = singleId;
        //visualizzazione a schermo dell'ID al quale si è connessi
        String idText = Integer.toHexString(id);
        connectedToId.setText(idText);
        //collegamento a listeners di un solo tag id
        idTagListener = new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagHasConnected: Connesso a " +
                        Integer.toHexString(id));
                setDistanceText(tagDistance, distanceView);
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagHasDisconnected: disconnesso id = " +
                        Integer.toHexString(id));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(R.string.noConnection);
                        if ((!alarmStatus) && (!alarmMuted)) {
                            distanceAlarm();
                            alarmStatus = true;
                        }
                    }
                });
            }

            @Override
            public void onTagDataAvailable(final int tagDistance) {
                Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagDataAvailable: id = " + Integer.toHexString(id) +
                        ", tagDistance = " + tagDistance);
                if ((tagDistance > maxDistance) && (!alarmMuted) && (!alarmStatus)) {
                    Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                            "addTagListener -> onTagDataAvailable: " +
                            "(tagDistance == " + tagDistance +
                            ") > (maxDistance == " + maxDistance + "), " +
                            "(alarmMuted == false), (alarmStatus == false)");
                    alarmStatus = true;
                    distanceAlarm();
                }
                if ((tagDistance <= maxDistance) && (alarmStatus)) {
                    try {
                        Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                                "addTagListener -> onTagDataAvailable: " +
                                "(tagDistance == " + tagDistance +
                                ") <= (maxDistance == " + maxDistance + "), " +
                                "(alarmStatus == true)");
                        alarmMuted = false;
                        alarmStatus = false;
                        myAlarm.stop();
                        pulsante.unregisterGpioCallback(pulsanteCallback);
                    } catch (IOException e) {
                        Log.e(MainActivityTAG, "Errore nella chiusura dell'allarme", e);
                    }
                }
                setDistanceText(tagDistance, distanceView);
            }
        };
        myController.addTagListener(id, idTagListener);
    }

    /**
     * Aggiorna la View con la distanza ricevuta
     * @param distance     distanza ricevuta
     * @param distanceView TextView dove aggiornare la distanza
     */
    private void setDistanceText(final int distance, final TextView distanceView) {
        Log.v(MainActivityTAG, "setDistanceText: id = " + Integer.toHexString(id)
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
                Log.d(MainActivityTAG, "distanceAlarm: running");
                try {
                    if (myAlarm == null || pulsante == null) {
                        return;
                    }

                    myAlarm.start();

                    pulsante.registerGpioCallback(pulsanteCallback);

                } catch (IOException e) {
                    Log.e(MainActivityTAG, "distanceAlarm -> Errore:", e);
                }
            }
        });
    }

    @Override
    public void onPause() {
        Log.v(MainActivityTAG, "onPause");
        //passaggio di stato
        super.onPause();

        //chiusura periferiche
        if (pulsante != null) {
            try {
                pulsante.close();
            } catch (IOException e) {
                Log.e(MainActivityTAG, "onPause -> Errore pulsante:", e);
            }
            pulsante = null;
        }

        if (myAlarm != null) {
            try {
                myAlarm.close();
            } catch (IOException e) {
                Log.e(MainActivityTAG, "onPause -> Errore allarme:", e);
            }
            myAlarm = null;

        }

        boolean flag;

        synchronized (controllerLock) {
            flag = myController == null;

            if (flag)
                return;
            Log.d(MainActivityTAG, "onPause -> chiusura controller");
            //chiusura controller
            myController.close();
            myController = null;
        }

        //attesa per favorire la chiusura di DistanceController
        SleepHelper.sleepMillis(BUS_DELAY);
    }
}