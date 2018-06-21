package group107.distancealert;

import android.os.SystemClock;
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
 * Classe thread-safe che rappresenta un sensore di distanza.
 */
public class DistanceController
{
    /**
     * Stringa utile per log del DistanceController
     */
    private static final String TAG = "DistanceController";

    /**
     * Numero di byte usati dal modulo DWM per descrivere i dati relativi a ciascun tag
     */
    private static final int BYTES_PER_ENTRY = 20;

    /**
     * Numero di volte per cui ricevendo gli stessi dati dal modulo DWM, un tag viene dichiarato
     * disconnesso. Questo serve perché il modulo DWM continua ad includere i dati relativi
     * a tag fuori portata, riportando l'ultima distanza nota.
     */
    private static final int COUNTER_FOR_DISCONNECTED = 4;

    /**
     * Numero di errori nella comunicazione con il modulo DWM prima di intervenire
     */
    private static final int COUNTER_FOR_CONNECTION_ERRORS = 2;

    /**
     * Periodo minimo di aggiornamento a cui può essere settato il controller (<= 10 Hz)
     */
    private static final long MINIMUM_UPDATE_PERIOD = 100L;

    /**
     * Periodo di sospensione delle comunicazioni in caso di problemi (in ms)
     */
    private static final long COMMUNICATION_PAUSE_TIME = 30000L;

    /**
     * Variabile usata per salvare il momento in cui sono state sospese le comunicazioni
     */
    private static long communicationPauseTimer = 0;

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
     * Classe che rappresenta una coppia id-distanza.
     * E' implementata in modo tale da essere un oggetto immutabile.
     */
    @SuppressWarnings("WeakerAccess")
    public static class Entry
    {
        public final int tagID;
        public final int tagDistance;
        private int counter;

        /**
         * Crea una nuova entry con id del tag e distanza specificata per parametro
         *
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
         *
         * @param e Entry da copiare
         */
        private Entry(Entry e)
        {
            tagID = e.tagID;
            tagDistance = e.tagDistance;
            counter = e.counter;
        }

        /**
         * Effettua un confronto per stabilire se l'entry passata per parametro è uguale,
         * sia come id del tag che come distanza misurata.
         *
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
         *
         * @return Una stringa che rappresenta l'entry
         */
        @Override
        public String toString()
        {
            return "ID: " + tagID + "  Distanza: " + tagDistance + "mm";
        }
    }

    /**
     * Logga tutte le entry presenti nella lista, anteponendoci un messaggio
     *
     * @param tag Il tag con cui fare il log
     * @param message Il messaggio da anteporre ai dati
     * @param separator Una stringa usata per separare visivamente i dati
     * @param data Lista con le entry da loggare
     */
    @SuppressWarnings("SameParameterValue")
    private static void logEntryData(String tag, String message, String separator, List<Entry> data)
    {
        if (data == null || data.size() == 0)
        {
            Log.d(tag, message + "<nessuno>");
            return;
        }

        String result = message;

        for (int i = 0; i < data.size(); i++)
            result = result.concat(data.get(i).toString() + separator);

        Log.d(tag, result);
    }

    /**
     * Memorizza i dati attuali
     */
    private List<Entry> actualData;

    /**
     * Memorizza i dati dei tag che sono disconnessi
     */
    private List<Entry> disconnectedData;

    /**
     * Memorizza tutti i listeners associati a tutti i tag
     */
    private List<AllTagsListener> allListeners;

    /**
     * Memorizza tutti i listeners associati ad uno specifico tag
     */
    private List<Pair<Integer, TagListener>> tagListeners;

    /**
     * Oggetto che gestisce la comunicazione a basso livello con il modulo DWM
     */
    private DriverDWM driverDWM;

    /**
     * Contatore degli errori di comunicazione con il modulo DWM
     */
    private int connectionErrors = 0;

    /**
     * oggetto che, usando un thread secondario che autogestisce la sua schedulazione periodica,
     * si occupa di effettuare il polling del modulo DWM per ottenere le distanze.
     */
    private ScheduledThreadPoolExecutor updateDataTimer;

