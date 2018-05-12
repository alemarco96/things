package group107.testpietro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static group107.testpietro.MainActivity.TAG;
import static group107.testpietro.MainActivity.id;

public class PostActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        final TextView msg = findViewById(R.id.msgPost);
        msg.setText("TestOk, id = " + MainActivity.id);

        final Button connectTo = findViewById(R.id.connect);
        connectTo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Click eseguito in PostActivity");
                Intent n = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(n);
            }
        });
    }
}
