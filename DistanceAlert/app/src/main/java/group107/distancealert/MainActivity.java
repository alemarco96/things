package group107.distancealert;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//TODO possibile ottimizzazione codice riguardante aggiornamento lista IDs
/*--> onTagHasConnected(List<DistanceController.Entry> tags) : tags è la lista dei tags appena
connessi
--> onTagHasDisconnected(List<DistanceController.Entry> tags) : tags è la lista dei tags appena
disconnessi
*/
public class MainActivity extends Activity {
    public static final String TAG = "107G";

    private static final String GPIO_PULSANTE = "BCM26";
    private static final String RPI3_UART = "MINIUART";
    private static final String RPI3_SPI = "SPI0.0";

    private Gpio pulsante;
    private DistanceController myController;
    private int id = -1;
    private int maxDistance = 2000;
    private List<RadioButton> item = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //riferimento alla TextView che mostra la distanza ricevuta
        final LinearLayout idLayout = findViewById(R.id.idLayout);
        //Creazione di RadioGroup, ospiterà i RadioButtons
        final RadioGroup listIDsGroup = new RadioGroup(getApplicationContext());
        //Collegamento RadioGroup al LinearLayout ospitante
        idLayout.addView(listIDsGroup);

        //riferimento alla TextView che mostra l'ID al quale si è connessi.
        final TextView connectedToId = findViewById(R.id.connectedTo_id);
        //riferimento alla TextView che mostra la distanza ricevuta
        final TextView distanceView = findViewById(R.id.distance);

