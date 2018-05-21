package group107.distancealert;

import android.support.v4.app.Fragment;

/**
 * Classe ospitante il "fragment" relativo al modulo DWM.
 * Una volta selezionato un id, in tale fragment verranno vistualizzati i dati ricevuti.
 */

public class DwmActivity extends SingleFragmentActivity {

    @Override
    protected Fragment createFragment() {
        return DwmFragment.newInstance();
    }
}
