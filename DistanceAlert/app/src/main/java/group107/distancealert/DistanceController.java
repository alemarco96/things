package group107.distancealert;

import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Classe che rappresenta un sensore di distanza.
 */
public class DistanceController
{
    private static final String TAG = "DistanceController";
    //numero di byte usati dal modulo DWM per descrivere i dati relativi a ciascun tag
    private static final int BYTES_PER_ENTRY = 20;
    //numero di volte per cui ricevendo gli stessi dati dal modulo DWM, un tag viene dichiarato disconnesso
    private static final int COUNTER_FOR_DISCONNECTED = 4;
    //numero di errori nella comunicazione con il modulo DWM prima di intervenire
    private static final int COUNTER_FOR_CONNECTION_ERRORS = 3;
    //periodo minimo di aggiornamento a cui può essere settato il controller (<= 10 Hz)
    private static final long MINIMUM_UPDATE_PERIOD = 100L;

    private static long dwmBugTimer = 0;
    private static final long DWM_BUG_PAUSE = 30000L;

    /**
     * Oggetto che serve per ordinare in ordine crescente per tagID le entry
     */
    private final Comparator<Entry> MATCHING_ID_ENTRY_COMPARATOR = new Comparator<Entry>()
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
            return "ID: " + tagID + "  Distanza: " + tagDistance + "mm";
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

    //TODO:serve davvero?
    //oggetto usato per impedire la chiusura del controller mentre è in corso la comunicazione a basso livello
    private final Object workingLock = new Object();

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
    private ScheduledThreadPoolExecutor updateDataTimer;