    /**
     * Oggetto che definisce la routine di aggiornamento periodica del controller
     */
    private final Runnable updateDataTask = new Runnable()
    {
        @Override
        public void run()
        {
            if ((SystemClock.uptimeMillis() - communicationPauseTimer < COMMUNICATION_PAUSE_TIME)
                    && communicationPauseTimer != 0) {
                Log.v(TAG, "Aggiornamento distanza in pausa. Prossimo tentativo tra: "
                        + (communicationPauseTimer + COMMUNICATION_PAUSE_TIME - SystemClock.uptimeMillis()) + "ms");
                return;
            }

            synchronized (this)
            {
                try
                {
                    List<Entry> data = updateData();
                    classifyDataAndNotify(data);
                    actualData = data;
                    connectionErrors = 0;
                } catch (Exception e)
                {
                    connectionErrors++;
                    Log.w(TAG, "Avvenuta " + connectionErrors + "^ eccezione in updateDataTask", e);

                    /*
                     Nel caso sia stato raggiunto il limite di errori di comunicazione consecutivi,
                     si procede tentando di risolverli
                     */
                    if (connectionErrors >= COUNTER_FOR_CONNECTION_ERRORS)
                    {
                        try
                        {
                            /*
                             Nel caso sia stato perso l'accesso alla periferica,
                             deve ricreare l'oggetto DriverDWM
                             */
                            if (e instanceof IllegalStateException) {
                                String bus = driverDWM.getMyBus();
                                driverDWM.close();
                                driverDWM = new DriverDWM(bus);
                            }

                            // Dopo aver aspettato un tempo minimo, controlla la connessione
                            SleepHelper.sleepMillis(MINIMUM_UPDATE_PERIOD);
                            driverDWM.checkDWM();

                            /*
                             Arrivati a questo punto, la connessione è funzionante.
                             Viene azzerato il contatore degli errori.
                             */
                            connectionErrors = 0;
                        } catch (Exception e1)
                        {
                            /*
                             Nel caso ci siano stati ancora problemi, sospende l'aggiornamento
                             per un lasso di tempo, così da permettere al modulo di sistemarsi.
                             */
                            communicationPauseTimer = SystemClock.uptimeMillis();

                            String text = "Periferica non funzionante.\n" +
                                    "Nuovo tentativo tra " + (COMMUNICATION_PAUSE_TIME / 1000L) + " secondi.";
                            notifyError(text, e1);
                            Log.e(TAG, text, e1);
                        }
                    }
                }
            }
        }
    };

    /**
     * Imposta il controller. Non avvia il polling con il modulo,
     * che deve essere fatto manualmente tramite il metodo startUpdate()
     *
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     * @throws IllegalArgumentException Se il busName non è valido
     * @throws IOException Se avviene un errore nella creazione del driver DWM
     */
    @SuppressWarnings("WeakerAccess")
    public DistanceController(String busName) throws IllegalArgumentException, IOException
    {
        synchronized (this)
        {
            driverDWM = new DriverDWM(busName);
            tagListeners = new ArrayList<>();
            allListeners = new ArrayList<>();

            actualData = new ArrayList<>();
            disconnectedData = new ArrayList<>();

            connectionErrors = 0;

            // Evita di effettuare il check della connessione se il controller è stato messo in pausa
            if (SystemClock.uptimeMillis() - communicationPauseTimer < COMMUNICATION_PAUSE_TIME
                    && communicationPauseTimer != 0) {
                Log.v(TAG, "Controllo della connessione non effettuato.");
                return;
            }

            // Controlla lo stato della connessione del modulo
            try {
                driverDWM.checkDWM();
            } catch (Exception e) {
                // Connessione non funzionante. Rilascia risorse
                driverDWM.close();
                throw e;
            }
        }
    }

    /**
     * Imposta il controller. Avvio automatico dell'aggiornamento, con periodo impostabile
     *
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
     * Restituisce una lista con gli ID dei tag connessi al modulo DWM.
     *
     * @return una lista contenente i tag connessi
     */
    public synchronized List<Integer> getTagIDs()
    {
        List<Integer> tags = new ArrayList<>(actualData.size());
        for (int i = 0; i < actualData.size(); i++)
        {
            tags.add(actualData.get(i).tagID);
        }

        return tags;
    }

