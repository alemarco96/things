package it.likenoother.myspi;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.things.pio.PeripheralManager;
import java.util.Arrays;
import com.google.android.things.pio.SpiDevice;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static java.lang.Byte.toUnsignedInt;

public class MainActivity extends Activity {
    private static final String SPI_DEVICE_NAME = "SPI0.0";
    private static SpiDevice mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView mTextView=findViewById(R.id.mytextview);
        final Button mButton=findViewById(R.id.mybutton);

        mButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mTextView.setText("working");

                String myLog="";
                myLog+=Arrays.toString(transferData(new byte[] {(byte)0x0c,(byte)0x00},false));
                mTextView.setText(myLog);

                int length;
                long timer =System.currentTimeMillis();
                do{
                    try {
                        TimeUnit.MICROSECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        mTextView.setText("ERR");
                        return;
                    }
                    length=(transferData(new byte[1],true)[0]);
                    myLog+="\n"+length;
                    mTextView.setText(myLog);
                }while(length==0 && System.currentTimeMillis()-timer<100);

                try {
                    TimeUnit.MICROSECONDS.sleep(100);
                } catch (InterruptedException e) {
                    mTextView.setText("ERR");
                    return;
                }

                int[] r = transferData(new byte[length],true);
                myLog+="\n"+Arrays.toString(r);

                long distance=r[26];
                distance = distance << 8;
                distance += r[25];
                distance = distance << 8;
                distance += r[24];
                distance = distance << 8;
                distance += r[23];

                myLog+="\nDistance: "+distance+"mm";

                mTextView.setText(myLog);
            }
        });
    }

    private int[] transferData(byte[] buffer, boolean autoFill){
        if(buffer.length<=0 || buffer.length==255){
            return null;
        }

        if(autoFill==true) {
            Arrays.fill(buffer, (byte) 0xff);
        }

        byte[] response = new byte[buffer.length];

        try {
            PeripheralManager manager = PeripheralManager.getInstance();
            mDevice = manager.openSpiDevice(SPI_DEVICE_NAME);
            mDevice.setMode(SpiDevice.MODE0);

            mDevice.setFrequency(8000000);
            mDevice.setBitsPerWord(8);
            mDevice.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);

            mDevice.transfer(buffer, response, buffer.length);

            mDevice.close();
        } catch (IOException e) {
            return null;
        }

        int[] intResponse=new int[response.length];
        for (int i=0; i<response.length; i++) {
            intResponse[i]=toUnsignedInt(response[i]);
        }

        return intResponse;
    }
}