        //TextView che mostra la distanza limite
        final TextView maxDistanceView = findViewById(R.id.maxDistance);
        String defaultMaxDistance = maxDistance/1000 + "." + maxDistance%1000 + " m";
        maxDistanceView.setText(defaultMaxDistance);
        //Bottone che aumenta la distanza limite
        Button plusMaxDistanceButton = findViewById(R.id.plusMaxDistance);
        plusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"MainActivity -> plusMaxDistanceButton -> onClick");
                if (maxDistance <= 90500) {
                    maxDistance += 500;
                    final String newMaxDistance = (maxDistance / 1000) + "." + (maxDistance % 1000)
                            +" m";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            maxDistanceView.setText(newMaxDistance);
                        }
                    });
                }
            }
        });
        //Bottone che diminuisce la distanza limite
        Button minusMaxDistanceButton = findViewById(R.id.minusMaxDistance);
        minusMaxDistanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"MainActivity -> minusMaxDistanceButton -> onClick");
                if (maxDistance >= 500) {
                    maxDistance -= 500;
                    final String newMaxDistance = (maxDistance / 1000) + "." + (maxDistance % 1000)
                            + " m";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            maxDistanceView.setText(newMaxDistance);
                        }
                    });
                }
            }
        });

        try {
            //sceglie canale di comunicazione UART ("MINIUART") o SPI ("SPI0.0")
            myController = new DistanceController(RPI3_SPI);//TODO pulsante per passare da spi a uart
            //Start polling
            myController.startUpdate(300L);

            /*Connessione ai listeners generali per creare lista di IDs rilevati
            visualizzabile su schermo e completa di bottoni per la visione dei dati relativi
            allo specifico id selezionato */
            new Thread(new Runnable() {
                @Override
                public void run() {
                    myController.addAllTagsListener(new AllTagsListener() {
                        @Override
                        public void onTagHasConnected(List<DistanceController.Entry> tags) {
                            Log.i(TAG,"MainActivity -> addAllTagListener ->" +
                                    " onTagHasConnected: tags.sixe() = " + tags.size());
                            //almeno un Tag si è appena connesso, rigenerazione lista IDs
                            //regenerateRagioGroup(listIDsGroup, idLayout, distanceView,
                            //        connectedToId);

                            //aggiunga tags appena connessi
                            for(int i = 0; i < tags.size(); i++) {
                                item.add(new RadioButton(getApplicationContext()));
                                final String textItem = Integer.toHexString(tags.get(i).tagID);
                                item.get(i).setText(textItem);
                                final int singleId = tags.get(i).tagID;
                                final int finalI = i;
                                item.get(i).setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Log.i(TAG, "MainActivity -> addAllTagListener ->" +
                                                " onTagHasConnected -> item.get(i = " + finalI +
                                                ").setOnClickListener: id = " + textItem);
                                        id = singleId;
                                        connectToSpecificListener(distanceView, connectedToId);
                                    }
                                });
                                if(id == singleId) {
                                    Log.i(TAG,"MainActivity ->" +
                                        " addAllTagListener ->" +
                                        " onTagHasConnected: ciclo for, i = " + i +
                                        ", RadioButton toggled: (id = " + Integer.toHexString(id) +
                                        ") == (singleid = " + Integer.toHexString(singleId) + ")");
                                    item.get(i).toggle();
                                }
                            }

                        }

                        @Override
                        public void onTagHasDisconnected(List<DistanceController.Entry> tags) {
                            Log.i(TAG,"MainActivity -> addAllTagListener" +
                                    " -> onTagHasDisconnected: tags.size() = " + tags.size());
                            //almeno un Tag si è appena disconnesso, rigenerazione lista IDs
                            //regenerateRagioGroup(listIDsGroup, idLayout, distanceView,
                            //        connectedToId);

                            //rimozione tags appena disconnessi
                            for(int i = 0; i < tags.size(); i ++) {
                                for(int j = 0; j < item.size(); j++) {
                                    Log.i(TAG, "MainActivity -> addAllTagListener" +
                                            " -> onTagHasDisconnected: i = " + i + ", j = " + j);
                                    if (Integer.toHexString(tags.get(i).tagID)
                                            == item.get(j).getText()) {
                                        Log.i(TAG, "MainActivity -> addAllTagListener" +
                                                " -> onTagHasDisconnected" +
                                                "(Integer.toHexString(tags.get(i).tagID) = " +
                                                Integer.toHexString(tags.get(i).tagID) +
                                                ") == (item.get(j).getText() = " +
                                                item.get(j).getText() + ")");
                                        item.remove(j);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onTagDataAvailable(List<DistanceController.Entry> tags) {
                            Log.i(TAG,"MainActivity -> addAllTagListener" +
                                    " -> onTagDataAvailable");
                            //dato inviato dai tag, lista IDs invariata
                        }
                    });
                }
            }).start();
        } catch (java.io.IOException | InterruptedException e) {
            Log.e(TAG, "MainActivity -> onCreate: Errore:\n", e);
            /*Generata un'eccezione al momento della creazione dell'instanza DistanceController
            quindi lo notifico sullo schermo utilizzato dall'utente*/
            connectedToId.setText(R.string.noDwm);
        }


        PeripheralManager manager = PeripheralManager.getInstance();//todo mettilo pure dove ti piace di più
        try {
            pulsante = manager.openGpio(GPIO_PULSANTE);
            pulsante.setActiveType(Gpio.ACTIVE_HIGH);
            pulsante.setDirection(Gpio.DIRECTION_IN);
        } catch (IOException e) {
            //todo
        }
    }

    /**
     * Rigenera la lista che gestisce gli IDs disponibili
     * @param listIDsGroup RadioGroup da aggiornare
     * @param idLayout LinearLayout contenente il RadioGroup da aggiornare
     * @param distanceView La TextView dove viene visualizzato il dato di distanza rilevato
     * @param connectedToId La TextView dove viene visualizzato l'ID del modulo a distanza
     *                      "distanceView"
     */
    private void regenerateRagioGroup(final RadioGroup listIDsGroup, final LinearLayout idLayout,
                                      final TextView distanceView, final TextView connectedToId){
        Log.i(TAG, "MainActivity -> regenerateRadioGroup");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG,"MainActivity -> regenerateRadioGroup: running thread");
                //ricezione IDs connessi
                int[] ids = myController.getTagIDs();
                //creazione di Array contentente i RadioGroups
                final RadioButton[] item = new RadioButton[ids.length];
                //pulizia RadioGroup ospitante i RadioButtons
                listIDsGroup.removeAllViews();
                //popolazione dell'array di RadioButtons
                for(int i = 0; i < ids.length; i++) {
                    Log.i(TAG, "MainActivity -> regenerateRadioGroup: ciclo for, i = " + i);
                    item[i] = new RadioButton(getApplicationContext());
                    final int singleId = ids[i];
                    final String idText = Integer.toHexString(singleId);
                    item[i].setText(idText);

                    //Click specifico di ogni singolo RadioButton
                    item[i].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.i(TAG, "MainActivity -> regenerateRadioGroup:"
                                    + " onClick " + idText);
                            id = singleId;
                            connectToSpecificListener(distanceView, connectedToId);
                        }
                    });
                    //Controllo se il bottone era stato premuto in precedenza
                    if(id != -1 && id == singleId) {
                        Log.i(TAG, "MainActivity -> regenerateRadioGroup:" +
                                " ciclo for, i = " + i + ", RadioButton toggled: (id = " + id +
                                ") == (singleid = " + singleId + ")");
                        item[i].toggle();
                    }
                    //Aggiunta del bottone in fondo alla lista
                    listIDsGroup.addView(item[i], -1, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                //pulizia scorso idLayout, ospitante il RadioGroup
                idLayout.removeAllViews();
                //pubblicazione RadioGroup su idlayout
                idLayout.addView(listIDsGroup);
            }
        });
    }

    /**
     * Connessione ad un id selezionato il quale invia il dato della distanza
     * @param distanceView La TextView dove viene visualizzato il dato di distanza rilevato
     * @param connectedToId La TextView dove viene visualizzato l'ID del modulo a distanza
     *                      "distanceView"
     */
    private void connectToSpecificListener(final TextView distanceView,
                                           final TextView connectedToId) {
        Log.i(TAG, "MainActivity -> connectToSpecificListener: connectedToId = "
                + connectedToId);
        //visualizzazione a schermo dell'ID al quale si è connessi
        String idText = Integer.toHexString(id);
        connectedToId.setText(idText);
        //collegamento a listeners di un solo tag id
        myController.addTagListener(id, new TagListener() {
            @Override
            public void onTagHasConnected(final int tagDistance) {
                Log.i(TAG, "MainActivity -> connectToSpecificListener -> addTagListener" +
                        " -> onTagHasConnected: Connesso a " + Integer.toHexString(id));
                setDistanceText(tagDistance, distanceView);
            }

            @Override
            public void onTagHasDisconnected(final int tagLastKnownDistance) {
                Log.i(TAG, "MainActivity -> connectToSpecificListener -> addTagListener" +
                        " -> onTagHasDisconnected: disconnesso id = " + Integer.toHexString(id));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        distanceView.setText(R.string.noConnection);
                    }
                });
            }

            @Override
            public void onTagDataAvailable(final int tagDistance) {
                Log.i(TAG, "MainActivity -> connectToSpecificListener -> addTagListener" +
                        " -> onTagDataAvailable: id = " + Integer.toHexString(id) +
                        ", tagDistance = " + tagDistance);
                if (tagDistance > maxDistance) {
                    distanceAlarm();//todo migliorare implementazione?
                }
                setDistanceText(tagDistance, distanceView);
            }
        });
    }

    /**
     * Aggiorna la View con la distanza ricevuta
     * @param tagDistance distanza ricevuta
     * @param distanceView TextView dove aggiornare la distanza
     */
    private void setDistanceText (final int tagDistance, final TextView distanceView) {
        Log.i(TAG, "MainActivity -> setDistanceText: id = " + id + ", tagDistance = "
                + tagDistance);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String newText =    (tagDistance/1000) +
                        "." + (tagDistance%1000) + " m";
                distanceView.setText(newText);
            }
        });
    }

    /**
     * Restituisce l'id ai quali si è connessi
     * @return id variabile di tipo int rappresentante l'id ai quali si è connessi
     */
    protected int getActualId() {
        return id;
    }

    /**
     * Restituisce la distanza massima impostata
     * @return maxDistance variabile di tipo int rappresentante la distanza massima
     */
    protected int getActualMaxDistance() {
        return maxDistance;
    }

    /*TODO controlla questo metodo*/
    private void distanceAlarm() {
        try {
            final DistanceAlarm myAlarm = new DistanceAlarm();
            myAlarm.start();
            pulsante.setEdgeTriggerType(Gpio.EDGE_RISING);

            pulsante.registerGpioCallback(/*todo runtime exception create handler inside thread that has not called Looper.prepare() */
                    /*new Handler(),*/
                    new GpioCallback() {
                        @Override
                        public boolean onGpioEdge(Gpio gpio) {
                            try {
                                myAlarm.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return false;
                        }
                    }
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "MainActivity -> onDestroy");
        //passaggio di stato
        super.onDestroy();

        if (myController != null) {
            Log.i(TAG, "MainActivity -> onDestroy -> chiusura controller");
            //chiusura controller
            myController.close();
        }
    }
}
