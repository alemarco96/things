package group107.testpietro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class MainActivity extends Activity {
    public static String TAG = "TestPietro";
    protected static int id;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context appContext = getApplicationContext();
        final LinearLayout idLayout = findViewById(R.id.idLayout);
        final RadioGroup listIDsGroup = new RadioGroup(appContext);
        int j = 15;
        for (int i = 0; i < j; i++) {
            Log.i(TAG, "Entrato nel ciclo for con i = " + i);
            final RadioButton[] item = new RadioButton[j];
            item[i] = new RadioButton(appContext);
            item[i].setText("Opzione " + i);
            final int iT = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, iT + " Thread lanciato");
                    item[iT].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG, iT + " click eseguito");
                            id = iT;
                            Intent n = new Intent(getApplicationContext(), PostActivity.class);
                            startActivity(n);
                        }
                    });
                }
            }).start();
            listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        idLayout.addView(listIDsGroup);

    }
}
