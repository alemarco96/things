package it.likenoother.rictestspi;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Byte.toUnsignedInt;

public class MainActivity extends Activity {
    // SPI Device Name
    private static final String SPI_DEVICE_NAME = "SPI0.0";
    private SpiDevice mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final TextView mTextView=findViewById(R.id.mytextview);
        final Button mButton=findViewById(R.id.mybutton);

        mButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mTextView.setText("working");
/*
                String myLog="";
                myLog+=transferData(new byte[] {(byte)0x0c,(byte)0x00},false).toString();
                mTextView.setText(myLog);

                int length;
                long timer =System.currentTimeMillis();
                do{
                    try {
                        TimeUnit.MICROSECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    length=(transferData(new byte[1],true)[0]);
                    myLog+="\n\n"+length;
                    mTextView.setText(myLog);
                }while(length==0 && System.currentTimeMillis()-timer<100);

                try {
                    TimeUnit.MICROSECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                myLog+="\n\n"+transferData(new byte[length],true).toString();

                mTextView.setText(myLog);*/
            }
        });
    }
/*
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
*/
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
