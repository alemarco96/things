package group107.distancealert;

import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.Closeable;
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
    private static void logEntryData(String tag, String message, String separator, List<Entry> data) {
        String result = "" + message;

        if (data == null || data.size() == 0)
            result.concat("<nessuno>");

        for (int i = 0; i < data.size(); i++)
            result.concat(data.get(i).toString() + separator);

        Log.d(tag, result);
    }

    /**
     * Logga il tempo trascorso per svolgere una operazione
     * @param tag Il tag con cui fare il log
     * @param message Il messaggio da anteporre ai dati
     * @param timeElapsed Il tempo in nanoecondi trascorsi
     */
    private static void logTimeElapsed(String tag, String message, long timeElapsed) {
        Log.d(tag, message + timeElapsed / 1000 + " us");
    }

    //memorizza i dati attuali
    private List<Entry> actualData;
    //memorizza i dati dei tag che sono disconnessi
    private List<Entry> disconnectedData;

    //memorizza tutti i listeners associati a tutti i tag
    private List<AllTagsListener> allListeners;
    //memorizza tutti i listeners associati ad uno specifico tag
    private List<Pair<Integer, TagListener>> tagListeners;

    //memorizza l'ultimo errore nel thread di aggiornamento
    private Throwable lastException;

    //oggetto usato per gestire l'accesso in mutua esclusione ai dati
    private final Object dataLock = new Object();
    //oggetto usato per gestire l'accesso in mutua esclusione ai listeners
    private final Object listenersLock = new Object();
    //TODO: scrivere meglio commento
    //oggetto usato per gestore l'accesso in mutua esclusione all'eccezione
    private final Object exceptionLock = new Object();

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
                synchronized (exceptionLock)
                {
                    lastException = null;
                }
            } catch (Exception e)
            {
                //TODO: gestire eccezione
                Log.e(MainActivity.TAG, "Avvenuta eccezione in updateDataTask", e);
                synchronized (exceptionLock)
                {
                    lastException = e;
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
     * @throws IOException
     * @throws InterruptedException
     */
    private List<Entry> updateData() throws IOException, InterruptedException
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
                //final List<Entry> connectedCopy = cloneList(connected);
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
                //final List<Entry> disconnectedCopy = cloneList(disconnected);
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
                //final List<Entry> dataCopy = cloneList(updated);
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
    public DistanceController(String busName) throws IllegalArgumentException, IOException, InterruptedException
    {
        driverDWM = new DriverDWM(busName);
        tagListeners = new ArrayList<>();
        allListeners = new ArrayList<>();

        actualData = new ArrayList<>();
        disconnectedData = new ArrayList<>();

        lastException = null;

        //controlla lo stato della connessione del modulo
        driverDWM.checkDWM();
    }

    /**
     * Imposta il controller. Avvio automatico del polling, con un ritardo iniziale e periodo impostabile
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     * @param initialDelay Il ritardo iniziale prima di far iniziare il polling
     * @param period Il periodo di aggiornamento
     * @throws IllegalArgumentException Se il busName non è valido, oppure il periodo è negativo
     * @throws IOException Se avviene un errore nella creazione del driver DWM
     */
    public DistanceController(String busName, long initialDelay, long period) throws IllegalArgumentException, IOException, InterruptedException
    {
        this(busName);

        if (period < 0)
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo.");

        startUpdate(initialDelay, period);
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
     * @param listener Il listener
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
    public void removeAllTagsListener(AllTagsListener listener)
    {
        synchronized (listenersLock)
        {
            allListeners.remove(listener);
        }
    }

    /**
     * Inizia il polling del modulo con ritardo iniziale e periodo impostabili come parametro
     *
     * @param period Il tempo che trascorre tra un update e il successivo
     */
    public void startUpdate(long initialDelay, long period) throws IllegalStateException
    {
        if (period < 0)
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo.");

        if (updateDataTimer == null)
        {
            updateDataTimer = new Timer(true);
            updateDataTimer.scheduleAtFixedRate(updateDataTask, initialDelay > 0 ? initialDelay : 0, period);
        } else
            throw new IllegalStateException("Timer già avviato");
    }

    /**
     * Inizia il polling del modulo con un periodo impostabile come parametro
     * @param period Il tempo che trascorre tra un update e il successivo
     */
    public void startUpdate(long period) throws IllegalStateException
    {
        startUpdate(0L, period);
    }

    /**
     * Termina il polling del modulo
     * */
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
     * @param busName il nome del bus di comunicazione
     * @throws IOException
     * @throws InterruptedException
     */
    public void switchBus(String busName) throws IOException, InterruptedException {
        if (driverDWM != null)
            driverDWM.close();

        long period = updateDataTask.scheduledExecutionTime();
        stopUpdate();

        try {
            TimeUnit.MILLISECONDS.sleep(50L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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

        try
        {
            driverDWM.close();
            driverDWM = null;
        } catch (IOException e)
        {
            //ignora errore
            driverDWM = null;
        }

        synchronized (listenersLock)
        {
            allListeners = null;
            tagListeners = null;
        }
    }

    /**
     * Restituisce l'ultimo errore del controller
     * @return L'ultimo errore del controller
     */
    public Throwable getLastException()
    {
        synchronized (exceptionLock)
        {
            return lastException;
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