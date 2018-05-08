package group107.distancealert;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.UartDevice;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import static java.lang.Byte.toUnsignedInt;

// classe model
public class DriverDWM {
    private static SpiDevice mySPI;
    private static UartDevice myUART;

    /**
     * @param bus stringa relativa al bus SPI o UART a cui è connesso il modulo,
     *            ovvero, per la raspberry: "SPI0.0" o "SPI0.1" o "MINIUART".
     * @throws IOException
     * @throws InterruptedException
     */
    public DriverDWM(String bus) throws IOException, InterruptedException {
        PeripheralManager manager = PeripheralManager.getInstance();
        mySPI = manager.openSpiDevice(bus);

        configureCommunication();
        resetCommunication();
    }

    /**
     * Configura i parametri della comunicazione SPI o UART necessari per il modulo DWM
     * @throws IOException
     */
    private void configureCommunication() throws IOException{
        if(mySPI!=null) {
            mySPI.setMode(SpiDevice.MODE0);
            mySPI.setFrequency(8000000);
            mySPI.setBitsPerWord(8);
            mySPI.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);
        }

        if(myUART!=null){
            myUART.setBaudrate(115200);
            myUART.setDataSize(8);
            myUART.setParity(UartDevice.PARITY_NONE);
            myUART.setStopBits(1);
        }
    }

    /**
     * Fa il reset dello stato della comunicazione del modulo DWM.
     * Nel caso non ottenga una risposta corretta, ovvero ci sono dei problemi hardware,
     * lancia l'eccezione: "SPI device not connected".
     * @throws IOException
     * @throws InterruptedException
     */
    private void resetCommunication() throws IOException, InterruptedException {
    /*    // Invio 3 byte 0xff al modulo DWM
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
        }*/
    }


    /**
     * Chiusura e rilascio della periferica SPI e/o UART
     * @throws IOException
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
