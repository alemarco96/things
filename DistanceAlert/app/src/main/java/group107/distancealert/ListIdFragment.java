package group107.distancealert;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Classe che implementa il "fragment" relativo alla lista degli id disponibili.
 */

public class ListIdFragment extends Fragment {
    protected static DistanceController mController;
    protected static final String TAG = "107G";
    protected static int dwmId;

    private RecyclerView mIdsListView;
    protected List<Integer> listIds;
    private idAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sceglie canale di comunicazione UART o SPI
        try {
            mController = new DistanceController("SPI0.0");
        } catch (Exception e) {
            Log.e(TAG, "Errore:\n", e);
        }
        //Start polling
        mController.startUpdate(1000L);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        //Operazioni di gestione del fragment
        View viewIdFragment = inflater.inflate(R.layout.fragment_ids_list, container,false);
        mIdsListView = (RecyclerView) viewIdFragment.findViewById(R.id.ids_recycler_view);
        mIdsListView.setLayoutManager(new LinearLayoutManager(getActivity()));

        //Implementazione dei listeners
        mController.addAllTagsListener(new AllTagsListener() {
            @Override
            public void onTagHasConnected(List<DistanceController.Entry> tags) {
                Log.i(TAG, "IdFragment -> onTagHasConnected");
                updateViewList(mController.getTagIDs());
            }

            @Override
            public void onTagHasDisconnected(List<DistanceController.Entry> tags) {
                Log.i(TAG, "IdFragment -> onTagHasDisconnected");
                updateViewList(mController.getTagIDs());
            }

            @Override
            public void onTagDataAvailable(List<DistanceController.Entry> tags) {
                Log.i(TAG, "IdFragment -> onTagHasDataAvailable");
            }
        });

        return viewIdFragment;
    }

    /**
     * Metodo utile all'aggiornamento della lista degli "ids" disponibili.
     * @param tagIds La lista con gli "ids" disponibili
     */
    private void updateViewList(final List<Integer> tagIds){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listIds = tagIds;
                mAdapter = new idAdapter(listIds);
                mIdsListView.setAdapter(mAdapter);
            }
        });
    }

    /**
     * Classe che implementa la parte "Holder" di "RecyclerView".
     * "RecyclerView" serve per manipolare e gestire una lista di "View", in particolare una lista
     * di "holders"
     */
    private class idHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView mIdNameTextView;
        private int mDwmId;

        public idHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.list_item_id, parent, false));
            itemView.setOnClickListener(this);
            mIdNameTextView = (TextView) itemView.findViewById(R.id.itemId);
        }

        public void bind(int id) {
            mDwmId = id;
            Log.i(TAG, "idHolder -> bind id = " + Integer.toString(id));
            mIdNameTextView.setText(Integer.toString(id));

        }

        /*
            @TODO rendere generale questo onClick (pag. 1152 libro bignerdranch)
         */
        @Override
        public void onClick(View view){
            Log.i(TAG, "idHolder -> onClick");
            dwmId = mDwmId;
            //Intent intentDwmActivity = new Intent(getActivity(), DwmActivity.class);
            //startActivity(intentDwmActivity);
            Fragment fragment = DwmFragment.newInstance();
            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
            fragmentManager.beginTransaction().add(R.id.detail_fragment_container, fragment).commit();
        }
    }

    /**
     * Classe utile per creare gli "holders" che servono (uno per ogni id rilevato)
     */
    private class idAdapter extends RecyclerView.Adapter<idHolder>{
        private List<Integer> mListIds;

        public idAdapter (List<Integer> ids) {
            mListIds = ids;
        }

        @Override
        public idHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            return new idHolder(layoutInflater, parent);
        }

        @Override
        public void onBindViewHolder(idHolder holder,int index){
            int id = mListIds.get(index);
            holder.bind(id);
        }

        @Override
        public int getItemCount() {
            return mListIds.size();
        }

    }

}
