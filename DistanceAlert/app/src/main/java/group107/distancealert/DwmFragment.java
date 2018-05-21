package group107.distancealert;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import static group107.distancealert.ListIdFragment.mController;
import static group107.distancealert.ListIdFragment.TAG;
import static group107.distancealert.ListIdFragment.dwmId;

/**
 * Classe che implementa il "fragment" relativo alla visualizzazione dei dati rilevati dal "DWM" in
 * relazione ad uno specifico "id"
 */

public class DwmFragment extends Fragment {

    private boolean alert;
    private int maxUserDistance;

    public static DwmFragment newInstance(){
        DwmFragment fragment = new DwmFragment();
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState){
        Log.i(TAG, "DwmFragment -> OnCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        Log.i(TAG, "DwmFragment -> OnCreateView");

        View viewDwmFragment = inflater.inflate(R.layout.fragment_dwm, container,false);

        final TextView connectedTo = viewDwmFragment.findViewById(R.id.connectedTo);
        connectedTo.setText(R.string.connected_to + dwmId);

        final TextView distanceView = viewDwmFragment.findViewById(R.id.distance);

        mController.addTagListener(dwmId, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG, "DwmFragment -> OnCreateView -> onTagHasConnected");
                viewDistances(distanceView, tagDistance);
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(TAG, "DwmFragment -> OnCreateView -> onTagHasDisconnected");
                distanceView.setText(R.string.noConnection);
            }

            @Override
            public void onTagDataAvailable(final int tagDistance) {
                Log.i(TAG, "DwmFragment -> OnCreateView -> onTagDataAvailable");
                viewDistances(distanceView, tagDistance);
            }
        });

        return viewDwmFragment;
    }

    /**
     * Metodo utile per l'aggiornamento della "TextView" che mostra la distanza rilevata
     * @param distanceView Riferimento alla "TextView"
     * @param tagDistance Distanza rilevata
     */

    private void viewDistances(final TextView distanceView, final int tagDistance){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String newText =    getString(R.string.distance) +
                                    " " + (tagDistance/1000) +
                                    "." + (tagDistance%1000);
                distanceView.setText(newText);
            }
        });
    }
}
