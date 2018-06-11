package group107.distancealert;

import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static group107.distancealert.MainActivity.TAG;

/**
 * Classe che rappresenta un sensore di distanza.
 */
public class DistanceController
{
    //numero di byte usati dal modulo DWM per descrivere i dati relativi a ciascun tag
    private static final int BYTES_PER_ENTRY = 20;
    //numero di volte per cui ricevendo gli stessi dati dal modulo DWM, un tag viene dichiarato disconnesso
    private static final int COUNTER_FOR_DISCONNECTED = 3;
    //numero di volte per cui avvenendo un errore nella comunicazione con il modulo DWM, dichiara disconnessi tutti i tag e stoppa il polling
    private static final int COUNTER_FOR_CONNECTION_ERRORS = COUNTER_FOR_DISCONNECTED;
    //periodo minimo di aggiornamento a cui può essere settato il controller (<= 10 Hz)
    private static final long MINIMUM_UPDATE_PERIOD = 100L;

    /**
     * Oggetto che serve per ordinare in ordine crescente per tagID le entry
     */
    private Comparator<Entry> MATCHING_ID_ENTRY_COMPARATOR = new Comparator<Entry>()
    {
        @Override
        public int compare(Entry e1, Entry e2)
        {
            return e1.tagID - e2.tagID;
        }
    };

    /**
     * Classe che rappresenta una coppia id-distanza. E' implementata in modo tale da essere un oggetto immutabile
     */
    public static class Entry
    {
        public final int tagID;
        public final int tagDistance;
        private int counter;

        /**
         * Crea una nuova entry con id del tag e distanza specificata per parametro
         * @param id L'id del tag a cui è associata l'entry
         * @param distance La distanza misurata
         */
        private Entry(int id, int distance)
        {
            tagID = id;
            tagDistance = distance;
            counter = 0;
        }

        /**
         * Crea una copia dell'entry passata per parametro
         * @param e Entry da copiare
         */
        private Entry(Entry e)
        {
            tagID = e.tagID;
            tagDistance = e.tagDistance;
            counter = e.counter;
        }

