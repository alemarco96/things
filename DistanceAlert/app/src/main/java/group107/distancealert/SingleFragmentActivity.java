package group107.distancealert;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import static group107.distancealert.ListIdFragment.TAG;


/**
 * Classe astratta utile per la creazione dei "fragments" è infatti utilizzatta da tutte le "activities"
 * le quali ospitano almeno un "fragment"
 */

public abstract class SingleFragmentActivity extends AppCompatActivity {
    protected abstract Fragment createFragment();

    @LayoutRes
    protected int getLayoutResId() {
        Log.i(TAG, "SingleFragmentActivity -> getLayoutResId");
        return R.layout.activity_fragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceStete){
        Log.i(TAG, "SingleFragmentActivity -> onCreate");

        super.onCreate(savedInstanceStete);
        setContentView(getLayoutResId());
        Log.i(TAG, "SingleFragmentActivity -> onCreate: super.onCreate e setContentView eseguiti");

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);
        Log.i(TAG, "SingleFragmentActivity -> onCreate: Impostazione fragment terminate");

        if (fragment == null) {
            Log.i(TAG, "SingleFragmentActivity -> onCreate: fragment == null");
            fragment = createFragment();
            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
            Log.i(TAG, "SingleFragmentActivity -> onCreate: commit del fragment eseguito");
        }
    }
}
