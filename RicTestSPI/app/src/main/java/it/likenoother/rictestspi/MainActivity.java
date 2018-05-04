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

public class MainActivity extends Activity {
    // SPI Device Name
    private static final String TAG="MainActivity";
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
                String myLog="";
                myLog+=transferData(new byte[] {0x0c,0x00}).toString();

                byte length;
                long timer =System.currentTimeMillis();
                do{
                   //attenzione alle conversioni per la lunghezza!!!
                   length=transferData(new byte[] {-128})[0];
                }while(length==0 && System.currentTimeMillis()-timer<100);

                //dovresti creare un buffer di tutti 0xff
                myLog+=transferData(new byte[length]).toString();

                mTextView.setText(myLog);
            }
        });
    }

    private byte[] transferData(byte[] buffer){
        if(buffer.length<=0 || buffer.length==255){
            return null;
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
            Log.w(TAG, "Unable to access SPI device", e);
        }

        return response;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mDevice != null) {
            try {
                mDevice.close();
                mDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close SPI device", e);
            }
        }
    }
}