        /**
         * Effettua un confronto per stabilire se l'entry passata per parametro è uguale, sia come id del tag che come distanza misurata
         * @param obj L'altra entry con cui effettuare il confronto
         * @return true se le due entry sono uguali, altrimenti false
         */
        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Entry))
                return false;

            Entry entry = (Entry) obj;
            return (tagID == entry.tagID) && (tagDistance == entry.tagDistance);
        }

        /**
         * Ottiene una rappresentazione testuale dell'entry
         * @return Una stringa che rappresenta l'entry
         */
        @Override
        public String toString()
        {
            return "ID: " + tagID + "  Distanza: " + tagDistance / 1000 + "." + tagDistance % 1000;
        }
    }

    /**
     * Clona la lista di entry id-distanza passata per argomento. Viene effettuata una copia dei dati
     * @param source La lista con i dati da copiare
     * @return Una lista copia di quella passata per parametro
     */
    private static List<Entry> cloneList(List<Entry> source)
    {
        List<Entry> dest = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++)
        {
            dest.add(new Entry(source.get(i)));
        }
        return dest;
    }

    /**
     * Logga tutte le entry presenti nella lista, anteponendoci un messaggio
     * @param tag Il tag con cui fare il log
     * @param message Il messaggio da anteporre ai dati
     * @param separator Una stringa usata per separare visivamente i dati
     * @param data Lista con le entry da loggare
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored", "SameParameterValue"})
    private static void logEntryData(String tag, String message, String separator, List<Entry> data)
    {
        if (data == null || data.size() == 0)
        {
            Log.d(tag, message + "<nessuno>");
            return;
        }

        String result = "" + message;

        for (int i = 0; i < data.size(); i++)
            result.concat(data.get(i).toString() + separator);

        Log.d(tag, result);
    }

    //memorizza i dati attuali
    private List<Entry> actualData;
    //memorizza i dati dei tag che sono disconnessi
    private List<Entry> disconnectedData;

    //memorizza tutti i listeners associati a tutti i tag
    private List<AllTagsListener> allListeners;
    //memorizza tutti i listeners associati ad uno specifico tag
    private List<Pair<Integer, TagListener>> tagListeners;

    //oggetto usato per gestire l'accesso in mutua esclusione ai dati
    private final Object dataLock = new Object();
    //oggetto usato per gestire l'accesso in mutua esclusione ai listeners
    private final Object listenersLock = new Object();

    //contatore degli errori di comunicazione con il modulo DWM
    private int connectionErrors = 0;

    //oggetto che gestisce la comunicazione a basso livello con il modulo DWM
    private DriverDWM driverDWM;

    //oggetto che, usando un thread secondario che autogestisce la sua schedulazione periodica, si occupa di effettuare
    //il polling del modulo DWM per ottenere le distanze
    private Timer updateDataTimer;

    /**
     * Oggetto che definisce la routine di aggiornamento periodica
     */
    private TimerTask updateDataTask = new TimerTask()
    {
        @Override
        public void run()
        {
            try
            {
                List<Entry> data = updateData();
                classifyDataAndNotify(data);
                synchronized (dataLock)
                {
                    actualData = data;
                }
                connectionErrors = 0;
            } catch (Throwable e)
            {
                Log.e(MainActivity.TAG, "Avvenuta eccezione in updateDataTask", e);
                connectionErrors++;
                if (connectionErrors >= COUNTER_FOR_CONNECTION_ERRORS)
                {
                    List<Entry> data;
                    synchronized (dataLock)
                    {
                        data = cloneList(actualData);
                    }
                    synchronized (listenersLock)
                    {
                        //notifica a tutti i listeners che tutti i tag che erano connessi all'ultimo aggiornamento si sono disconnessi

                        notifyToAllTagsListeners(null, data, null);
                        notifyToTagsListeners(new ArrayList<Entry>(), data, new ArrayList<Entry>());
                    }

                    //stoppa il thread di aggiornamento
                    stopUpdate();

                    Log.e(TAG, "*** Troppi errori di comunicazione avvenuti. Stop dell'aggiornamento del controller. ***");
                }
            }
        }
    };

    /**
     * Recupera le informazioni dal pacchetto di risposta del modulo DWM
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La lista di coppie id-distanza dei tag
     * @throws IllegalArgumentException Se i dati ricevuti dal modulo DWM non sono validi
     */
    private List<Entry> getDataFromDWMResponse(int[] dwmResponse) throws IllegalArgumentException
    {
        if (dwmResponse == null || dwmResponse.length < 21 || dwmResponse[2] != 0)
        {
            //dati non validi
            throw new IllegalArgumentException("Dati ricevuti dal modulo non validi.");
        }

        int numberOfValues = dwmResponse[20];

        List<Entry> newData = new ArrayList<>(numberOfValues);
        int startIndex = 21;

        //Nota che il modulo DWM usa notazione Little Endian!
        for (int i = 0; i < numberOfValues; i++, startIndex += BYTES_PER_ENTRY)
        {
            int id = (dwmResponse[startIndex + 1] << 8) + dwmResponse[startIndex];

            int d1 = dwmResponse[startIndex + 2];
            int d2 = dwmResponse[startIndex + 3];
            int d3 = dwmResponse[startIndex + 4];
            int d4 = dwmResponse[startIndex + 5];

            int distance = (d4 << 24) + (d3 << 16) + (d2 << 8) + d1;

            newData.add(new Entry(id, distance));
        }

        return newData;
    }

    /**
     * Ottiene i nuovi dati dal modulo DWM
     * @return I nuovi dati dal modulo DWM
     * @throws IOException Se avviene un errore di comunicazione con il modulo DWM
     */
    private List<Entry> updateData() throws IOException
    {
        int[] dwmResponse = driverDWM.requestAPI((byte) 0x0C, null);

        List<Entry> newData = getDataFromDWMResponse(dwmResponse);

        logEntryData(TAG, "\nDati dal modulo:\n", "\n", newData);

        //ordina gli elementi per tagID crescente, in modo tale da velocizzare le operazioni successive
        Collections.sort(newData, MATCHING_ID_ENTRY_COMPARATOR);

        //elimina i dati dei tag disconnessi
        for (int i = 0; i < disconnectedData.size(); i++)
        {
            Entry discEntry = disconnectedData.get(i);

            int result = Collections.binarySearch(newData, discEntry, MATCHING_ID_ENTRY_COMPARATOR);
            if (result >= 0)
            {
                //dato dello stesso tag presente
                Entry newEntry = newData.get(result);

                if(newEntry.tagDistance == discEntry.tagDistance)
                {
                    //tag disconnesso. eliminare entry dai dati attuali
                    newData.remove(result);
                } else
                {
                    //tag prima disconnesso, ed ora si è riconnesso. rimuovere dalla lista dei tag disconnessi
                    //la sua riconnessione verrà riconosciuta al prossimo passo
                    disconnectedData.remove(i);
                    i--;
                }
            }
        }

        return newData;
    }

    /**
     * Classifica i dati ottenuti dal modulo DWM e notifica ai listener
     * @param newData I nuovi dati ottenuti dal modulo
     */
    private void classifyDataAndNotify(List<Entry> newData)
    {
        List<Entry> updated;
        List<Entry> connected = new ArrayList<>();
        List<Entry> disconnected = new ArrayList<>();

        synchronized (dataLock)
        {
            updated = new ArrayList<>(newData.size() > actualData.size() ? newData.size() : actualData.size());

            for (int i = 0; i < newData.size(); i++)
            {
                Entry newEntry = newData.get(i);

                //ricerca di entry in actualData con lo stesso tagID
                int result = Collections.binarySearch(actualData, newEntry, MATCHING_ID_ENTRY_COMPARATOR);
                if (result >= 0)
                {
                    Entry actualEntry = actualData.get(result);

                    if (newEntry.tagDistance == actualEntry.tagDistance)
                    {
                        //tag appena disconnesso
                        newEntry.counter = actualEntry.counter + 1;
                        if (newEntry.counter >= COUNTER_FOR_DISCONNECTED)
                        {
                            disconnected.add(new Entry(newEntry));
                            disconnectedData.add(new Entry(newEntry));
                        }
                        else
                        {
                            updated.add(new Entry(newEntry));
                        }
                    } else
                    {
                        newEntry.counter = 0;
                        updated.add(new Entry(newEntry));
                    }
                } else
                {
                    //tag appena connesso
                    newEntry.counter = 0;
                    connected.add(new Entry(newEntry));
                }
            }

            Collections.sort(disconnectedData, MATCHING_ID_ENTRY_COMPARATOR);

            logEntryData(TAG, "\nTag appena connessi: " + connected.size() + "\n", "\n", connected);
            logEntryData(TAG, "\nTag appena disconnessi: " + disconnected.size() + "\n", "\n", disconnected);
            logEntryData(TAG, "\nTag ancora connessi: " + updated.size() + "\n", "\n", updated);
        }

        synchronized (listenersLock)
        {
            notifyToAllTagsListeners(connected, disconnected, updated);

            notifyToTagsListeners(connected, disconnected, updated);
        }
    }

    /**
     * Notifica a tutti i listener globali
     * @param connected Lista di entry dei tag appena connessi
     * @param disconnected Lista di entry dei tag appena disconnessi
     * @param updated Lista di entry dei tag con dati aggiornati
     */
    private void notifyToAllTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> updated)
    {
        //lock già ottenuto

        for (final AllTagsListener listener:allListeners)
        {
            if (connected != null && connected.size() > 0)
            {
                //presenti tags connessi nell'ultimo aggiornamento dei dati
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        //notifica su thread separato
                        listener.onTagHasConnected(connected);
                    }
                }).start();
            }

            if (disconnected != null && disconnected.size() > 0)
            {
                //presenti tags disconnessi nell'ultimo aggiornamento dei dati
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        //notifica su thread separato
                        listener.onTagHasDisconnected(disconnected);
                    }
                }).start();
            }

            if (updated != null && updated.size() > 0)
            {
                //notifica i nuovi valori
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        //notifica su thread separato
                        listener.onTagDataAvailable(updated);
                    }
                }).start();
            }
        }
    }

    /**
     * Notifica a tutti i listener specifici per un tag, se sono avvenuti degli eventi su quel tag
     * @param connected Lista di entry dei tag appena connessi
     * @param disconnected Lista di entry dei tag appena disconnessi
     * @param updated Lista di entry dei tag con dati aggiornati
     */
    private void notifyToTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> updated)
    {
        //lock già ottenuto

        for (Pair<Integer, TagListener> pair:tagListeners)
        {
            int ID = pair.first;
            final TagListener listener = pair.second;

            //connected, disconnected e data sono ordinati, per costruzione
            final int r1 = Collections.binarySearch(connected, new Entry(ID, -1), MATCHING_ID_ENTRY_COMPARATOR);
            if (r1 >= 0)
            {
                //il tag si è appena connesso
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        listener.onTagHasConnected(connected.get(r1).tagDistance);
                    }
                }).start();

                continue;
            }

            final int r2 = Collections.binarySearch(disconnected, new Entry(ID, -1), MATCHING_ID_ENTRY_COMPARATOR);
            if (r2 >= 0)
            {
                //il tag si è appena disconnesso
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        listener.onTagHasDisconnected(disconnected.get(r2).tagDistance);
                    }
                }).start();

                continue;
            }

            final int r3 = Collections.binarySearch(updated, new Entry(ID, -1), MATCHING_ID_ENTRY_COMPARATOR);
            if (r3 >= 0)
            {
                //il tag è ancora connesso e si notifica la nuova posizione
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        listener.onTagDataAvailable(updated.get(r3).tagDistance);
                    }
                }).start();
            }
        }
    }

    /**
     * Imposta il controller. Non avvia il polling con il modulo, che deve essere fatto manualmente tramite il metodo startUpdate()
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     * @throws IllegalArgumentException Se il busName non è valido
     * @throws IOException Se avviene un errore nella creazione del driver DWM
     */
    @SuppressWarnings("WeakerAccess")
    public DistanceController(String busName) throws IllegalArgumentException, IOException
    {
        driverDWM = new DriverDWM(busName);
        tagListeners = new ArrayList<>();
        allListeners = new ArrayList<>();

        actualData = new ArrayList<>();
        disconnectedData = new ArrayList<>();

        connectionErrors = 0;

        //controlla lo stato della connessione del modulo
        driverDWM.checkDWM();
    }

    /**
     * Imposta il controller. Avvio automatico del polling, con un ritardo iniziale e periodo impostabile
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     * @param period Il periodo di aggiornamento
     * @throws IllegalArgumentException Se il busName non è valido, oppure il periodo è negativo
     * @throws IOException Se avviene un errore nella creazione del driver DWM
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public DistanceController(String busName, long period) throws IllegalArgumentException, IOException
    {
        this(busName);

        if (period < 0)
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo. Il periodo deve essere di almeno: " + MINIMUM_UPDATE_PERIOD + " ms.");
        else if (period < MINIMUM_UPDATE_PERIOD)
            throw new IllegalArgumentException("Il periodo di aggiornamento è troppo basso. Il periodo deve essere di almeno: " + MINIMUM_UPDATE_PERIOD + " ms.");

        startUpdate(period);
    }

    /**
     * Aggiunge un listener per uno specifico tag
     * @param tagID L'ID del tag a cui viene associato il listener
     * @param listener Il listener
     */
    public void addTagListener(int tagID, TagListener listener)
    {
        synchronized (listenersLock)
        {
            tagListeners.add(new Pair<>(tagID, listener));
        }
    }

    /**
     * Rimuove il listener, se presente
     * @param listener Il listener da rimuovere
     */
    @SuppressWarnings("unused")
    public void removeTagListener(TagListener listener)
    {
        synchronized (listenersLock)
        {
            for (int i = 0; i < tagListeners.size(); i++)
            {
                Pair<Integer, TagListener> pair = tagListeners.get(i);

                if (pair.second.equals(listener))
                {
                    tagListeners.remove(i);
                }
            }
        }
    }

    /**
     * Aggiunge un listener che risponde agli eventi per tutti i tag
     * @param listener Il listener da aggiungere
     */
    public void addAllTagsListener(AllTagsListener listener)
    {
        synchronized (listenersLock)
        {
            allListeners.add(listener);
        }
    }

    /**
     * Rimuove il listener, se presente
     * @param listener Il listener da rimuovere
     */
    @SuppressWarnings("unused")
    public void removeAllTagsListener(AllTagsListener listener)
    {
        synchronized (listenersLock)
        {
            allListeners.remove(listener);
        }
    }

    /**
     * Inizia il polling del modulo con un periodo impostabile come parametro
     * @param period Il tempo che trascorre tra un update e il successivo
     */
    @SuppressWarnings("WeakerAccess")
    public void startUpdate(long period) throws IllegalArgumentException, IllegalStateException
    {
        if (period < 0)
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo. Il periodo deve essere di almeno: " + MINIMUM_UPDATE_PERIOD + " ms.");
        else if (period < MINIMUM_UPDATE_PERIOD)
            throw new IllegalArgumentException("Il periodo di aggiornamento è troppo basso. Il periodo deve essere di almeno: " + MINIMUM_UPDATE_PERIOD + " ms.");

        if (updateDataTimer == null)
        {
            updateDataTimer = new Timer(true);
            updateDataTimer.scheduleAtFixedRate(updateDataTask, 0L, period);
        } else
            throw new IllegalStateException("Timer già avviato");
    }

    /**
     * Termina il polling del modulo
     * */
    @SuppressWarnings("WeakerAccess")
    public void stopUpdate()
    {
        if (updateDataTimer != null)
        {
            updateDataTimer.cancel();
            updateDataTimer = null;
        }
    }

    /**
     * Cambia il bus di comunicazione con il modulo DWM
     * @param busName Il nome del bus di comunicazione
     * @throws IOException In caso di errore di comunicazione con il modulo DWM
     */
    @SuppressWarnings({"unused", "EmptyCatchBlock"})
    public void switchBus(String busName) throws IOException
    {
        if (driverDWM != null)
        {
            driverDWM.close();
            driverDWM = null;
        }

        long period = updateDataTask.scheduledExecutionTime();
        stopUpdate();

        //Prova a sospendere il thread per 50ms. Se non riesce, viene effettuato busy-waiting
        long time = System.currentTimeMillis();
        do
        {
            try
            {
                TimeUnit.MILLISECONDS.sleep(50L - (System.currentTimeMillis() - time));
            } catch (InterruptedException e) {}
        } while((System.currentTimeMillis() - time) >= 50L);

        driverDWM = new DriverDWM(busName);
        driverDWM.checkDWM();

        startUpdate(period);
    }

    /**
     * Chiude il DistanceController e rilascia le risorse
     */
    public void close()
    {
        if (updateDataTimer != null)
        {
            updateDataTimer.cancel();
            updateDataTimer = null;
        }

        if (driverDWM != null)
        {
            try
            {
                driverDWM.close();
            } catch (IOException e)
            {
                Log.e(MainActivity.TAG, "Eccezione in chiusura del driver DWM", e);
            }
            driverDWM = null;
        }

        synchronized (listenersLock)
        {
            allListeners = null;
            tagListeners = null;
        }
    }

    /**
     * Restituisce una lista con gli ID dei tag connessi al modulo DWM. E' da usare una tantum.
     * @return una lista contenente i tag connessi
     */
    public List<Integer> getTagIDs()
    {
        synchronized (dataLock)
        {
            List<Integer> tags = new ArrayList<>(actualData.size());
            for (int i = 0; i < actualData.size(); i++)
            {
                int result = Collections.binarySearch(disconnectedData, actualData.get(i), MATCHING_ID_ENTRY_COMPARATOR);
                if (result < 0)
                    tags.add(actualData.get(i).tagID);
            }

            return tags;
        }
    }
}