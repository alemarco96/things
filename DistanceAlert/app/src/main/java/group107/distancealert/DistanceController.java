package group107.distancealert;

import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import static group107.distancealert.MainActivity.TAG;

/**
 * Classe che rappresenta un sensore di distanza.
 */
public class DistanceController
{
    /**
     * Classe che rappresenta una coppia id-distanza. E' implementata in modo tale da essere un oggetto immutabile
     */
    public static class Entry
    {
        public final int tagID;
        public final int tagDistance;

        /**
         * Crea una nuova entry con id del tag e distanza specificata per parametro
         * @param id L'id del tag a cui è associata l'entry
         * @param distance La distanza misurata
         */
        private Entry(int id, int distance)
        {
            tagID = id;
            tagDistance = distance;
        }

        /**
         * Crea una copia dell'entry passata per parametro
         * @param e Entry da copiare
         */
        private Entry(Entry e) {
            tagID = e.tagID;
            tagDistance = e.tagDistance;
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
        public String toString() {
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
        for (Entry entry:source) {
            dest.add(new DistanceController.Entry(entry));
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

        if (data == null || data.size() == 0) {
            result.concat("<nessuno>");
        }

        for (int i = 0; i < data.size(); i++) {
            result.concat(data.toString() + separator);
        }

        Log.d(tag, result);
    }

    /**
     * Logga il tempo trascorso per svolgere una operazione
     * @param tag Il tag con cui fare il log
     * @param message Il messaggio da anteporre ai dati
     * @param timeElapsedInMs Il tempo in millisecondi trascorsi
     */
    private static void logTimeElapsed(String tag, String message, long timeElapsedInMs) {
        Log.d(tag, message + timeElapsedInMs / 1000 + "." + timeElapsedInMs % 1000 + " s.");
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

    private DriverDWM driverDWM;

    //oggetto che, usando un thread secondario che autogestisce la sua schedulazione periodica, si occupa di effettuare
    //il polling del modulo DWM per ottenere le distanze
    private Timer updateDataTimer;

    //oggetto che definisce cosa viene eseguito dal updateDataTimer
    private TimerTask updateDataTask = new TimerTask()
    {
        @Override
        public void run() {
            try {
                long time = System.currentTimeMillis();
                int[] dwmResponse = driverDWM.requestAPI((byte) 0x0C, null);
                logTimeElapsed(TAG, "\nTempo impiegato per ricevere i dati dal modulo: ", System.currentTimeMillis() - time);

                time = System.currentTimeMillis();
                List<Entry> newData = getDataFromDWMResponse(dwmResponse);
                logTimeElapsed(TAG, "\nTempo impiegato per decodificare i dati ricevuti: ", System.currentTimeMillis() - time);

                logEntryData(TAG, "\nDati dal modulo:\n", "\n", newData);

                //ordina gli elementi per tagID crescente, in modo tale da velocizzare le operazioni successive
                Collections.sort(newData, matchingIDEntryComparator);

                //elimina i dati dei tag disconnessi
                for (int i = 0; i < disconnectedData.size(); i++) {
                    Entry discEntry = disconnectedData.get(i);
                    int result = Collections.binarySearch(newData, discEntry, matchingIDEntryComparator);
                    if (result >= 0) {
                        //dato dello stesso tag presente
                        Entry newEntry = newData.get(result);

                        if(newEntry.tagDistance == discEntry.tagDistance) {
                            //tag disconnesso. eliminare entry dai dati attuali
                            newData.remove(result);
                        } else {
                            //tag prima disconnesso, ed ora si è riconnesso. rimuovere dalla lista dei tag disconnessi
                            disconnectedData.remove(i);
                            i--;
                        }
                    }
                }

                //copia i dati per poter essere utilizzati senza avere la mutua esclusione dataLock
                List<Entry> actualDataCopy;
                List<Entry> newDataCopy = cloneList(newData);

                //salva i nuovi valori
                synchronized (dataLock)
                {
                    actualDataCopy = cloneList(actualData);
                    actualData = newData;
                }

                //svolge il lavoro di notifica su thread separato per non bloccare il thread di aggiornamento
                WorkerThread workerThread = new WorkerThread(actualDataCopy, newDataCopy);
                workerThread.start();
            } catch (Exception e)
            {
                //TODO: gestire eccezione
                Log.e(MainActivity.TAG, "Avvenuta eccezione in updateDataTask", e);
            }
        }
    };

    //Oggetto che serve per ordinare in ordine crescente per tagID le entry
    private Comparator<Entry> matchingIDEntryComparator = new Comparator<Entry>() {
        @Override
        public int compare(Entry e1, Entry e2) {
            return e1.tagID - e2.tagID;
        }
    };

    private class WorkerThread extends Thread
    {
        private List<Entry> prevData;
        private List<Entry> actData;

        private WorkerThread(List<Entry> previous, List<Entry> actual)
        {
            prevData = previous;
            actData = actual;
            setDaemon(true);
        }

        public void run()
        {
            List<Entry> common = new ArrayList<>(actData.size() > prevData.size() ? actData.size() : prevData.size());
            List<Entry> connected = new ArrayList<>();
            List<Entry> disconnected = new ArrayList<>();

            for (int i = 0; i < actData.size(); i++) {
                Entry actEntry = actData.get(i);

                //ricerca di entry in prevData con lo stesso tagID
                int result = Collections.binarySearch(prevData, actEntry, matchingIDEntryComparator);
                if (result >= 0) {
                    Entry prevEntry = prevData.get(result);

                    if (actEntry.tagDistance == prevEntry.tagDistance) {
                        //tag appena disconnesso
                        disconnected.add(actEntry);
                        disconnectedData.add(actEntry);
                    } else {
                        common.add(new Entry(actEntry));
                    }
                } else {
                    //tag appena connesso
                    connected.add(new Entry(actEntry));
                }
            }

            logEntryData(TAG, "\nTag appena connessi: " + connected.size() + "\n", "\n", connected);
            logEntryData(TAG, "\nTag appena disconnessi: " + disconnected.size() + "\n", "\n", disconnected);
            logEntryData(TAG, "\nTag ancora connessi: " + common.size() + "\n", "\n", common);

            synchronized (listenersLock) {
                long time = System.currentTimeMillis();
                notifyToAllTagsListeners(connected, disconnected, common);
                logTimeElapsed(TAG, "\nTempo impiegato per le notifiche ai AllTagsListeners: ", System.currentTimeMillis() - time);

                time = System.currentTimeMillis();
                notifyToTagsListeners(connected, disconnected, common);
                logTimeElapsed(TAG, "\nTempo impiegato per le notifiche ai TagsListeners: ", System.currentTimeMillis() - time);
            }
        }

        private void notifyToAllTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> data)
        {
            //lock già ottenuto

            for (final AllTagsListener listener:allListeners) {
                if (connected != null && connected.size() > 0) {
                    //presenti tags connessi nell'ultimo aggiornamento dei dati
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //notifica su thread separato
                            List<Entry> connectedCopy = cloneList(connected);
                            listener.onTagHasConnected(connectedCopy);
                        }
                    }).start();
                }

                if (disconnected != null && disconnected.size() > 0) {
                    //presenti tags disconnessi nell'ultimo aggiornamento dei dati
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //notifica su thread separato
                            List<Entry> disconnectedCopy = cloneList(disconnected);
                            listener.onTagHasDisconnected(disconnectedCopy);
                        }
                    }).start();
                }

