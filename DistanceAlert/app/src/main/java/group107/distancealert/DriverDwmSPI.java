package group107.distancealert;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Byte.toUnsignedInt;

public class DriverDwmSPI {
    private static SpiDevice mySPI;

    /**
     * @param bus stringa relativa al bus SPI a cui è connesso il modulo,
     *            ovvero, per la raspberry: "SPI0.0" o "SPI0.1".
     * @throws IOException
     * @throws InterruptedException
     */
    public DriverDwmSPI(String bus) throws IOException, InterruptedException {
        PeripheralManager manager = PeripheralManager.getInstance();
        mySPI = manager.openSpiDevice(bus);

        setupSPI();
        resetDWM();
    }

    /**
     * Configura i parametri della comunicazione SPI necessari per il modulo DWM
     * @throws IOException
     */
    private void setupSPI() throws IOException{
        mySPI.setMode(SpiDevice.MODE0);
        mySPI.setFrequency(8000000);
        mySPI.setBitsPerWord(8);
        mySPI.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);
    }

    /**
     * Fa il reset dello stato della comunicazione del modulo DWM.
     * Nel caso non ottenga una risposta corretta, ovvero ci sono dei problemi hardware,
     * lancia l'eccezione: "SPI device not connected".
     * @throws IOException
     * @throws InterruptedException
     */
    private void resetDWM() throws IOException, InterruptedException {
        // Invio 3 byte 0xff al modulo DWM
        transferData(new byte[1],true);
        TimeUnit.MICROSECONDS.sleep(50);
        transferData(new byte[1],true);
        TimeUnit.MICROSECONDS.sleep(50);
        int response=transferData(new byte[1],true)[0];

        // Se l'ultimo byte ricevuto è diverso da 0xff significa che c'è un problema
        if(response!=0xff) {
            mySPI.close();
            mySPI = null;
            throw new IOException("SPI device not connected");
        }
    }

    /**
     * @param buffer array contenete i byte da inviare via SPI
     * @param autoFill l'opzione autoFill è abilitata riempie il buffer di 0xff
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException
     */
    private int[] transferData(byte[] buffer, boolean autoFill) throws IOException {
        // Riempie l'array buffer di 0xff nel caso l'opzione autoFill sia true
        if(autoFill) {
            Arrays.fill(buffer, (byte) 0xff);
        }

        // Istanzia l'array response
        byte[] response = new byte[buffer.length];

        // Trasferimento dati via SPI, i dati da inviare sono nell'array Buffer,
        // i dati ricevuti vengono salvati nell'array response
        mySPI.transfer(buffer, response, buffer.length);

        // Conversione dei dati ricevuti a unsigned int
        int[] intResponse=new int[response.length];
        for (int i=0; i<response.length; i++) {
            intResponse[i]=toUnsignedInt(response[i]);
        }

        return intResponse;
    }

    /**
     * Riceve come parametri il tag e il valore dell'API. (Esempio: tag=0x0c, value=(0x01,0x05))
     * Il parametro value può anche essere un array vuoto nel caso l'API non preveda paramentri.
     * @param tag byte
     * @param value byte[]
     * @return array int[] contenente il pacchetto di byte ricevuti in risposta dal modulo DWM
     * @throws IOException
     * @throws InterruptedException
     */
    public int[] query(byte tag, byte[] value) throws IOException, InterruptedException {
        // Controllo che il tag richiesto e i relativi valori abbiano senso
        if(tag!=0 && value.length<255){
            throw new IOException("Bad parameters");
        }

        // Praparazione pacchetto TLV da inviare al modulo
        byte[] buffer= new byte[value.length+2];
        buffer[0]=tag;
        buffer[1]=(byte)value.length;
        for(int i=0; i<value.length; i++){
            buffer[2+i]=value[i];
        }

        // Trasferimento pacchetto a DWM
        transferData(buffer,false);

        // Attesa della costruzione della risposta da parte del modulo
        int length;
        long timer =System.currentTimeMillis();
        do{
            TimeUnit.MICROSECONDS.sleep(50);
            length=transferData(new byte[1],true)[0];
        }while(length==0 && System.currentTimeMillis()-timer<10);
        TimeUnit.MICROSECONDS.sleep(50);

        // Ricezione della risposta
        int[] result = transferData(new byte[length],true);

        return result;
    }

    /**
     * Chiusura e rilascio della periferica SPI
     * @throws IOException
     */
    public void close() throws IOException {
        if (mySPI != null) {
            mySPI.close();
            mySPI = null;
        }
    }
}
