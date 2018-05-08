package group107.distancealert;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Byte.toUnsignedInt;

public class DriverDwmUART {
    private static UartDevice myUART;

    public DriverDwmUART(String bus) throws IOException, InterruptedException {
        PeripheralManager manager = PeripheralManager.getInstance();
        myUART = manager.openUartDevice(bus);

        setupUART();
        resetDWM();
    }

    private void setupUART() throws IOException{
        myUART.setBaudrate(115200);
        myUART.setDataSize(8);
        myUART.setParity(UartDevice.PARITY_NONE);
        myUART.setStopBits(1);
    }

    private void resetDWM() throws IOException, InterruptedException {
    }

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


        // Ricezione della risposta

        return null;
    }

    /**
     * Chiusura e rilascio della periferica UART
     * @throws IOException
     */
    public void close() throws IOException {
        if (myUART != null) {
            myUART.close();
            myUART = null;
        }
    }
}
