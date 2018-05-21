package group107.distancealert;

import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.util.Log;

import static group107.distancealert.ListIdFragment.TAG;


/**
 * Classe ospitante il "fragment" che mostra la lista degli "ids" rilevati e ne permette la scelta
 * di uno di essi per la successiva visualizzazione dei dati rilevati tramite quell'id.
 */

public class ListIdActivity extends SingleFragmentActivity {

    @Override
    protected Fragment createFragment() {
        Log.i(TAG, "ListIdActivity -> createFragment");
        return new ListIdFragment();
    }

    @LayoutRes
    protected int getLayoutResId() {
        Log.i(TAG, "ListIdActivity -> getLayoutResId");
        return R.layout.activity_master;
    }

}
