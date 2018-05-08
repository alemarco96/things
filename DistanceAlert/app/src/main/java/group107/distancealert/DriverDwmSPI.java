package group107.distancealert;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Byte.toUnsignedInt;

public class DriverDwmSPI {
    private static SpiDevice mySPI;

    public DriverDwmSPI(String bus) throws IOException {
        PeripheralManager manager = PeripheralManager.getInstance();
        mySPI = manager.openSpiDevice(bus);

        setupSPI();
        resetDWM();
    }

    private void setupSPI() throws IOException{
        mySPI.setMode(SpiDevice.MODE0);
        mySPI.setFrequency(8000000);
        mySPI.setBitsPerWord(8);
        mySPI.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);
        return;
    }

    private void resetDWM() throws IOException{
        transferData(new byte[1],true);
        transferData(new byte[1],true);
        int response=transferData(new byte[1],true)[0];

        if(response!=0xff){
            mySPI.close();
            mySPI=null;
            throw new IOException("SPI device not connected");
        }
        return;
    }

    private int[] transferData(byte[] buffer, boolean autoFill) throws IOException {
        if(buffer.length<=0 || buffer.length==255){
            return null;
        }

        if(autoFill==true) {
            Arrays.fill(buffer, (byte) 0xff);
        }

        byte[] response = new byte[buffer.length];

        mySPI.transfer(buffer, response, buffer.length);

        int[] intResponse=new int[response.length];
        for (int i=0; i<response.length; i++) {
            intResponse[i]=toUnsignedInt(response[i]);
        }

        return intResponse;
    }

    /**
     * Receive as parameter the TAG of the API and the value of the API (Example: TAG=0x0C, Value=(0x00))
     * @param tag byte
     * @param value byte[]
     * @return byte[] containing the packets received from DWM after the query
     * @throws IOException
     * @throws InterruptedException
     */
    public int[] query(int tag, int[] value) throws IOException, InterruptedException {
        if(tag!=0 && value.length<255){//controlla tag e value

        }

        byte[] buffer= new byte[value.length+2];
        buffer[0]=tag;//TODO converti int in byte hex
        buffer[1]=value.length;//TODO converti int in byte hex
        for(int i=0; i<value.length;i++){
            buffer[2+i]=value[i];//TODO converti int in byte hex
        }

        transferData(buffer,false);

        int length;
        long timer =System.currentTimeMillis();
        do{
            TimeUnit.MICROSECONDS.sleep(50);
            length=transferData(new byte[1],true)[0];
        }while(length==0 && System.currentTimeMillis()-timer<10);
        TimeUnit.MICROSECONDS.sleep(50);

        int[] result = transferData(new byte[length],true);

        return result;
    }
}
