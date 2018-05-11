package group107.distancealert;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Byte.toUnsignedInt;

/**
 * Classe "Model" del design pattern Model-View-Controller.
 * Questa classe ha lo scopo di gestire completamente la comunicazione con il modulo DWM1001-DEV,
 * sia che esso sia collegato via UART o via SPI.
 * N.B. Invocare il metodo close quando si ha finito di usarla.
 */
public class DriverDWM {
    /**
     * Oggetti riferiti alle periferiche SPI e UART
     */
    private SpiDevice mySPI;
    private UartDevice myUART;

    /**
     * @param busName stringa relativa al bus SPI o UART a cui è connesso il modulo,
     *                ovvero, per la raspberry: "SPI0.0" o "SPI0.1" o "MINIUART".
     * @throws IOException          Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     * @throws InterruptedException Lanciata se viene interrotto lo sleep nella comunicazione SPI
     */
    public DriverDWM(String busName) throws IOException, InterruptedException {
        PeripheralManager manager = PeripheralManager.getInstance();

        /*
        Se il busName è un bus SPI, prova ad ottenere un'istanza della periferica SPI.
        Nel caso in precedenza fosse stata istanziata la UART, viene chiusa.
         */
        if (busName.contains("SPI")) {
            mySPI = manager.openSpiDevice(busName);
            if (myUART != null) {
                myUART.close();
            }
            myUART = null;
        }

        // Se, invece, il busName è un bus UART faccio l'opposto.
        else if (busName.contains("UART")) {
            myUART = manager.openUartDevice(busName);
            if (mySPI != null) {
                mySPI.close();
            }
            mySPI = null;
        }

        // Se il busName non viene riconosciuto, lancia un'eccezione
        else {
            throw new IllegalArgumentException("Unrecognized bus name");
        }

        /*
        Configurazione, reset e controllo della comunicazione.
        Se ci sono problemi vengono lanciate le relative eccezioni
         */
        configureCommunication();
        checkCommunication();
    }

    /**
     * Configura i parametri della comunicazione SPI o UART necessari per il modulo DWM
     *
     * @throws IOException Lanciata se ci sono problemi di accesso alla periferica
     */
    private void configureCommunication() throws IOException {
        // Caso SPI
        if (mySPI != null) {
            mySPI.setMode(SpiDevice.MODE0);
            mySPI.setFrequency(8000000);
            mySPI.setBitsPerWord(8);
            mySPI.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);
        }

        // Caso UART
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
     * @throws IOException          Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     * @throws InterruptedException Lanciata se viene interrotto lo sleep nella comunicazione SPI
     */
    private void checkCommunication() throws IOException, InterruptedException {
        // Reset: caso SPI
        if (mySPI != null) {
            // Invio 3 byte 0xff al modulo DWM per resettare lo stato della comunicazione SPI
            transferViaSPI(new byte[1], true);
            TimeUnit.MICROSECONDS.sleep(50);
            transferViaSPI(new byte[1], true);
            TimeUnit.MICROSECONDS.sleep(50);
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
     * @throws InterruptedException     Lanciata se viene interrotto lo sleep nella comunicazione SPI
     * @throws IllegalArgumentException Lanciata se vengono passati parametri insensati
     */
    public int[] requestAPI(byte tag, byte[] value) throws IOException, InterruptedException, IllegalArgumentException {
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
            // Trasferimento pacchetto a DWM
            transferViaSPI(buffer, false);

            /*
            Attesa della costruzione della risposta da parte del modulo.
            Finché non è pronta lui risponde sempre 0x00.
            Quando è pronta, invece, comunica la lunghezza totale della risposta da leggere.
            Se l'attesa va oltre i 10ms significa che ci sono dei problemi.
             */
            int length;
            long timer = System.currentTimeMillis();
            do {
                TimeUnit.MICROSECONDS.sleep(50);
                length = transferViaSPI(new byte[1], true)[0];
            } while (length == 0x00 && System.currentTimeMillis() - timer < 10L);
            TimeUnit.MICROSECONDS.sleep(50);

            // Nel caso ci siano stati problemi di comunicazione
            if (length == 0x00 || length == 0xff) {
                throw new IOException("Communication error via SPI");
            }

            // Ricezione della risposta
            return transferViaSPI(new byte[length], true);
        }

        // Caso UART
        else {
            return transferViaUART(buffer);
        }
    }

    /**
     * Gestisce lo scambio di dati  via SPI, con l'opzione (utile per le specifiche del DWM) di
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
     * Se non riceve alcuna risposta entro 50ms lancia la relativa eccezione.
     *
     * @param transmit array contenete i byte da inviare via UART
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException Lanciata se ci sono problemi di comunicazione o di accesso alla periferica
     */
    private int[] transferViaUART(byte[] transmit) throws IOException {
        // Reset della comunicazione e invio della richiesta
        myUART.flush(UartDevice.FLUSH_IN_OUT);
        myUART.write(transmit, transmit.length);

        byte[] totalReceive = new byte[255];
        int totalCount = 0;
        long timer = System.currentTimeMillis();

        while (totalCount == 0 && System.currentTimeMillis() - timer < 50L) {
            byte[] tempReceive = new byte[20];
            int tempCount;
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
        }

        // Nel caso ci siano problemi di comunicazione, lancia eccezione
        if (totalCount == 0) {
            throw new IOException("Communication error via UART: nothing received");
        }

        // Ritaglia array tenendo solo la parte interessante
        if (totalCount > 0) {
            System.arraycopy(totalReceive, 0, totalReceive, 0, totalCount);
        }

        // Conversione dei dati ricevuti a unsigned int
        int[] intReceive = new int[totalReceive.length];
        for (int i = 0; i < totalReceive.length; i++) {
            intReceive[i] = toUnsignedInt(totalReceive[i]);
        }

        return intReceive;
    }

    /**
     * Chiusura e rilascio della periferica SPI e/o UART
     *
     * @throws IOException Lanciata se ci sono problemi nella chiusura della periferica
     */
    public void close() throws IOException {
        if (mySPI != null) {
            mySPI.close();
            mySPI = null;
        }

        if (myUART != null) {
            myUART.close();
            myUART = null;
        }
    }
}