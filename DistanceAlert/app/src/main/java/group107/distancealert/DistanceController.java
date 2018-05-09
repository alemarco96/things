package group107.distancealert;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

//classe controller
public class DistanceController
{
    //contiene le ultime distanze note
    private int[] lastKnownDistances;
    //contiene gli UWB_Address dei tag. La relativa distanza è memorizzata allo stesso indice
    private int[] tagAddressList;

    //oggetto usato per gestire l'accesso in mutua esclusione ai dati
    private final Object dataLock = new Object();
    //oggetto usato per gestire l'accesso in mutua esclusione ai listeners
    private final Object listenersLock = new Object();

    private DriverDWM driverDWM;
    //memorizza tutti coloro che vogliono rimanere aggiornati con il controller
    private List<DistanceDataAvailableListener> exportToListeners;

    //oggetto che, usando un thread secondario che autogestisce la sua schedulazione periodica, si occupa di effettuare
    //il polling del modulo DWM per ottenere le distanze
    private Timer updateDistanceTimer;

    //oggetto che definisce cosa viene eseguito dal updateDistanceTimer
    private TimerTask updateTask = new TimerTask()
    {
        @Override
        public void run() {
            try {
                int[] dwmResponse = driverDWM.requestAPI((byte) 0x0C, null);

                Pair<int[], int[]> data = getDataFromDWMResponse(dwmResponse);

                //clona gli array per non permettere la modifica dei dati memorizzati all'esterno
                int[] clonedTagsAddresses = Arrays.copyOf(data.first, data.first.length);
                int[] clonedDistances = Arrays.copyOf(data.second, data.second.length);

                //salva i nuovi valori
                synchronized (dataLock)
                {
                    tagAddressList = data.first;
                    lastKnownDistances = data.second;
                }

                //notifica a tutti dei nuovi valori disponibili
                synchronized (listenersLock)
                {
                    for (DistanceDataAvailableListener listener : exportToListeners) {
                        listener.onDataAvailable(clonedTagsAddresses, clonedDistances);
                    }
                }
            } catch (Exception e)
            {
                //TODO: gestire eccezione
            }
        }
    };

    /**
     * Imposta il controller. Non avvia il polling con il modulo, che deve essere fatto manualmente tramite il metodo start()
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     * @throws IllegalArgumentException Se il busName non è valido
     * @throws IOException Se avviene un errore nella creazione del driver DWM
     */
    public DistanceController(String busName) throws IllegalArgumentException, IOException, InterruptedException
    {
        driverDWM = new DriverDWM(busName);
        exportToListeners = new ArrayList<DistanceDataAvailableListener>();
        lastKnownDistances = null;
    }

    /**
     * Aggiunge un listener
     * @param listener
     */
    public void addListener(DistanceDataAvailableListener listener)
    {
        synchronized (listenersLock)
        {
            exportToListeners.add(listener);
        }
    }

    /**
     * Rimuove il listener, se presente
     * @param listener
     */
    public void removeListener(DistanceDataAvailableListener listener)
    {
        synchronized (listenersLock)
        {
            exportToListeners.remove(listener);
        }
    }

    /**
     * Inizia il polling del modulo con un periodo impostabile come parametro
     * @param period Il tempo che trascorre tra un update e il successivo
     */
    public void start(long period) throws IllegalStateException
    {
        if(updateDistanceTimer == null) {
            updateDistanceTimer = new Timer(true);
            updateDistanceTimer.scheduleAtFixedRate(updateTask, 0L, period);
        }else{
            throw new IllegalStateException("Timer già avviato");
        }
    }

    /**
     * Termina il polling del modulo
     * */
    public void stop()
    {
        if(updateDistanceTimer != null) {
            updateDistanceTimer.cancel();
            updateDistanceTimer = null;
        }
    }

    /**
     * Chiude il DistanceController e rilascia le risorse
     */
    public void close()
    {
        try {
            driverDWM.close();
            driverDWM = null;
        } catch (IOException e) {
           //ignora errore
        }

        updateDistanceTimer.cancel();
        updateDistanceTimer = null;

        synchronized (listenersLock)
        {
            exportToListeners = null;
        }
    }

    /**
     * Ottiene l'ultima distanza nota di uno specifico tag, scelto tramite il tagID passato come parametro
     * @param tagID ID del tag da cui ottenere la distanza
     * @return Ultima distanza nota del tag richiesto
     * @throws IllegalArgumentException Se il tagID specificato come parametro non è disponibile
     * @throws IllegalStateException Se il polling sul modulo DWM non è stato avviato con start()
     */
    public int getDistance(int tagID) throws IllegalArgumentException, IllegalStateException {
        if(updateDistanceTimer == null){
            //il polling del modulo DWM non è attivo, perciò non è possibile restituire un valore appropriato
            throw new IllegalStateException("Per poter ricevere un valore è necessario attivare il polling con il metodo start().");
        }

        int index = 0;

        synchronized (dataLock)
        {
            for(; index < tagAddressList.length; index++) {
                if(tagID == tagAddressList[index]){
                    //trovato tag con ID corrispondente
                    break;
                }
            }

            if(index == tagAddressList.length)
            {
                //tag con ID specificato per parametro non trovato
                throw new IllegalArgumentException("Tag con ID: " + tagID + " non è connesso al modulo.");
            }

            return lastKnownDistances[index];
        }
    }

    /**
     * Recupera le informazioni dal pacchetto di risposta del modulo DWM
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La distanza dei tag
     * @throws RuntimeException Se il modulo DWM riporta un errore nella risposta
     */
    private Pair<int[], int[]> getDataFromDWMResponse(int[] dwmResponse) throws RuntimeException
    {
        int errorCode = dwmResponse[2];

        if(errorCode != 0){
            //è avvenuto un errore
            throw new RuntimeException("Errore nella risposta del modulo.");
        }

        int numberOfValues = dwmResponse[21];
        final int bytesPerEntry = 20;

        int[] distances = new int[numberOfValues];
        int[] tags = new int[numberOfValues];
        int startIndex = 22;

        //Nota che il modulo DWM usa notazione Little Endian!
        for(int i = 0; i < numberOfValues; i++, startIndex += bytesPerEntry)
        {
            int UWB_Address = (dwmResponse[startIndex + 1] << 8) + dwmResponse[startIndex];

            int d1 = dwmResponse[startIndex + 2];
            int d2 = dwmResponse[startIndex + 3];
            int d3 = dwmResponse[startIndex + 4];
            int d4 = dwmResponse[startIndex + 5];

            int distance = (d4 << 24) + (d3 << 16) + (d2 << 8) + d1;

            tags[i] = UWB_Address;
            distances[i] = distance;
        }

        return new Pair<int[], int[]>(tags, distances);
    }
}