package group107.distancealert;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

public class DistanceAlert extends SingleFragmentActivity {

    @Override
    protected Fragment createFragment(){
        return new DwmFragment();
    }
}
