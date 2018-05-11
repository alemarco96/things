package group107.distancealert;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

//classe controller
public class DistanceController
{
    //rappresenta i dati del controller. creata per non creare confusione usando la classe standard Pair
    public class Entry
    {
        public final int tagID;
        public final int tagDistance;

        private Entry(int id, int distance)
        {
            tagID = id;
            tagDistance = distance;
        }

        public boolean equals(Object obj)
        {
            if(!(obj instanceof Entry))
                return false;

            Entry entry = (Entry) obj;
            return (tagID == entry.tagID) && (tagDistance == entry.tagDistance);
        }
    }

    //memorizza i dati attuali
    private List<Entry> actualData;

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
                int[] dwmResponse = driverDWM.requestAPI((byte) 0x0C, null);

                List<Entry> newData = getDataFromDWMResponse(dwmResponse);
                //ordina gli elementi per tagID crescente, in modo tale da velocizzare le operazioni successive
                Collections.sort(newData, entryComparator);

                //copia i dati per poter essere utilizzati senza avere la mutua esclusione dataLock
                List<Entry> actualDataCopy;
                List<Entry> newDataCopy = new ArrayList<>(newData.size());
                Collections.copy(newDataCopy, newData);

                //salva i nuovi valori
                synchronized (dataLock)
                {
                    actualDataCopy = new ArrayList<>(actualData.size());
                    Collections.copy(actualDataCopy, actualData);

                    actualData = newData;
                }