    /**
     * Inizia l'aggiornamento del modulo con un periodo impostabile come parametro
     *
     * @param period Il tempo che trascorre tra un update e il successivo
     * @throws IllegalArgumentException Se il periodo di aggiornamento è troppo basso
     * @throws IllegalStateException Se l'aggiornamento è già avviato
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void startUpdate(long period) throws IllegalArgumentException, IllegalStateException
    {
        if (period < 0)
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo. " +
                    "Il periodo deve essere di almeno: " + MINIMUM_UPDATE_PERIOD + " ms.");

        else if (period < MINIMUM_UPDATE_PERIOD)
            throw new IllegalArgumentException("Il periodo di aggiornamento è troppo basso. " +
                    "Il periodo deve essere di almeno: " + MINIMUM_UPDATE_PERIOD + " ms.");

        // Avvio aggiornamento temporizzato
        if (updateDataTimer == null)
        {
            updateDataTimer = new ScheduledThreadPoolExecutor(1);

            /*
             Impostazione necessaria per spegnere correttamente il timer completando l'esecuzione del
             relativo task già avviato prima dello spegnimento
             */
            updateDataTimer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            updateDataTimer.scheduleAtFixedRate(updateDataTask, period, period, TimeUnit.MILLISECONDS);
        } else
            throw new IllegalStateException("Timer già avviato");
    }

    /**
     * Recupera le informazioni dal pacchetto di risposta del modulo DWM
     *
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La lista di coppie id-distanza dei tag
     * @throws IllegalArgumentException Se i dati ricevuti dal modulo DWM non sono validi
     */
    private synchronized List<Entry> getDataFromDWMResponse(int[] dwmResponse) throws IllegalArgumentException
    {
        if (dwmResponse == null || dwmResponse.length < 21 || dwmResponse[2] != 0)
        {
            // Dati non validi
            throw new IllegalArgumentException("Dati ricevuti dal modulo non validi.");
        }

        // Ottiene il numero di tag inclusi nella risposta
        int numberOfValues = dwmResponse[20];

        List<Entry> newData = new ArrayList<>(numberOfValues);
        int startIndex = 21;

        // Si noti che il modulo DWM usa la notazione Little Endian
        for (int i = 0; i < numberOfValues; i++, startIndex += BYTES_PER_ENTRY)
        {
            // Decodifica dell'id del tag
            int id = (dwmResponse[startIndex + 1] << 8) + dwmResponse[startIndex];

            // Decodifica della distanza relativa al tag
            int d1 = dwmResponse[startIndex + 2];
            int d2 = dwmResponse[startIndex + 3];
            int d3 = dwmResponse[startIndex + 4];
            int d4 = dwmResponse[startIndex + 5];
            int distance = (d4 << 24) + (d3 << 16) + (d2 << 8) + d1;

            // Aggiunge i dati alla lista delle Entry
            newData.add(new Entry(id, distance));
        }

        return newData;
    }

    /**
     * Ottiene i nuovi dati dal modulo DWM
     *
     * @return I nuovi dati dal modulo DWM
     * @throws IOException Se avviene un errore di comunicazione con il modulo DWM
     */
    private synchronized List<Entry> updateData() throws IOException
    {
        // Richiede i dati al driverDVM
        int[] dwmResponse = driverDWM.requestAPI((byte) 0x0C, null);

        // Ottiene una lista di Entry dai dati ricevuti dal modulo
        List<Entry> newData = getDataFromDWMResponse(dwmResponse);

        logEntryData(TAG, "\nDati dal modulo:\n", "\n", newData);

        // Ordina gli elementi per tagID crescente, in modo tale da velocizzare le operazioni successive
        Collections.sort(newData, MATCHING_ID_ENTRY_COMPARATOR);

        // Elimina i dati dei tag disconnessi
        for (int i = 0; i < disconnectedData.size(); i++)
        {
            Entry discEntry = disconnectedData.get(i);

            String toLog = "updateData() -> Esaminando tag disconnesso: " + Integer.toHexString(discEntry.tagID);

            // Ricerca di dati relativi al tag disconnesso in esame nei dati ricevuti dal modulo
            int result = Collections.binarySearch(newData, discEntry, MATCHING_ID_ENTRY_COMPARATOR);
            if (result >= 0)
            {
                // Dato dello stesso tag presente
                Entry newEntry = newData.get(result);

                if(newEntry.tagDistance == discEntry.tagDistance)
                {
                    // Tag disconnesso. Eliminare entry dai dati attuali
                    toLog += " rimossa entry dalla lista.";
                    newData.remove(result);
                } else
                {
                    /*
                     Tag che prima era disconnesso e ora si è riconnesso. Rimuovere dalla lista dei
                     tag disconnessi; la sua riconnessione verrà riconosciuta al prossimo passo.
                     */
                    toLog += " tag si è riconnesso.";
                    disconnectedData.remove(i);
                    i--;
                }
            }
            else
                // Dati del tag disconnesso in esame non presenti nei dati ricevuti dal modulo
                toLog += " tag non è presente.";

            Log.v(TAG, toLog);
        }

        return newData;
    }

    /**
     * Classifica i dati ottenuti dal modulo DWM e notifica ai listener
     *
     * @param newData I nuovi dati ottenuti dal modulo
     */
    private synchronized void classifyDataAndNotify(List<Entry> newData)
    {
        List<Entry> updated = new ArrayList<>();
        List<Entry> connected = new ArrayList<>();
        List<Entry> disconnected = new ArrayList<>();

        for (int i = 0; i < newData.size(); i++)
        {
            Entry newEntry = newData.get(i);

            String toLog = "classifyDataAndNotify() -> Esaminando tag: " + Integer.toHexString(newEntry.tagID);

            // Ricerca di Entry nei dati precedenti con lo stesso tagID
            int result = Collections.binarySearch(actualData, newEntry, MATCHING_ID_ENTRY_COMPARATOR);
            if (result >= 0)
            {
                Entry actualEntry = actualData.get(result);

                if (newEntry.tagDistance == actualEntry.tagDistance)
                {
                    // Tag appena disconnesso
                    newEntry.counter = actualEntry.counter + 1;

                    /*
                     Controlla se il tag è andato fuori portata,
                     ovvero se ha ricevuto troppi dati uguali consecutivi
                     */
                    if (newEntry.counter >= COUNTER_FOR_DISCONNECTED)
                    {
                        // Dichiara disconnessione
                        toLog += " appena disconnesso.";
                        disconnected.add(new Entry(newEntry));
                        disconnectedData.add(new Entry(newEntry));
                    }
                    else
                    {
                        // Incrementa contatore dati uguali consecutivi
                        toLog += (" potrebbe essere disconnesso, con counter: " + newEntry.counter + ".");
                        updated.add(new Entry(newEntry));
                    }
                } else
                {
                    // Ricevuti nuovi dati, azzera contatore
                    newEntry.counter = 0;
                    toLog += " tag aggiornato.";
                    updated.add(new Entry(newEntry));
                }
            } else
            {
                // Tag appena connesso
                newEntry.counter = 0;
                toLog += " appena connesso.";
                connected.add(new Entry(newEntry));
            }

            Log.v(TAG, toLog);
        }

        // Ricerca di Entry relative a tag che "scompaiono" nell'ultimo aggiornamento
        for (int i = 0; i < actualData.size(); i++)
        {
            Entry actualEntry = actualData.get(i);

            int result = Collections.binarySearch(newData, actualEntry, MATCHING_ID_ENTRY_COMPARATOR);
            if (result < 0)
            {
                // Tag presente nei dati vecchi ma non più nei nuovi => tag disconnesso
                disconnected.add(new Entry(actualEntry.tagID, actualEntry.tagDistance));

                Log.v(TAG, "classifyDataAndNotify() -> Esaminando vecchio tag: " +
                        Integer.toHexString(actualEntry.tagID) + ": appena disconnesso.");
            }
        }

        // Ordina i dati relativi ai tag disconnessi. Necessario per velocizzare le operazioni.
        Collections.sort(disconnectedData, MATCHING_ID_ENTRY_COMPARATOR);

        logEntryData(TAG, "\nTag appena connessi: " + connected.size() + "\n", "\n", connected);
        logEntryData(TAG, "\nTag appena disconnessi: " + disconnected.size() + "\n", "\n", disconnected);
        logEntryData(TAG, "\nTag ancora connessi: " + updated.size() + "\n", "\n", updated);

        // Notifica i nuovi dati a tutti i listener
        notifyToAllTagsListeners(connected, disconnected, updated);
        notifyToTagsListeners(connected, disconnected, updated);
    }

    /**
     * Notifica a tutti i listener globali.
     * Questo metodo non è synchronized poiché viene invocato solamente all'interno di metodi synchronized.
     *
     * @param connected Lista di entry dei tag appena connessi
     * @param disconnected Lista di entry dei tag appena disconnessi
     * @param updated Lista di entry dei tag con dati aggiornati
     */
    private void notifyToAllTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> updated)
    {
        for (int i = 0; i < allListeners.size(); i++)
        {
            final AllTagsListener listener = allListeners.get(i);

            if (connected != null && connected.size() > 0)
            {
                // Presenti tags connessi nell'ultimo aggiornamento dei dati
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // Notifica su thread separato
                        listener.onTagHasConnected(connected);
                    }
                }).start();
            }

            if (disconnected != null && disconnected.size() > 0)
            {
                // Presenti tags disconnessi nell'ultimo aggiornamento dei dati
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // Notifica su thread separato
                        listener.onTagHasDisconnected(disconnected);
                    }
                }).start();
            }

            if (updated != null && updated.size() > 0)
            {
                // Presenti tags aggiornati nell'ultimo aggiornamento dei dati
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // Notifica su thread separato
                        listener.onTagDataAvailable(updated);
                    }
                }).start();
            }
        }
    }

    /**
     * Notifica a tutti i listener specifici per un tag, se sono avvenuti degli eventi su quel tag
     *
     * @param connected Lista di entry dei tag appena connessi
     * @param disconnected Lista di entry dei tag appena disconnessi
     * @param updated Lista di entry dei tag con dati aggiornati
     */
    private void notifyToTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> updated)
    {
        // Lock già ottenuto

        for (int i = 0; i < tagListeners.size(); i++)
        {
            final Pair<Integer, TagListener> pair = tagListeners.get(i);
            int ID = pair.first;
            final TagListener listener = pair.second;

            if (connected != null && connected.size() > 0)
            {
                // connected, disconnected e data sono ordinati, per costruzione
                final int r1 = Collections.binarySearch(connected, new Entry(ID, -1), MATCHING_ID_ENTRY_COMPARATOR);
                if (r1 >= 0)
                {
                    // Il tag si è appena connesso
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            // Notifica su thread separato
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
                    // Il tag si è appena disconnesso
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            // Notifica su thread separato
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
                    // Il tag è ancora connesso e si notifica la nuova posizione
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            // Notifica su thread separato
                            listener.onTagDataAvailable(updated.get(r3).tagDistance);
                        }
                    }).start();
                }
            }
        }
    }

    /**
     * Segnala a tutti i listener un errore nell'aggiornamento
     *
     * @param shortDescription Breve descrizione del problema
     * @param e Eccezzione avvenuta
     */
    private void notifyError(final String shortDescription, final Exception e) {
        // Segnalazione a tutti gli AllTagsListener
        for (int i = 0; i < allListeners.size(); i++) {
            final AllTagsListener listener = allListeners.get(i);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // Notifica su thread separato
                    listener.onError(shortDescription, e);
                }
            }).start();
        }

        // Segnalazione a tutti i TagListener
        for (int i = 0; i < tagListeners.size(); i++) {
            final Pair<Integer, TagListener> pair = tagListeners.get(i);
            final TagListener listener = pair.second;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    // Notifica su thread separato
                    listener.onError(shortDescription, e);
                }
            }).start();
        }
    }

    /**
     * Aggiunge un listener che risponde agli eventi per tutti i tag
     *
     * @param listener Il listener da aggiungere
     */
    public synchronized void addAllTagsListener(AllTagsListener listener)
    {
        if (allListeners == null)
            return;

        allListeners.add(listener);
    }

    /**
     * Aggiunge un listener per uno specifico tag
     *
     * @param tagID L'ID del tag a cui viene associato il listener
     * @param listener Il listener
     */
    public synchronized void addTagListener(int tagID, TagListener listener)
    {
        if (tagListeners == null)
            return;

        tagListeners.add(new Pair<>(tagID, listener));
    }

    /**
     * Rimuove il listener, se presente
     *
     * @param listener Il listener da rimuovere
     */
    @SuppressWarnings("unused")
    public synchronized void removeAllTagsListener(AllTagsListener listener)
    {
        if (allListeners == null)
            return;

        allListeners.remove(listener);
    }

    /**
     * Rimuove il listener, se presente
     *
     * @param listener Il listener da rimuovere
     */
    @SuppressWarnings("unused")
    public synchronized void removeTagListener(TagListener listener)
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

    /**
     * Termina l'aggiornamento del modulo
     */
    @SuppressWarnings({"WeakerAccess"})
    public synchronized void stopUpdate()
    {
        if (updateDataTimer != null)
        {
            updateDataTimer.shutdown();
            updateDataTimer = null;
        }
    }

    /**
     * Chiude il DistanceController e rilascia le risorse
     */
    public synchronized void close()
    {
        // Termina l'aggiornamento
        stopUpdate();

        // Chiude il driverDWM
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

        // Cancella i listener
        allListeners = null;
        tagListeners = null;
    }
}