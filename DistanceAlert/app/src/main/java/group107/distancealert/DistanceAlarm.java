package group107.distancealert;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Questa classe ha lo scopo di gestire completamente l'allarme, ovvero il led lampeggiante e il
 * buzzer che suona un semplice motivetto musicale.
 * Quando si ha finito di usare un oggetto di questa classe è importante invocare il metodo close
 * per rilasciare le periferiche hardware utilizzate.
 */
@SuppressWarnings("WeakerAccess")
public class DistanceAlarm {
    /**
     * Stringa utile per log della classe DistanceAlarm
     */
    private final static String TAG = "DistanceAlarm";

    /**
     * Periodo di aggiornamento temporizzato dell'allarme (durata dei toni e del lampeggio del LED)
     */
    private static final long ALARM_PERIOD = 250L;

    /**
     * Sequenza di frequenze del segnale PWM utilizzate per realizzare il motivetto musicale
     * e relativo indice usato per puntare alla nota da eseguire
     */
    private final int[] tone = {
            400, 400, 400,
            440, 440, 440,
            500, 500, 500,
            400, 440, 500,
            400, 440, 500,
            400, 440, 500
    };
    private int toneIndex;

    /**
     * Oggetti riferiti alle periferiche GPIO e PWM
     */
    private Gpio led;
    private Pwm buzzer;

    /**
     * Oggetto usato per la sincronizzazione tra il thread della UI
     * e il thread dell'aggiornamneto temporizzato dell'allarme
     */
    private final Object lock = new Object();

    /**
     * Oggetto ScheduledThreadPoolExecutor usato per gestire la programmazione temporizzata dell'allarme
     */
    private ScheduledThreadPoolExecutor timer;

    /**
     * Costruttore: ottiene accesso alle periferiche e le inizializza
     *
     * @throws IOException Lanciata se ci sono problemi di accesso alle periferiche
     */
    @SuppressWarnings("WeakerAccess")
    public DistanceAlarm(String gpioLed, String pwmBuzzer) throws IOException {
        // Ottengo istanza di PeripheralManager per poter gestire le periferiche
        PeripheralManager manager = PeripheralManager.getInstance();

        /*
         Prova ad ottenere un'istanza della periferica GPIO relativa al
         pin desiderato, la inizializza come uscita inizialmente a 0V e
         ne associa il livello logico alto al livello di tensione alto.
         */
        led = manager.openGpio(gpioLed);
        led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        led.setActiveType(Gpio.ACTIVE_HIGH);

        /*
         Prova ad ottenere un'istanza della periferica PWM relativa al
         pin desiderato, la inizializza adeguatamente e la lascia spenta.
         */
        buzzer = manager.openPwm(pwmBuzzer);
        buzzer.setPwmFrequencyHz(tone[toneIndex]);
        buzzer.setPwmDutyCycle(50);
        buzzer.setEnabled(false);
    }

    /**
     * Avvia l'allarme e imposta la programmazione temporizzata che permette di cambiare tono
     * al buzzer e di far lampeggiare il LED.
     *
     * @throws IOException Lanciata se ci sono problemi di accesso alle periferiche
     */
    public void start() throws IOException {
        // Controlla che l'allarme non sia già avviato
        if (timer != null) {
            return;
        }

        // Reset inizio motivetto musicale
        toneIndex = 0;

        // Avvio programmazione temporizzata
        timer = new ScheduledThreadPoolExecutor(1);

        /*
         Impostazione necessaria per spegnere correttamente il timer completando l'esecuzione del
         relativo task già avviato prima dello spegnimento
         */
        timer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        timer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                /*
                 Cambia tono emesso dal buzzer e cambia stato al GPIO del LED.
                 Usa synchronized (lock) per evitare l'esecuzione simultanea
                 con eventuali chiamate ai metodi stop() e close().
                 */
                synchronized (lock) {
                    toneIndex = (toneIndex + 1) % tone.length;
                    try {
                        buzzer.setPwmFrequencyHz(tone[toneIndex]);
                        led.setValue((!led.getValue()) && (timer != null));
                    } catch (IOException e) {
                        Log.w(TAG, "Exception updating alarm state", e);
                    }
                }
            }
        }, 0L, ALARM_PERIOD, TimeUnit.MILLISECONDS);

        // Abilita la periferica PWM
        buzzer.setEnabled(true);
    }

    /**
     * Terminazione della programmazione temporizzata sul timer, oltre a spegnere il LED e il buzzer
     *
     * @throws IOException Lanciata se ci sono problemi nella chiusura delle periferiche
     */
    public void stop() throws IOException {
        // Controlla che l'allarme non sia già spento
        if (timer == null) {
            return;
        }

        // Ferma timer
        timer.shutdown();
        timer = null;

        /*
         Spegne LED e buzzer facendo attenzione alla sincronizzazione
         con l'aggiornamento temporizzato dell'allarme
         */
        synchronized (lock) {
            led.setValue(false);
            buzzer.setEnabled(false);
        }
    }

    /**
     * Terminazione della programmazione temporizzata sul timer e
     * rilascio delle periferiche relative al led e al buzzer.
     *
     * @throws IOException Lanciata se ci sono problemi nella chiusura delle periferiche
     */
    public void close() throws IOException {
        // Spegne l'allarme se è attivato
        if (timer != null) {
            stop();
        }

        // Chiusura delle periferiche
        synchronized (lock) {
            if (led != null) {
                led.close();
                led = null;
            }

            if (buzzer != null) {
                buzzer.close();
                buzzer = null;
            }
        }
    }
}