                if (data != null && data.size() > 0) {
                    //notifica i nuovi valori
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //notifica su thread separato
                            List<Entry> dataCopy = cloneList(data);
                            listener.onTagDataAvailable(dataCopy);
                        }
                    }).start();
                }
            }
        }

        private void notifyToTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> data)
        {
            //lock già ottenuto

            for (Pair<Integer, TagListener> pair:tagListeners) {
                int ID = pair.first;
                final TagListener listener = pair.second;

                //connected, disconnected e data sono ordinati, per costruzione
                final int r1 = Collections.binarySearch(connected, new Entry(ID, -1), matchingIDEntryComparator);
                if (r1 >= 0) {
                    //il tag si è appena connesso
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTagHasConnected(connected.get(r1).tagDistance);
                        }
                    }).start();

                    continue;
                }

                final int r2 = Collections.binarySearch(disconnected, new Entry(ID, -1), matchingIDEntryComparator);
                if (r2 >= 0) {
                    //il tag si è appena disconnesso
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTagHasDisconnected(disconnected.get(r2).tagDistance);
                        }
                    }).start();

                    continue;
                }

                final int r3 = Collections.binarySearch(data, new Entry(ID, -1), matchingIDEntryComparator);
                if (r3 >= 0) {
                    //il tag è ancora connesso e si notifica la nuova posizione
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTagDataAvailable(data.get(r3).tagDistance);
                        }
                    }).start();
                }
            }
        }
    }

    /**
     * Recupera le informazioni dal pacchetto di risposta del modulo DWM
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La lista di coppie id-distanza dei tag
     * @throws IllegalArgumentException Se i dati ricevuti dal modulo DWM non sono validi
     */
    private List<Entry> getDataFromDWMResponse(int[] dwmResponse) throws IllegalArgumentException
    {
        if (dwmResponse == null || dwmResponse.length < 21 || dwmResponse[2] != 0) {
            //dati non validi
            throw new IllegalArgumentException("Dati ricevuti dal modulo non validi.");
        }

        final int bytesPerEntry = 20;
        int numberOfValues = dwmResponse[20];

        List<Entry> newData = new ArrayList<>(numberOfValues);
        int startIndex = 21;

        //Nota che il modulo DWM usa notazione Little Endian!
        for (int i = 0; i < numberOfValues; i++, startIndex += bytesPerEntry)
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
     * Imposta il controller. Non avvia il polling con il modulo, che deve essere fatto manualmente tramite il metodo start()
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     * @throws IllegalArgumentException Se il busName non è valido
     * @throws IOException Se avviene un errore nella creazione del driver DWM
     */
    public DistanceController(String busName) throws IllegalArgumentException, IOException, InterruptedException {
        driverDWM = new DriverDWM(busName);
        tagListeners = new ArrayList<>();
        allListeners = new ArrayList<>();

        actualData = new ArrayList<>();
        disconnectedData = new ArrayList<>();

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
    public DistanceController(String busName, long initialDelay, long period) throws IllegalArgumentException, IOException, InterruptedException {
        this(busName);

        if (period < 0) {
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo.");
        }

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
        if (period < 0) {
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo.");
        }

        if (updateDataTimer == null) {
            updateDataTimer = new Timer(true);
            updateDataTimer.scheduleAtFixedRate(updateDataTask, initialDelay > 0 ? initialDelay : 0, period);
        }else{
            throw new IllegalStateException("Timer già avviato");
        }
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
        if (updateDataTimer != null) {
            updateDataTimer.cancel();
            updateDataTimer = null;
        }
    }

    /**
     * Chiude il DistanceController e rilascia le risorse
     */
    public void close()
    {
        if (updateDataTimer != null) {
            updateDataTimer.cancel();
            updateDataTimer = null;
        }

        try {
            driverDWM.close();
            driverDWM = null;
        } catch (IOException e) {
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
     * Ottiene l'ultima distanza nota di uno specifico tag, scelto tramite il tagID passato come parametro. Da usare esclusivamente una-tantum.
     * @param tagID ID del tag da cui ottenere la distanza
     * @return Ultima distanza nota del tag richiesto
     * @throws IllegalArgumentException Se il tagID specificato come parametro non è disponibile
     * @throws IllegalStateException Se il polling sul modulo DWM non è stato avviato con startUpdate()
     */
    public int getTagDistance(int tagID) throws IllegalArgumentException, IllegalStateException {
        if (tagID < 0 || ((tagID & 0xFFFF) != 0))
        {
            //id non valido (id numero senza segno a 16bit)
            throw new IllegalArgumentException("ID: " + tagID + " non è valido.");
        }
        if (updateDataTimer == null){
            //il polling del modulo DWM non è attivo, perciò non è possibile restituire un valore appropriato
            throw new IllegalStateException("Per poter ricevere un valore è necessario attivare il polling con il metodo startUpdate().");
        }

        synchronized (dataLock)
        {
            int result = Collections.binarySearch(actualData, new Entry(tagID, -1), matchingIDEntryComparator);
            if (result < 0) {
                //tagID non presente
                throw new IllegalArgumentException("ID: " + tagID + " non è collegato.");
            }

            return result;
        }
    }

    /**
     * Ottiene una lista contenente tutti gli id dei tag. Da usare esclusivamente una-tantum.
     * @return Lista con tutti gli id dei tag
     */
    public List<Integer> getTagIDs()
    {
        List<Integer> idsList = new ArrayList<>();

        synchronized (dataLock)
        {
            for (int i = 0; i < actualData.size(); i++)
            {
                idsList.add(actualData.get(i).tagID);
            }
        }

        return idsList;
    }
}