package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity principale del progetto.
 */
public class MainActivity extends Activity {

    /**
     * Stringa utile per log della MainActivity
     */
    private final String MainActivityTAG = "MainActivity";

    /**
     * Stringhe costanti usate per identificare le periferiche
     */
    private static final String PWM_BUZZER = "PWM1";
    private static final String GPIO_LED = "BCM16";
    private static final String GPIO_PULSANTE = "BCM26";
    private static final String RPI3_UART = "MINIUART";
    private static final String RPI3_SPI = "SPI0.0";

    /**
     * Bus iniziale: true: SPI  false: UART
     */
    private static final boolean STARTING_BUS = true;

    /**
     * Costanti relative alla distanza di allerta
     */
    private static final int STEP = 200;
    private static final int MAX_DISTANCE = 5000;
    private static final int MIN_DISTANCE = 200;
    private static final int DEFAULT_DISTANCE = 2000;

    /**
     * Dichiarazione elementi grafici condivisi da più metodi all'interno di MainActivity
     */
    private LinearLayout idLayout;
    private RadioGroup listIDsGroup;
    private TextView connectedToId;
    private TextView distanceView;
    private TextView maxDistanceView;
    private Switch switchMethodView;
    private TagListener idTagListener;

    /**
     * Oggetti utili all'activity
     */
    private Gpio pulsante;
    private DistanceController myController;
    private DistanceAlarm myAlarm;
    private int id = -1;
    private int maxDistance = DEFAULT_DISTANCE;
    final private List<RadioButton> item = new ArrayList<>();
    private boolean nextSpi = STARTING_BUS;
    private boolean alarmMuted = false;
    private boolean alarmStatus = false;

    /**
     * Oggetto necessario per evitare race-conditions nell'accesso al distanceController
     * da vari thread
     */
    private final Object controllerLock = new Object();

    /**
     * Periodo, in millisecondi, di aggiornamento del distanceController
     */
    private static final long UPDATE_PERIOD = 300L;

    /**
     * Ritardo, in millisecondi, usato nella gestione della comunicazione
     */
    private static final long BUS_DELAY = 100L;

