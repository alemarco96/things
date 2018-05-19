package group107.distancealert;

import android.support.v4.app.Fragment;

/**
 * Classe ospitante il "fragment" che mostra la lista degli "ids" rilevati e ne permette la scelta
 * di uno di essi per la successiva visualizzazione dei dati rilevati tramite quell'id.
 */

public class ListIdActivity extends SingleFragmentActivity {

    @Override
    protected Fragment createFragment() {
        return new ListIdFragment();
    }
}
