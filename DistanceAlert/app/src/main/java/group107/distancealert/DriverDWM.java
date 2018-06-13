package group107.distancealert;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Byte.toUnsignedInt;

/**
 * Classe "Model" del design pattern Model-View-Controller.
 * Questa classe ha lo scopo di gestire completamente la comunicazione con il modulo DWM1001-DEV,
 * sia che esso sia collegato via UART o via SPI.
 * Quando si ha finito di usare un oggetto di questa classe è importante invocare il metodo close
 * per rilasciare le periferiche hardware utilizzate.
 */
public class DriverDWM {
    private static final String TAG = "DriverDWM";

    /**
     * Oggetti riferiti alle periferiche SPI e UART
     */
    private SpiDevice mySPI;
    private UartDevice myUART;

    /**
     * Parametri costanti usati per gestire la temporizzazione durante le comunicazioni SPI e UART
     */
    private static final long SPI_SLEEP_TIME = 100L; // microsecondi
    private static final long MAX_SPI_WAIT = 20L;   // millisecondi
    private static final long MAX_UART_WAIT = 50L;  // millisecondi

    /**
     * Costruttore: verica la validità del busName richiesto e configura la comunicazione
     *
     * @param busName stringa relativa al bus SPI o UART a cui è connesso il modulo,
     *                ovvero, per la raspberry: "SPI0.0" o "SPI0.1" o "MINIUART".
     * @throws IOException Lanciata se ci sono problemi di accesso alla periferica
     */
    @SuppressWarnings("WeakerAccess")
    public DriverDWM(String busName) throws IOException {
        // Ottengo istanza di PeripheralManager per poter gestire le periferiche
        PeripheralManager manager = PeripheralManager.getInstance();

        /*
        Se il busName è un bus SPI, prova ad ottenere un'istanza della periferica SPI.
         */
        if (busName.contains("SPI")) {
            mySPI = manager.openSpiDevice(busName);
        }

        // Se invece il busName è un bus UART, prova ad ottenere un'istanza della periferica UART.
        else if (busName.contains("UART")) {
            myUART = manager.openUartDevice(busName);
        }

        // Se il busName non viene riconosciuto, lancia un'eccezione
        else {
            throw new IllegalArgumentException("Unrecognized bus name");
        }

        // Configurazione dei parametri della comunicazione per il modulo DWM
        configureCommunication();
    }

    /**
     * Configura i parametri della comunicazione SPI o UART necessari per il modulo DWM
     *
     * @throws IOException Lanciata se ci sono problemi di accesso alla periferica
     */
    private void configureCommunication() throws IOException {
        /*
         Caso SPI:
                Modalità 0
                Clock = 8MHz
                Numero di bit = 8
                ordine dei bit = prima quelli più significativi
         */
        if (mySPI != null) {
            mySPI.setMode(SpiDevice.MODE0);
            mySPI.setFrequency(8000000);
            mySPI.setBitsPerWord(8);
            mySPI.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);
        }

