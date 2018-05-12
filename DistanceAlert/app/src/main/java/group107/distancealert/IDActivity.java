package group107.distancealert;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.List;

import static group107.distancealert.MainActivity.TAG;

/*
    Classe in fase di test
    TODO testare se così comunque funziona
    TODO ottimizzare
    TODO gestire ordine dei RadioButtons devono rimanere nello stesso ordine altrimenti ad ogni secondo potrebbe cambiare
 */

public class IDActivity extends Activity {
    private DistanceController myController;
    static protected int id;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_id);

        //riferimento alla TextView che mostra la distanza ricevuta
        final LinearLayout idLayout = findViewById(R.id.idLayout);

        //Creazione di RadioGroup, ospiterà i RadioButtons
        //final Context appContext = getApplicationContext();
        final RadioGroup listIDsGroup = new RadioGroup(getApplicationContext());

        //sceglie canale di comunicazione UART o SPI
        try {
            myController = new DistanceController("SPI0.0");
        } catch (Exception e) {
            Log.e(TAG, "Errore:\n", e);
        }

        //Start polling
        myController.startUpdate(1000L);

        myController.addAllTagsListener(new AllTagsListener() {
            @Override
            public void onTagHasConnected(List<DistanceController.Entry> tags) {
                Log.i(TAG,"IDActivity, onTagHasConnected");

                //connessione con i moduli
                //quindi ricerca degli IDs e successiva stampa su schermo sottoforma di RadioButtons

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG,"IDActivity, onTagHasConnected, running thread");
                        List<Integer> ids = myController.getTagIDs();
                        final RadioButton[] item = new RadioButton[ids.size()];
                        for(int i = 0; i < ids.size(); i++) {
                            Log.i(TAG, "onTagHasConnected, ciclo for, i = " + i);
                            item[i] = new RadioButton(getApplicationContext());
                            final int singleId = ids.get(i);
                            item[i].setText(singleId);

                            //Thread specifico per gestire il Click di ogni singolo RadioButton
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    item[singleId].setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Log.i(TAG, "Thread lanciato per RadioButton " + item[singleId]);
                                            id = singleId;
                                            Intent toMainActivity = new Intent(getApplicationContext(), MainActivity.class);
                                            startActivity(toMainActivity);
                                        }
                                    });
                                }
                            }).start();
                            //Aggiunta del bottone in fondo alla lista
                            listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
                        }
                        //pubblicazione RadioGroup sul layout
                        idLayout.addView(listIDsGroup);
                    }
                });
            }

            @Override
            public void onTagHasDisconnected(List<DistanceController.Entry> tags) {
                Log.i(TAG,"IDActivity, onTagHasDisconnected");

                //connessione con i moduli
                //quindi ricerca degli IDs e successiva stampa su schermo sottoforma di RadioButtons

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG,"IDActivity, onTagHasDisconnected, running thread");
                        List<Integer> ids = myController.getTagIDs();
                        final RadioButton[] item = new RadioButton[ids.size()];
                        for(int i = 0; i < ids.size(); i++) {
                            Log.i(TAG, "onTagHasDisconnected, ciclo for, i = " + i);
                            item[i] = new RadioButton(getApplicationContext());
                            final int singleId = ids.get(i);
                            item[i].setText(singleId);

                            //Thread specifico per gestire il Click di ogni singolo RadioButton
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    item[singleId].setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Log.i(TAG, "Thread lanciato per RadioButton " + item[singleId]);
                                            id = singleId;
                                            Intent toMainActivity = new Intent(getApplicationContext(), MainActivity.class);
                                            startActivity(toMainActivity);
                                        }
                                    });
                                }
                            }).start();
                            //Aggiunta del bottone in fondo alla lista
                            listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
                        }
                        //pubblicazione RadioGroup sul layout
                        idLayout.addView(listIDsGroup);
                    }
                });
            }

            @Override
            public void onTagDataAvailable(List<DistanceController.Entry> tags) {
                Log.i(TAG,"IDActivity, onTagDataAvailable");

                //connessione con i moduli
                //quindi ricerca degli IDs e successiva stampa su schermo sottoforma di RadioButtons

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG,"IDActivity, onTagDataAvailable, running thread");
                        List<Integer> ids = myController.getTagIDs();
                        final RadioButton[] item = new RadioButton[ids.size()];
                        for(int i = 0; i < ids.size(); i++) {
                            Log.i(TAG, "onTagDataAvailable, ciclo for, i = " + i);
                            item[i] = new RadioButton(getApplicationContext());
                            final int singleId = ids.get(i);
                            item[i].setText(singleId);

                            //Thread specifico per gestire il Click di ogni singolo RadioButton
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    item[singleId].setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Log.i(TAG, "Thread lanciato per RadioButton " + item[singleId]);
                                            id = singleId;
                                            Intent toMainActivity = new Intent(getApplicationContext(), MainActivity.class);
                                            startActivity(toMainActivity);
                                        }
                                    });
                                }
                            }).start();
                            //Aggiunta del bottone in fondo alla lista
                            listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
                        }
                        //pubblicazione RadioGroup sul layout
                        idLayout.addView(listIDsGroup);
                    }
                });
            }
        });
    }

    @Override
    public void onPause() {
        //chiusura controller
        myController.close();

        //passaggio di stato
        super.onPause();
    }
}