                //svolge il lavoro di notifica su thread separato per non bloccare il thread di aggiornamento
                WorkerThread workerThread = new WorkerThread(actualDataCopy, newDataCopy);
                workerThread.start();
            } catch (Exception e)
            {
                //TODO: gestire eccezione
            }
        }
    };

    private Comparator<Entry> entryComparator = new Comparator<Entry>() {
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
        }

        public void run()
        {
            List<Entry> common = new ArrayList<>(actData.size() > prevData.size() ? actData.size() : prevData.size());
            List<Entry> connected = new ArrayList<>();

            for(int i = 0; i < actData.size(); i++) {
                Entry entry = actData.get(i);

                //ricerca di entry in prevData con lo stesso tagID
                int result = Collections.binarySearch(prevData, entry, entryComparator);

                if(result >= 0) {
                    //tagID dell'entry presente anche in prevData =>tag che è rimasto connesso
                    common.add(new Entry(entry.tagID, entry.tagDistance));

                    //serve per far rimanere in prevData solo le entry dei tag disconnessi
                    prevData.remove(result);
                } else {
                    //tag appena connesso
                    connected.add(new Entry(entry.tagID, entry.tagDistance));
                }
            }

            //usato solo per chiarezza di lettura del codice
            List<Entry> disconnected = prevData;

            synchronized (listenersLock) {
                notifyToAllTagsListeners(connected, disconnected, common);
                notifyToTagsListeners(connected, disconnected, common);
            }
        }

        private void notifyToAllTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> data)
        {
            //lock già ottenuto

            for(final AllTagsListener listener:allListeners) {
                if(connected != null && connected.size() > 0) {
                    //presenti tags connessi nell'ultimo aggiornamento dei dati
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //notifica su thread separato
                            List<Entry> connectedCopy = new ArrayList<>(connected.size());
                            Collections.copy(connectedCopy, connected);
                            listener.onTagHasConnected(connectedCopy);
                        }
                    }).start();
                }

                if(disconnected != null && disconnected.size() > 0) {
                    //presenti tags disconnessi nell'ultimo aggiornamento dei dati
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //notifica su thread separato
                            List<Entry> disconnectedCopy = new ArrayList<>(disconnected.size());
                            Collections.copy(disconnectedCopy, disconnected);
                            listener.onTagHasDisconnected(disconnectedCopy);
                        }
                    }).start();
                }

                if(data != null && data.size() > 0) {
                    //notifica i nuovi valori
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //notifica su thread separato
                            List<Entry> dataCopy = new ArrayList<>(data.size());
                            Collections.copy(dataCopy, data);
                            listener.onTagDataAvailable(dataCopy);
                        }
                    }).start();
                }
            }
        }

        private void notifyToTagsListeners(final List<Entry> connected, final List<Entry> disconnected, final List<Entry> data)
        {
            //lock già ottenuto

            for(Pair<Integer, TagListener> pair:tagListeners) {
                int ID = pair.first;
                final TagListener listener = pair.second;

                //connected, disconnected e data DOVREBBERO essere ordinati, per costruzione
                final int r1 = Collections.binarySearch(connected, new Entry(ID, -1), entryComparator);
                if(r1 > 0) {
                    //il tag si è appena connesso
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTagHasConnected(connected.get(r1).tagDistance);
                        }
                    }).start();

                    continue;
                }

                final int r2 = Collections.binarySearch(disconnected, new Entry(ID, -1), entryComparator);
                if(r2 > 0) {
                    //il tag si è appena disconnesso
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTagHasDisconnected(disconnected.get(r2).tagDistance);
                        }
                    }).start();

                    continue;
                }

                final int r3 = Collections.binarySearch(data, new Entry(ID, -1), entryComparator);
                if(r3 > 0) {
                    //il tag è ancora connesso e si notifica la nuova posizione
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTagDataAvailable(data.get(r3).tagDistance);
                        }
                    }).start();
                }

                //tag non più connesso. Non c'è niente da fare
            }
        }
    }

    /**
     * Imposta il controller. Non avvia il polling con il modulo, che deve essere fatto manualmente tramite il metodo start()
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

        if(period < 0) {
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
            for(int i = 0; i < tagListeners.size(); i++)
            {
                Pair<Integer, TagListener> pair = tagListeners.get(i);

                if(pair.second.equals(listener))
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
        if(period < 0) {
            throw new IllegalArgumentException("Il periodo di aggiornamento deve essere positivo.");
        }

        if(updateDataTimer == null) {
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
        if(updateDataTimer != null) {
            updateDataTimer.cancel();
            updateDataTimer = null;
        }
    }

    /**
     * Chiude il DistanceController e rilascia le risorse
     */
    public void close()
    {
        updateDataTimer.cancel();
        updateDataTimer = null;

        try {
            driverDWM.close();
            driverDWM = null;
        } catch (IOException e) {
            //ignora errore
        }

        synchronized (listenersLock)
        {
            allListeners = null;
            tagListeners = null;
        }
    }

    /**
     * Ottiene l'ultima distanza nota di uno specifico tag, scelto tramite il tagID passato come parametro
     * @param tagID ID del tag da cui ottenere la distanza
     * @return Ultima distanza nota del tag richiesto
     * @throws IllegalArgumentException Se il tagID specificato come parametro non è disponibile
     * @throws IllegalStateException Se il polling sul modulo DWM non è stato avviato con startUpdate()
     */
    public int getTagDistance(int tagID) throws IllegalArgumentException, IllegalStateException {
        if(tagID < 0 || ((tagID & 0xFFFF) != 0))
        {
            //id non valido (id numero senza segno a 16bit)
            throw new IllegalArgumentException("ID: " + tagID + " non è valido.");
        }
        if(updateDataTimer == null){
            //il polling del modulo DWM non è attivo, perciò non è possibile restituire un valore appropriato
            throw new IllegalStateException("Per poter ricevere un valore è necessario attivare il polling con il metodo startUpdate().");
        }

        synchronized (dataLock)
        {
            int result = Collections.binarySearch(actualData, new Entry(tagID, -1), entryComparator);
            if(result < 0) {
                //tagID non presente
                throw new IllegalArgumentException("ID: " + tagID + " non è collegato.");
            }

            return result;
        }
    }

    /**
     * Ottiene una lista contenente tutti gli id dei tag
     * @return Lista con tutti gli id dei tag
     */
    public List<Integer> getTagIDs()
    {
        List<Integer> idsList = new ArrayList<>();

        synchronized (dataLock)
        {
            for(Entry entry:actualData)
            {
                idsList.add(entry.tagID);
            }
        }

        return idsList;
    }

    /**
     * Recupera le informazioni dal pacchetto di risposta del modulo DWM
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La distanza dei tag
     * @throws RuntimeException Se il modulo DWM riporta un errore nella risposta
     */
    private List<Entry> getDataFromDWMResponse(int[] dwmResponse) throws RuntimeException
    {
        int errorCode = dwmResponse[2];

        if(errorCode != 0){
            //è avvenuto un errore
            throw new RuntimeException("Errore nella risposta del modulo.");
        }

        int numberOfValues = dwmResponse[21];
        final int bytesPerEntry = 20;

        List<Entry> newData = new ArrayList<>(numberOfValues);
        int startIndex = 22;

        //Nota che il modulo DWM usa notazione Little Endian!
        for(int i = 0; i < numberOfValues; i++, startIndex += bytesPerEntry)
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
}