        /*
         Caso UART:
                Baud-rate = 115200
                Numero di bit = 8
                Nessun bit di parità
                Un bit di stop
         */
        else {
            myUART.setBaudrate(115200);
            myUART.setDataSize(8);
            myUART.setParity(UartDevice.PARITY_NONE);
            myUART.setStopBits(1);
        }
    }

    /**
     * Fa il reset dello stato della comunicazione del modulo DWM.
     * Fa la richiesta di una API e controlla la risposta ricevuta.
     * Nel caso non ottenga una risposta corretta, ovvero ci sono dei problemi gravi,
     * probabilmente nell'hardware. Dunque lancia le relative eccezioni.
     *
     * @throws IOException Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     */
    public void checkDWM() throws IOException {
        // Reset: caso SPI
        if (mySPI != null) {
            /*
            Per resettare lo stato della comunicazione SPI, si inviano 3 byte 0xff in 3 trasferimenti
            separati da delle brevi pause per dare tempo al modulo di fare quello che deve fare
            */
            transferViaSPI(new byte[1], true);
            try {
                // Breve pausa tra due trasferimenti consecutivi
                TimeUnit.MICROSECONDS.sleep(SPI_SLEEP_TIME);
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted", e);
            }
            transferViaSPI(new byte[1], true);
            try {
                TimeUnit.MICROSECONDS.sleep(SPI_SLEEP_TIME);
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted", e);
            }
            int response = transferViaSPI(new byte[1], true)[0];

            // Se l'ultimo byte ricevuto è diverso da 0xff significa che c'è un problema
            if (response != 0xff) {
                mySPI.close();
                mySPI = null;
                throw new IOException("SPI device not connected");
            }
        }

        // Reset: caso UART
        else {
            // La semplice pulizia dei buffer di input e di output è sufficiente
            myUART.flush(UartDevice.FLUSH_IN_OUT);
        }

        /*
         Controllo che la comunicazione col modulo funzioni correttamente richiedendo l'API 0x04,
         ovvero quella per ricevere le frequenze di aggiornamento del modulo.
         Poi controllo che l'operazione sia andata a buon fine, se no lancia un'eccezione.
         */
        int[] buffer = requestAPI((byte) 0x04, null);
        if (buffer[0] != 0x40 || buffer[1] != 0x01 || buffer[2] != 0x00) {
            throw new IOException("Communication problem: check hardware and reset DWM");
        }
    }

    /**
     * Effettua la richiesta della API e con i relativi valori al modulo DWM e ritorna la rispota
     * ottenuta convertita in unsigned int.
     * N.B. Questo metodo è bloccante e può richiedere fino a 50ms per essere completato.
     * (Attenzione che la durata dipende anche da condizioni esterne al modulo)
     *
     * @param tag   byte relativo alla API da usare
     * @param value byte[] array di byte contente i valori da passare alla API.
     *              Può anche essere null nel caso non siano previsti valori da passare.
     * @return array int[] contenente il pacchetto di byte ricevuti in risposta dal modulo DWM
     * @throws IOException              Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     * @throws IllegalArgumentException Lanciata se vengono passati parametri insensati
     */
    public int[] requestAPI(byte tag, byte[] value) throws IOException, IllegalArgumentException {
        // Ottengo la lunghezza dell'array dei valori della API
        int L = value == null ? 0 : value.length;

        // Controllo che il tag richiesto e i relativi valori abbiano senso
        if (tag != 0 && L > 255) {
            throw new IllegalArgumentException("Bad parameters");
        }

        // Praparo il pacchetto TLV da inviare al modulo
        byte[] buffer = new byte[L + 2];
        buffer[0] = tag;
        buffer[1] = (byte) L;
        if (L > 0) {
            System.arraycopy(value, 0, buffer, 2, L);
        }

        // Caso SPI
        if (mySPI != null) {
            return requestViaSPI(buffer);
        }

        // Caso UART
        else {
            return requestViaUART(buffer);
        }
    }

    /**
     * Gestisce le varie fasei dello scambio di dati via SPI, ovvero l'invio della richiesta,
     * l'attesa della preparazione della risposta dal modulo DWM e la ricezione della stessa.
     * Se non riceve alcuna risposta entro MAX_SPI_WAIT lancia la relativa eccezione.
     *
     * @param transmit array contenete i byte da inviare via UART
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     */
    private int[] requestViaSPI(byte[] transmit) throws IOException {
        // Trasferimento pacchetto a DWM contentente la richiesta
        transferViaSPI(transmit, false);

        /*
        Attesa della costruzione della risposta da parte del modulo.
        Finché non è pronta lui risponde sempre 0x00.
        Quando è pronta, invece, comunica la lunghezza totale della risposta da leggere.
        Se l'attesa va oltre il tempo massimo significa che ci sono dei problemi.
         */
        int length;
        long timer = System.currentTimeMillis();
        do {
            try {
                // Breve pausa tra due trasferimenti consecutivi
                TimeUnit.MICROSECONDS.sleep(SPI_SLEEP_TIME);
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted", e);
            }

            // Ricezzione del byte contente la lunghezza della risposta
            length = transferViaSPI(new byte[1], true)[0];
        } while ((length == 0x00) && ((System.currentTimeMillis() - timer) < MAX_SPI_WAIT));

        // Nel caso ci siano stati problemi di comunicazione
        if (length == 0x00 || length == 0xff) {
            throw new IOException("Communication error via SPI");
        }

        try {
            // Breve pausa tra due trasferimenti consecutivi
            TimeUnit.MICROSECONDS.sleep(SPI_SLEEP_TIME);
        } catch (InterruptedException e) {
            Log.w(TAG, "Sleep interrupted", e);
        }

        // Ricezione della risposta
        return transferViaSPI(new byte[length], true);
    }

    /**
     * Gestisce lo scambio di dati  via SPI, con l'opzione, utile per le specifiche del DWM, di
     * riempire automaticamente di 0xff l'array di byte da inviare.
     *
     * @param transmit array contenete i byte da inviare via SPI
     * @param autoFill l'opzione autoFill è abilitata riempie il buffer di 0xff
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     */
    private int[] transferViaSPI(byte[] transmit, boolean autoFill) throws IOException {
        // Nel caso l'opzione autoFill sia true, riempio l'array trasmit di 0xff
        if (autoFill) {
            Arrays.fill(transmit, (byte) 0xff);
        }

        // Istanzio l'array receive di lunghezza pari a quella di trasmit
        byte[] receive = new byte[transmit.length];

        /*
        Trasferimento dati via SPI, i dati da inviare sono nell'array trasmit,
        i dati ricevuti vengono salvati nell'array receive
         */
        mySPI.transfer(transmit, receive, transmit.length);

        // Conversione dei dati ricevuti a unsigned int
        int[] intReceive = new int[receive.length];
        for (int i = 0; i < receive.length; i++) {
            intReceive[i] = toUnsignedInt(receive[i]);
        }

        return intReceive;
    }

    /**
     * Gestisce le varie fasei dello scambio di dati via UART, ovvero l'invio della richiesta e
     * l'attesa della risposta dal modulo DWM.
     * Se non riceve alcuna risposta entro MAX_UART_WAIT lancia la relativa eccezione.
     *
     * @param transmit array contenete i byte da inviare via UART
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     */
    private int[] requestViaUART(byte[] transmit) throws IOException {
        // Reset della comunicazione e invio della richiesta
        myUART.flush(UartDevice.FLUSH_IN_OUT);
        myUART.write(transmit, transmit.length);

        // Preparazione dei buffer e dei contatori usati per salvare la risposta
        byte[] totalReceive = new byte[255];
        int totalCount = 0;
        byte[] tempReceive = new byte[20];
        int tempCount;

        // Aspetta che arrivi la risposta entro il tempo d'attesa massimo
        waitUART(MAX_UART_WAIT);

        // Leggi i dati in arrivo
        while ((tempCount = myUART.read(tempReceive, tempReceive.length)) > 0) {
            // Nel caso ci siano problemi di comunicazione, lancia eccezione
            if (totalCount + tempCount > 255) {
                throw new IOException("Communication error via UART: endless communication");
            }

            /*
            Se ha letto qualche byte li trasferisce dall'array temporaneo all'array complessivo.
            Questo perché i byte non vengono ricevuti tutti assieme, ma in gruppi di
            lunghezza variabile. La lunghezza massima è di 20 byte per ogni gruppo.
             */
            System.arraycopy(tempReceive, 0, totalReceive, totalCount, tempCount);
            totalCount += tempCount;
        }

        // Nel caso ci siano problemi di comunicazione, lancia eccezione
        if (totalCount == 0) {
            throw new IOException("Communication error via UART: nothing received");
        }

        // Ritaglia array tenendo solo la parte interessante
        if (totalCount > 0) {
            totalReceive = Arrays.copyOfRange(totalReceive, 0, totalCount);
        }

        // Conversione dei dati ricevuti a unsigned int
        int[] intReceive = new int[totalReceive.length];
        for (int i = 0; i < totalReceive.length; i++) {
            intReceive[i] = toUnsignedInt(totalReceive[i]);
        }

        return intReceive;
    }

    /**
     * Aspetta che siano ricevuti dei dati dalla periferica UART.
     * Se l'attesa si protrae oltre il limite impostato viene comunque terminata
     *
     * @param maxTimeWait_millis tempo d'attesa massimo
     * @throws IOException Lanciata se ci sono problemi di accesso alla periferica
     */
    private void waitUART(long maxTimeWait_millis) throws IOException {
        // Creazione di un thread separato su cui svolgere la callback della UART
        myThread = new HandlerThread("UartCallbackThread");
        myThread.start();
        Handler myHandler = new Handler(myThread.getLooper());

        // Registrazione della callback
        myUART.registerUartDeviceCallback(myHandler, myUartDeviceCallBack);

        synchronized (lock) {
            try {
                // Thread principale in pausa per il tempo impostato o finché callback non lo sveglia
                lock.wait(maxTimeWait_millis);
            } catch (InterruptedException e) {
                Log.w(TAG, "UART wait interrupted");
            }
        }

        // Chiusura del thread della callback
        myThread.quitSafely();
        myThread = null;

        // Nel caso durante l'attesa fosse stata chiusa la periferica, lancia la seguente ecezione
        if (myUART == null) {
            throw new IOException("Communication error via UART: communication interrupted");
        }
    }

    // Oggetto usato per la sincronizzazione tra il thread principale e il thread della callback
    private final Object lock = new Object();//TODO commenti
    private HandlerThread myThread;
    private final UartDeviceCallback myUartDeviceCallBack = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uartDevice) {
            synchronized (lock) {
                // Risveglia il thread principale
                lock.notify();
            }

            // Callback viene eseguita solo una volta e poi si deregistra
            return false;
        }
    };

    /**
     * Chiusura e rilascio della periferica SPI e UART se erano aperte
     *
     * @throws IOException Lanciata se ci sono problemi nella chiusura della periferica
     */
    public void close() throws IOException {
        if (mySPI != null) {
            // Chiusura SPI
            mySPI.close();
            mySPI = null;
        }

        if (myUART != null) {
            // Deregitro la callback
            myUART.unregisterUartDeviceCallback(myUartDeviceCallBack);

            //Chiusura UART
            myUART.close();
            myUART = null;

            // Eventuale terminazione del thread della callback e risveglio del thread principale
            if (myThread != null) {
                myThread.quitSafely();
                synchronized (lock) {
                    lock.notify();
                }
            }
        }
    }
}