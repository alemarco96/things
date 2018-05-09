package group107.distancealert;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import static java.lang.Byte.toUnsignedInt;

// classe model
// TODO sistemare le eccezioni
// TODO controllare i commenti
// TODO commentare la classe
// TODO forse è il caso che il metodo requestAPI venga eseguito automaticamente in un altro thread?
public class DriverDWM {
    private SpiDevice mySPI;
    private UartDevice myUART;

    /**
     * @param busName stringa relativa al bus SPI o UART a cui è connesso il modulo,
     *            ovvero, per la raspberry: "SPI0.0" o "SPI0.1" o "MINIUART".
     * @throws IOException
     * @throws InterruptedException
     */
    public DriverDWM(String busName) throws IllegalArgumentException, IOException, InterruptedException {
        PeripheralManager manager = PeripheralManager.getInstance();

        if(busName.contains("SPI")) {
            mySPI = manager.openSpiDevice(busName);
            if(myUART!=null){
                myUART.close();
            }
            myUART=null;
        }

        else if(busName.contains("UART")) {
            myUART = manager.openUartDevice(busName);
            if(mySPI!=null){
                mySPI.close();
            }
            mySPI=null;
        }

        else{
            throw new IllegalArgumentException("Unrecognized bus name");
        }

        configureCommunication();
        checkCommunication();
    }

    /**
     * Configura i parametri della comunicazione SPI o UART necessari per il modulo DWM
     * @throws IOException
     */
    private void configureCommunication() throws IOException{
        // Caso SPI
        if(mySPI!=null) {
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
     * @throws IOException
     * @throws InterruptedException
     * @throws RuntimeException
     */
    private void checkCommunication() throws IOException, InterruptedException, RuntimeException {
        // Reset: caso SPI
        if(mySPI!=null){
            // Invio 3 byte 0xff al modulo DWM
            transferViaSPI(new byte[1],true);
            TimeUnit.MICROSECONDS.sleep(50);
            transferViaSPI(new byte[1],true);
            TimeUnit.MICROSECONDS.sleep(50);
            int response=transferViaSPI(new byte[1],true)[0];

            // Se l'ultimo byte ricevuto è diverso da 0xff significa che c'è un problema
            if(response!=0xff) {
                mySPI.close();
                mySPI = null;
                throw new IOException("SPI device not connected");
            }
        }

        // Reset: caso UART
        else{
            myUART.flush(UartDevice.FLUSH_IN_OUT);
        }

        // Controllo che la comunicazione col modulo funzioni correttamente
        int[] buffer=requestAPI((byte)0x04,null);
        if(buffer[0]!=0x40 || buffer[1]!=0x01 || buffer[2]!=0x00){
            throw new RuntimeException("Communication problem: check hardware and reset DWM");
        }
    }

    /**
     * @param tag byte relativo alla API da usare
     * @param value byte[] array di byte contente i valori da passare alla API.
     *              Può anche essere null nel caso non siano previsti valori da passare.
     * @return array int[] contenente il pacchetto di byte ricevuti in risposta dal modulo DWM
     * @throws IOException
     * @throws InterruptedException
     * @throws IllegalArgumentException
     */
    public int[] requestAPI(byte tag, byte[] value) throws IOException, InterruptedException, IllegalArgumentException {
        // Ottengo la lunghezza dell'array dei valori della API
        int L=0;
        if(value!=null){
            L=value.length;
        }

        // Controllo che il tag richiesto e i relativi valori abbiano senso
        if(tag!=0 && L>255){
            throw new IllegalArgumentException("Bad parameters");
        }

        // Praparazione pacchetto TLV da inviare al modulo
        byte[] buffer= new byte[L+2];
        buffer[0]=tag;
        buffer[1]=(byte)L;
        for(int i=0; i<L; i++){
            buffer[2+i]=value[i];
        }

        // Caso SPI
        if(mySPI!=null){
            // Trasferimento pacchetto a DWM
            transferViaSPI(buffer,false);

            // Attesa della costruzione della risposta da parte del modulo
            int length;
            long timer =System.currentTimeMillis();
            do{
                TimeUnit.MICROSECONDS.sleep(50);
                length=transferViaSPI(new byte[1],true)[0];
            }while(length==0x00 && System.currentTimeMillis()-timer<10L);
            TimeUnit.MICROSECONDS.sleep(50);

            // Nel caso ci siano problemi di comunicazione
            if(length==0x00 || length==0xff){
                throw new IOException("Communication error via SPI");
            }

            // Ricezione della risposta
            int[] result = transferViaSPI(new byte[length],true);

            return result;
        }

        // Caso UART
        else{
            int[] result = transferViaUART(buffer);

            return result;
        }
    }

    /**
     * @param buffer array contenete i byte da inviare via SPI
     * @param autoFill l'opzione autoFill è abilitata riempie il buffer di 0xff
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException
     */
    private int[] transferViaSPI(byte[] buffer, boolean autoFill) throws IOException {
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
     * @param buffer array contenete i byte da inviare via SPI
     * @return Array int[] contentente i valori ricevuti convertiti in unsigned int
     * @throws IOException
     */
    private int[] transferViaUART(byte[] buffer) throws IOException {
        myUART.flush(UartDevice.FLUSH_IN_OUT);
        myUART.write(buffer,buffer.length);

        byte[] totalResponse=new byte[255];
        int totalCount=0;
        long timer=System.currentTimeMillis();

        while(totalCount==0 && System.currentTimeMillis()-timer<50L) {
            byte[] response=new byte[20];
            int count;
            while ((count=myUART.read(response, response.length))>0) {
                // Nel caso ci siano problemi di comunicazione
                if(totalCount+count>255){
                    throw new IOException("Communication error via UART: endless communication");
                }

                for(int i=0; i<count; i++) {
                    totalResponse[totalCount+i]=response[i];
                }
                totalCount+=count;
            }
        }

        // Nel caso ci siano problemi di comunicazione ()
        if(totalCount==0){
            throw new IOException("Communication error via UART: nothing received");
        }

        // Ritaglia array tenendo solo la parte interessante
        if(totalCount>0) {
            totalResponse = Arrays.copyOfRange(totalResponse, 0, totalCount);
        }

        // Conversione dei dati ricevuti a unsigned int
        int[] intResponse=new int[totalResponse.length];
        for (int i=0; i<totalResponse.length; i++) {
            intResponse[i]=toUnsignedInt(totalResponse[i]);
        }

        return intResponse;
    }

    /**
     * Chiusura e rilascio della periferica SPI e/o UART
     * @throws IOException
     */
    public void close() throws IOException {
        if (mySPI!=null) {
            mySPI.close();
            mySPI=null;
        }

        if (myUART!=null) {
            myUART.close();
            myUART=null;
        }
    }
}