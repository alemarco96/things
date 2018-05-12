package group107.launchdefaultscreentest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {

    private static final String TAG = "TEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            @SuppressLint("PrivateApi")
            Class<?> c = Class.forName("com.android.iotlauncher.DefaultIoTLauncher");

            Intent intent = new Intent(this, c);
            startActivity(intent);
            finishAndRemoveTask();
        } catch (Throwable th) {
            Log.e(TAG, "\nErrore:\n", th);
        }

    }
}