    /**
     * Callback del pulsante fisico
     */
    final private GpioCallback pulsanteCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                // Tasto fisico premuto: silenzia allarme
                Log.d(MainActivityTAG, "pulsanteCallback -> onGpioEdge: spengo allarme");
                if(myAlarm != null) {
                    myAlarm.stop();
                }
                alarmMuted = true;
            } catch (IOException e) {
                Log.e(MainActivityTAG, "pulsanteCallback -> onGpioEdge -> " +
                        "Errore Pulsante:", e);
            }

            // Callback viene eseguita solo una volta e poi si deregistra
            return false;
        }
    };

    /**
     * Grazie a questo metodo si inizializza l'interfaccia utente, viene creata un'istanza
     * di DistanceController con il quale è possibile avviare le operazioni di comunicazione con
     * DriverDwm e quindi con il modulo fisico. Inoltre, viene preparato il sistema d'allarme.
     *
     * @param savedInstanceState Stato dell'istanza.
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(MainActivityTAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inizializzazione elementi grafici
        idLayout = findViewById(R.id.idLayout);
        listIDsGroup = new RadioGroup(getApplicationContext());
        connectedToId = findViewById(R.id.connectedTo_id);
        distanceView = findViewById(R.id.distance);
        maxDistanceView = findViewById(R.id.maxDistance);
        switchMethodView = findViewById(R.id.switchMethod);

        // Dichiarazione e inizializzazione elementi grafici utilizzati localmente
        Button plusMaxDistanceButton = findViewById(R.id.plusMaxDistance);
        Button minusMaxDistanceButton = findViewById(R.id.minusMaxDistance);

        // Collegamento del RadioGroup al LinearLayout ospitante
        idLayout.addView(listIDsGroup);

        // Visualizza distanza di allerta di default
        setDistanceText(maxDistance, maxDistanceView);

        // Bottone che aumenta la distanza di allerta
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(MainActivityTAG, "plusMaxDistanceButton -> onClick: " +
                        "aumento distanza di allerta");
                if (maxDistance < MAX_DISTANCE) {
                    maxDistance += STEP;
                    setDistanceText(maxDistance, maxDistanceView);
                }
            }
        });

        // Bottone che diminuisce la distanza di allerta
        minusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(MainActivityTAG, "minusMaxDistanceButton -> onClick: " +
                        "diminuisco distanza di allerta");
                if (maxDistance > MIN_DISTANCE) {
                    maxDistance -= STEP;
                    setDistanceText(maxDistance, maxDistanceView);
                }
            }
        });

        // Inizializzazione pulsante fisico che silenzia l'allarme
        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            /*
             GPIO relativo al pulsante impostato come input attivo a livello alto e con la
             possibilità di generare delle callback quando passa da livello basso a livello alto.
             */
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

        try {
            myAlarm = new DistanceAlarm(GPIO_LED, PWM_BUZZER);
        } catch (IOException e) {
            Log.e(MainActivityTAG, "onCreate: " +
                    "Inizializzazione allarme non riuscita, Errore: ", e);
            myAlarm = null;
        }

        // Switch che cambia metodo di connessione o UART o SPI
        switchMethodView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(MainActivityTAG,"onCreate -> switchMethodView -> onClick");
                closeController();
                // Attesa per favorire la chiusura di DistanceController
                SystemClock.sleep(BUS_DELAY);
                // Imposta comunicazione sull'altro bus
                setupCommunication(!nextSpi);
            }
        });

        // Inizializzazione comunicazione
        setupCommunication(nextSpi);
    }

    /**
     * Imposta la comunicazione con il modulo tramite il bus specificato dal parametro nextSpi
     *
     * @param isNextSpi Se TRUE: SPI, altrimenti: UART
     */
    private void setupCommunication(boolean isNextSpi)
    {
        // Scelta se inizializzare o SPI (isNextSpi == true) o UART (isNextSpi == false)
        nextSpi = isNextSpi;
        switchMethodView.setChecked(nextSpi);
        Log.d(MainActivityTAG,"setupCommunication: now " + (nextSpi ? "SPI" : "UART"));

        try {
            synchronized (controllerLock) {
                myController = new DistanceController(nextSpi ? RPI3_SPI : RPI3_UART,UPDATE_PERIOD);
            }

            // Inizia elaborazione dei dati ricevuti
            startElaboration();

            // Se id già selezionato in precedenza: selezione automatica
            if (id != -1) {
                connectToSpecificListener(id);
            }
        } catch (Throwable e) {
            Log.e(MainActivityTAG, "setupCommunication -> " +
                    "Errore nel setup della comunicazione:\n", e);
            /*
             Generata un'eccezione al momento della creazione dell'instanza DistanceController
             quindi lo notifica sullo schermo tramite toast
             */
            Toast t = Toast.makeText(getApplicationContext(),
                    R.string.noDwm, Toast.LENGTH_LONG);
            t.show();
            // Chiusura controller
            closeController();
        }
    }

    /**
     * Comincia l'elaborazione dei dati ricevuti: tramite l'AllTagListener rigenera il RadioGroup
     * quando opportuno.
     */
    private void startElaboration() {
        Log.d(MainActivityTAG, "startElaboration");
        /*
         Connessione all'AllTagListener per creare lista di IDs rilevati visualizzata
         su schermo, completa di bottoni per la selezione del tag specifico.
         */
        synchronized (controllerLock) {
            myController.addAllTagsListener(new AllTagsListener() {
                @Override
                public void onTagHasConnected(final List<DistanceController.Entry> tags) {
                    // Controllo di sicurezza
                    if(tags == null || tags.size() <= 0)
                        return;

                    // Ulteriore/i tag connesso/i: rigenerare RadioGroup
                    Log.i(MainActivityTAG, "startElaboration -> " +
                            "addAllTagListener -> onTagHasConnected: item.size() = " + item.size()
                            + ", tags.size() = " + tags.size());
                    regenerateRadioGroup();
                }

                @Override
                public void onTagHasDisconnected(final List<DistanceController.Entry> tags) {
                    // Controllo di sicurezza
                    if(tags == null || tags.size() <= 0)
                        return;

                    // Ulteriore/i tag disconnesso/i: rigenerare RadioGroup
                    Log.i(MainActivityTAG, "startElaboration -> addAllTagListener" +
                            " -> onTagHasDisconnected: item.size() = " + item.size()
                            + ", tags.size() = " + tags.size());
                    regenerateRadioGroup();
                }

                @Override
                public void onTagDataAvailable(final List<DistanceController.Entry> tags) {
                    // Controllo di sicurezza
                    if(tags == null || tags.size() <= 0)
                        return;

                    // Ricevuti dati aggiornati dal/dai tag connessi
                    Log.i(MainActivityTAG, "startElaboration -> addAllTagListener" +
                            " -> onTagDataAvailable: item.size() = " + item.size()
                            + ", tags.size() = " + tags.size());

                    // Se diversi, sicuramente c'è da aggiornare la lista degli ids
                    if (item.size() != tags.size()) {
                        regenerateRadioGroup();
                        return;
                    }

                    // Controllo siano presenti i tag giusti
                    for (int i = 0; i < tags.size(); i++) {
                        for (int j = 0; j < item.size(); j++) {
                            String itemText = (String) item.get(j).getText();
                            if (!(Integer.toHexString(tags.get(i).tagID).equals(itemText))) {

                                // Trovato id non presente nella lista, quindi la rigenera
                                regenerateRadioGroup();
                                return;
                            }
                        }
                    }
                }

                @Override
                public void onError(final String shortDescription, final Exception error) {

                    // Segnalazione del problema sulla UI tramite un toast
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), shortDescription,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    /**
     * Rigenera la lista che gestisce gli id disponibili
     */
    private void regenerateRadioGroup() {
        Log.d(MainActivityTAG, "regenerateRadioGroup");

        /*
         Esecuzione forzata nello UI Thread come richiesto dalla documentazione Android
         per le modifiche dell'interfaccia utente.
         */
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v(MainActivityTAG, "regenerateRadioGroup: running");
                // Ricezione id connessi
                List<Integer> ids;
                synchronized (controllerLock) {
                    ids = myController.getTagIDs();
                }

                // Pulizia RadioGroup ospitante i RadioButtons
                listIDsGroup.removeAllViews();
                // Pulizia RadioButtons
                item.clear();

                // Popolazione dell'array di RadioButtons
                for (int i = 0; i < ids.size(); i++) {
                    Log.v(MainActivityTAG, "regenerateRadioGroup: " +
                            "ciclo for, i = " + i);
                    item.add(new RadioButton(getApplicationContext()));
                    final int singleId = ids.get(i);
                    final String idText = Integer.toHexString(singleId);

                    // Etichetta del RadioButton
                    item.get(i).setText(idText);

                    // Modifica aspetto del RadioButton
                    item.get(i).setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    item.get(i).setTextColor(connectedToId.getTextColors());

                    // Click listener specifico di ogni singolo RadioButton
                    item.get(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(MainActivityTAG, "regenerateRadioGroup:"
                                    + " onClick " + idText);

                            synchronized (controllerLock) {
                                if (myController == null) {
                                    Log.i(MainActivityTAG, "regenerateRadioGroup:"
                                            + " onClick " + idText + ": myController == null");
                                    return;
                                }
                            }

                            // Se esiste già un tagListener, è da rimuovere
                            if(id != -1) {
                                Log.v(MainActivityTAG, "regenerateRadioGroup -> onClick " +
                                        "(id == " + id + ") !=-1: rimuovo lo scorso tagListener");
                                synchronized (controllerLock) {
                                    myController.removeTagListener(idTagListener);
                                }
                                idTagListener = null;
                            }

                            // Selezione specifico id
                            connectToSpecificListener(singleId);
                        }
                    });

                    /*
                     Controlla se il bottone era stato premuto in precedenza;
                     se sì, mostra RadioButton premuto
                     */
                    if (id != -1 && id == singleId) {
                        Log.v(MainActivityTAG, "regenerateRadioGroup:" +
                                " ciclo for, i = " + i + ", RadioButton toggled: (id = " + id +
                                ") == (singleid = " + singleId + ")");
                        item.get(i).toggle();
                    }

                    // Aggiunta del bottone in fondo alla lista
                    listIDsGroup.addView(item.get(i), -1,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                }

                // Pulizia scorso idLayout (ospita RadioGroup)
                idLayout.removeAllViews();

                // Pubblicazione RadioGroup rigenerato su idlayout
                idLayout.addView(listIDsGroup);
            }
        });

    }

    /**
     * Connessione al TagListener dell'id selezionato, il quale invia il dato della distanza
     *
     * @param singleId Id specifico del tag scelto
     */
    private void connectToSpecificListener(int singleId) {
        Log.d(MainActivityTAG, "connectToSpecificListener: connectedToId = "
                + connectedToId);
        id = singleId;

        // Visualizzazione a schermo dell'ID al quale si è connessi
        String idText = Integer.toHexString(id);
        connectedToId.setText(idText);

        // Listener dello specifico tag
        idTagListener = new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagHasConnected: Connesso a " +
                        Integer.toHexString(id));

                // Mostra distanza rilevata
                setDistanceText(tagDistance, distanceView);
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                        "addTagListener -> onTagHasDisconnected: disconnesso id = " +
                        Integer.toHexString(id) + " --> suono allarme");

                // Tag disconnesso, ovvero fuori portata, quindi suona allarme.
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

                // Controllo se è il caso di accendere l'allarme
                if ((tagDistance > maxDistance) && (!alarmMuted) && (!alarmStatus)) {
                    Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                            "addTagListener -> onTagDataAvailable: " +
                            "(tagDistance == " + tagDistance +
                            ") > (maxDistance == " + maxDistance + "), " +
                            "(alarmMuted == false), (alarmStatus == false)");

                    alarmStatus = true;
                    distanceAlarm();
                }

                /*
                 Controllo se tag rientra entro la distanza impostata; se sì e allarme è acceso
                 allora la spegne
                 */
                if ((tagDistance <= maxDistance) && (alarmStatus)) {
                    try {
                        Log.i(MainActivityTAG, "connectToSpecificListener -> " +
                                "addTagListener -> onTagDataAvailable: " +
                                "(tagDistance == " + tagDistance +
                                ") <= (maxDistance == " + maxDistance + "), " +
                                "(alarmStatus == true)");

                        alarmMuted = false;
                        alarmStatus = false;

                        if(myAlarm != null) {
                            // Spegne allarme
                            myAlarm.stop();
                        }

                        if(pulsante != null){
                            // Deregistra la callback della pressione del pulsante fisico
                            pulsante.unregisterGpioCallback(pulsanteCallback);
                        }
                    } catch (IOException e) {
                        Log.e(MainActivityTAG, "Errore nella chiusura dell'allarme", e);
                    }
                }

                // Mostra distanza rilevata
                setDistanceText(tagDistance, distanceView);
            }

            @Override
            public void onError(String shortDescription, Exception error) {
                // Non serve far nulla poiché già gestito nel metodo onError di AllTagsListener
            }
        };

        synchronized (controllerLock) {
            // Aggiungo il listener precedentemente inizializzato
            myController.addTagListener(id, idTagListener);
        }
    }

    /**
     * Aggiorna la View con la distanza ricevuta
     *
     * @param distance     distanza ricevuta
     * @param distanceView TextView dove aggiornare la distanza
     */
    private void setDistanceText(final int distance, final TextView distanceView) {
        Log.v(MainActivityTAG, "setDistanceText: id = " + Integer.toHexString(id)
                + ", tagDistance = " + distance);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String newText = (new DecimalFormat("#0.00")
                        .format((double)distance*1e-3)) + " m";
                distanceView.setText(newText);
            }
        });
    }

    /**
     * Avvia l'allarme che è possibile silenziare dal bottone fisico.
     * Si disattiva automaticamente se il tag ritorna dentro il limite.
     */
    private void distanceAlarm() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(getApplicationContext(),
                        R.string.alarm_toast, Toast.LENGTH_LONG);
                t.show();
                Log.d(MainActivityTAG, "distanceAlarm: running");
                try {
                    if(myAlarm != null) {
                        // Avvia allarme
                        myAlarm.start();
                    }

                    if(pulsante != null) {
                        // Registra la callback della pressione del pulsante fisico
                        pulsante.registerGpioCallback(pulsanteCallback);
                    }
                } catch (IOException e) {
                    Log.e(MainActivityTAG, "distanceAlarm -> Errore:", e);
                }
            }
        });
    }

    /**
     * Si occupa di chiudere l'istanza di DistanceController
     */
    private void closeController()
    {
        synchronized (controllerLock) {
            // Se istanza di DistanceController già creata allora deve essere chiusa
            if (myController == null) {
                Log.v(MainActivityTAG, "closeController: myController == null: " +
                        "niente da chiudere");
                return;
            }
            Log.v(MainActivityTAG, "closeController: myController =! null: " +
                    " chiusura del controller.");
            myController.close();
            myController = null;
        }
    }

    /**
     * Chiude e rilascia le risorse
     */
    @Override
    public void onPause() {
        Log.v(MainActivityTAG, "onPause");
        super.onPause();

        // Chiusura periferiche
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

        Log.d(MainActivityTAG, "onPause -> chiusura controller");
        // Chiusura Controller
        closeController();
    }
}