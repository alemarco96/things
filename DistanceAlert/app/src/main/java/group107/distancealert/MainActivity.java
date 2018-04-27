package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    Dwm dwm;
    int id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dwm = new Dwm("SPI0.0");

    }
}