    /**
     * Oggetto che definisce la routine di aggiornamento periodica
     */
    private final Runnable updateDataTask = new Runnable()
    {
        @Override
        public void run()
        {
            if (System.currentTimeMillis() - dwmBugTimer < DWM_BUG_PAUSE && dwmBugTimer != 0) {
                Log.v(TAG, "Aggiornamento distanza in pausa. Prossimo tentativo tra: "
                        + (dwmBugTimer + DWM_BUG_PAUSE - System.currentTimeMillis()) + "ms");
                return;
            }

            synchronized (workingLock)
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
                } catch (IOException e)
                {
                    connectionErrors++;
                    Log.w(TAG, "Avvenuta " + connectionErrors + "^ eccezione in updateDataTask", e);
                    if (connectionErrors >= COUNTER_FOR_CONNECTION_ERRORS)
                    {
                        //ritesta la connessione per valutare se è ancora attiva
                        try
                        {
                            SleepHelper.sleepMillis(MINIMUM_UPDATE_PERIOD);

                            driverDWM.checkDWM();

                            //canale di aggiornamento funzionante. Riprova a far funzionare il controller
                            connectionErrors = 0;
                        } catch (IOException e2)
                        {
                            dwmBugTimer = System.currentTimeMillis();

                            String text = "Periferica non funzionante.\n" +
                                    "Nuovo tentativo tra " + (DWM_BUG_PAUSE / 1000L) + " secondi.";

                            notifyError(text, e2);

                            Log.e(TAG, text, e2);
                        }
                    }
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

            String toLog = "updateData() -> Esaminando tag disconnesso: " + Integer.toHexString(discEntry.tagID);

            int result = Collections.binarySearch(newData, discEntry, MATCHING_ID_ENTRY_COMPARATOR);
            if (result >= 0)
            {
                //dato dello stesso tag presente
                Entry newEntry = newData.get(result);

                if(newEntry.tagDistance == discEntry.tagDistance)
                {
                    //tag disconnesso. eliminare entry dai dati attuali
                    toLog += " rimossa entry dalla lista.";
                    newData.remove(result);
                } else
                {
                    //tag prima disconnesso, ed ora si è riconnesso. rimuovere dalla lista dei tag disconnessi
                    //la sua riconnessione verrà riconosciuta al prossimo passo
                    toLog += " tag si è riconnesso.";
                    disconnectedData.remove(i);
                    i--;
                }
            }
            else
                //newData non presente in disconnectedData
                toLog += " tag non è presente.";

            Log.v(TAG, toLog);
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

                String toLog = "classifyDataAndNotify() -> Esaminando tag: " + Integer.toHexString(newEntry.tagID);

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
                            toLog += " appena disconnesso.";
                            disconnected.add(new Entry(newEntry));
                            disconnectedData.add(new Entry(newEntry));
                        }
                        else
                        {
                            toLog += (" potrebbe essere disconnesso, con counter: " + newEntry.counter + ".");
                            updated.add(new Entry(newEntry));
                        }
                    } else
                    {
                        newEntry.counter = 0;
                        toLog += " tag aggiornato.";
                        updated.add(new Entry(newEntry));
                    }
                } else
                {
                    //tag appena connesso
                    newEntry.counter = 0;
                    toLog += " appena connesso.";
                    connected.add(new Entry(newEntry));
                }

                Log.v(TAG, toLog);
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

        for (int i = 0; i < allListeners.size(); i++)
        {
            final AllTagsListener listener = allListeners.get(i);

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

        for (int i = 0; i < tagListeners.size(); i++)
        {
            final Pair<Integer, TagListener> pair = tagListeners.get(i);
            int ID = pair.first;
            final TagListener listener = pair.second;

            if (connected != null && connected.size() > 0)
            {
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
            }

            if (disconnected != null && disconnected.size() > 0)
            {
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
            }

            if (updated != null && updated.size() > 0)
            {
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
    }

    private void notifyError(final String shortDescription, final IOException e) {
        for (int i = 0; i < allListeners.size(); i++) {
            final AllTagsListener listener = allListeners.get(i);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    listener.onError(shortDescription, e);
                }
            }).start();
        }

        for (int i = 0; i < tagListeners.size(); i++) {
            final Pair<Integer, TagListener> pair = tagListeners.get(i);
            final TagListener listener = pair.second;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    listener.onError(shortDescription, e);
                }
            }).start();
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
        synchronized (workingLock)
        {
            driverDWM = new DriverDWM(busName);
            tagListeners = new ArrayList<>();
            allListeners = new ArrayList<>();

            actualData = new ArrayList<>();
            disconnectedData = new ArrayList<>();

            connectionErrors = 0;

            if (System.currentTimeMillis() - dwmBugTimer < DWM_BUG_PAUSE && dwmBugTimer != 0) {
                return;
            }

            //controlla lo stato della connessione del modulo
            try {
                driverDWM.checkDWM();
            } catch (IOException e) {
                //connessione non funzionante. Rilascia risorse
                driverDWM.close();
                throw e;
            }
        }
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
        startUpdate(period);
    }

    /**
     * Aggiunge un listener per uno specifico tag
     * @param tagID L'ID del tag a cui viene associato il listener
     * @param listener Il listener
     */
    public void addTagListener(int tagID, TagListener listener)
    {
        synchronized (workingLock)
        {
            synchronized (listenersLock)
            {
                if (tagListeners == null)
                    return;

                tagListeners.add(new Pair<>(tagID, listener));
            }
        }
    }

    /**
     * Rimuove il listener, se presente
     * @param listener Il listener da rimuovere
     */
    @SuppressWarnings("unused")
    public void removeTagListener(TagListener listener)
    {
        synchronized (workingLock)
        {
            synchronized (listenersLock)
            {
                if (tagListeners == null)
                    return;

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
    }

    /**
     * Aggiunge un listener che risponde agli eventi per tutti i tag
     * @param listener Il listener da aggiungere
     */
    public void addAllTagsListener(AllTagsListener listener)
    {
        synchronized (workingLock)
        {
            synchronized (listenersLock)
            {
                if (allListeners == null)
                    return;

                allListeners.add(listener);
            }
        }
    }

    /**
     * Rimuove il listener, se presente
     * @param listener Il listener da rimuovere
     */
    @SuppressWarnings("unused")
    public void removeAllTagsListener(AllTagsListener listener)
    {
        synchronized (workingLock)
        {
            synchronized (listenersLock)
            {
                if (allListeners == null)
                    return;

                allListeners.remove(listener);
            }
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

        synchronized (workingLock) {
            if (updateDataTimer == null)
            {
                updateDataTimer = new ScheduledThreadPoolExecutor(1);
                updateDataTimer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
                updateDataTimer.scheduleAtFixedRate(updateDataTask, period, period, TimeUnit.MILLISECONDS);
            } else
                throw new IllegalStateException("Timer già avviato");
        }
    }

    /**
     * Termina il polling del modulo
     * */
    @SuppressWarnings("WeakerAccess")
    public void stopUpdate()
    {
        synchronized (workingLock)
        {
            if (updateDataTimer != null)
            {
                updateDataTimer.shutdown();
                updateDataTimer = null;
            }
        }
    }

    /**
     * Chiude il DistanceController e rilascia le risorse
     */
    public void close()
    {
        synchronized (workingLock)
        {
            //stop update
            if (updateDataTimer != null)
            {

                updateDataTimer.shutdown();
                /*
                try {
                    updateDataTimer.awaitTermination(365, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "timer.awaitTermination() interrotto.");
                }
                */
                updateDataTimer = null;
            }

            if (driverDWM != null)
            {
                try
                {
                    driverDWM.close();
                } catch (IOException e)
                {
                    Log.e(TAG, "Eccezione in chiusura del driver DWM", e);
                }
                driverDWM = null;
            }

            synchronized (listenersLock)
            {
                allListeners = null;
                tagListeners = null;
            }
        }
    }

    /**
     * Restituisce una lista con gli ID dei tag connessi al modulo DWM. E' da usare una tantum.
     * @return una lista contenente i tag connessi
     */
    public List<Integer> getTagIDs()
    {
        synchronized (workingLock)
        {
            synchronized (dataLock)
            {
                List<Integer> tags = new ArrayList<>(actualData.size());
                for (int i = 0; i < actualData.size(); i++)
                {
                    tags.add(actualData.get(i).tagID);
                }

                return tags;
            }
        }
    }
}