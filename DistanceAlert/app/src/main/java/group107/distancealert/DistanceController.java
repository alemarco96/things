package group107.distancealert;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

//classe controller
public class DistanceController {

    //TODO: come gestiamo il fatto che si possono collegare più ancore al modulo?
    private int[] lastKnownDistances;
    //oggetto usato per gestire l'accesso in mutua esclusione a lastKnownDistances
    private final Object lockOfLastKnownDistances = new Object();

    private DriverDWM driverDWM;
    private byte tag;
    private byte[] request;

    //oggetto che, usando un thread secondario e gestendo automaticamente la sua schedulazione periodica, si occupa di effettuare
    // il polling del modulo DWM per ottenere le distanze
    private Timer updateDistanceTimer;

    //oggetto che definisce cosa viene eseguito dal updateDistanceTimer
    private TimerTask updateTask = new TimerTask() {
        @Override
        public void run() {
            try {
                int[] dwmResponse = driverDWM.requestAPI(tag, request);

                int[] distances = getDistanceFromDWMResponse(dwmResponse);

                //salva i nuovi valori
                synchronized (lockOfLastKnownDistances)
                {
                    lastKnownDistances = distances;
                }
            } catch (Exception e)
            {
                //TODO: gestire eccezione
            }
        }
    };

    /**
     * Imposta tag e buffer contenente la richiesta da inviare al modulo DWM per ottenere le distanze delle ancore
     */
    private void setupRequestForDriver()
    {
        //TODO: impostare tag e buffer con la richiesta

        /*

        //Il tag è relativo a dwm_pos_get?

        tag = 0x02;
        request = null; //non ci sono altri valori da dargli in pasto

         */
    }

    /**
     * Recupera le informazioni sulle distanze delle ancore dal pacchetto di risposta del modulo DWM
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La distanza delle ancore
     */
    private int[] getDistanceFromDWMResponse(int[] dwmResponse)
    {
        //TODO: recuperare la distanza dalla risposta ottenuta

        /*
        Nota che il modulo adotta la convenzione Little Endian!

        TLV response:
        -8 bit: type(sempre 0x40)
        -8 bit: length(numero di value che ti comunica, pari al numero di ancore connesse)
        -8 bit: errcode(0 per ok)

        value:
            -8 bit: type(sempre 0x41)
            -8 bit length(13 byte, sempre 0x0D)
            -32 bit: posizione x
            -32 bit: posizione y
            -32 bit: posizione z
            -8 bit: quality factor
        */

        /*

        int numberOfValues = dwmResponse[1];
        int errorCode = dwmResponse[2];

        if(errorCode != 0 || numberOfValues == 0){
            //è avvenuto un errore
            //TODO: gestire errore
        }

        int[] distances = new int[numberOfValues];

        int startIndex = 3;

        for(int i = 0; i < numberOfValues; i++)
        {
            int index = startIndex + 15 * i + 2;
            int x1 = dwmResponse[index];
            int x2 = dwmResponse[index + 1];
            int x3 = dwmResponse[index + 2];
            int x4 = dwmResponse[index + 3];

            //il modulo usa convenzione Little Endian!
            long x = x4 << 24 + x3 <<16 + x2 << 8 + x1;

            index = startIndex + 15 * i + 6;
            int y1 = dwmResponse[index];
            int y2 = dwmResponse[index + 1];
            int y3 = dwmResponse[index + 2];
            int y4 = dwmResponse[index + 3];

            //il modulo usa convenzione Little Endian!
            long y = y4 << 24 + y3 <<16 + y2 << 8 + y1;

            index = startIndex + 15 * i + 10;
            int z1 = dwmResponse[index];
            int z2 = dwmResponse[index + 1];
            int z3 = dwmResponse[index + 2];
            int z4 = dwmResponse[index + 3];

            //il modulo usa convenzione Little Endian!
            long z = z4 << 24 + z3 <<16 + z2 << 8 + z1;

            //come usarlo? per ora è inutile...
            int qualityFactor = dwmResponse[startIndex + 15 * i + 14];

            //calcolo della distanza
            long modulo = (x * x) + (y * y) + (z * z);

            int distance = (int) Math.round((long) modulo);
            distances[i] = distance;
        }

        return distances;
        */

        return null;
    }


    /**
     * Imposta il controller. Non avvia il polling con il modulo, che deve essere fatto tramite start()
     * @param busName Il nome del pin a cui è collegato fisicamente il modulo
     */
    public DistanceController(String busName) {
        try {
            driverDWM = new DriverDWM(busName);

            setupRequestForDriver();
        } catch (IllegalArgumentException e1)
        {
            //busName parametro non valido
            //TODO: gestire eccezione
        } catch (Exception e) {
            //avvenuto errore nella creazione dell'istanza del driver
            //TODO: gestire eccezione
        }
        lastKnownDistances = null;
    }

    /**
     * Inizia il polling del modulo con un periodo impostabile come parametro
     * @param period Il tempo che trascorre tra un update e il successivo
     */
    public void start(long period)
    {
        if(updateDistanceTimer == null) {
            updateDistanceTimer = new Timer(true);
            updateDistanceTimer.scheduleAtFixedRate(updateTask, 0L, period);
        }else{
            //timer già avviato. TODO: ignorare nuova richiesta, generare eccezione o sovrascrivere?
        }
    }

    /**
     * Termina il polling del modulo
     */
    public void stop()
    {
        if(updateDistanceTimer != null) {
            updateDistanceTimer.cancel();
            updateDistanceTimer = null;
        }
    }

    /**
     * Ottiene l'ultima distanza nota da una specifica ancora, scelta passandone l'id come parametro
     * @param id id dell'ancora da cui ottenere la distanza
     * @return int in cui è memorizzata l'ultima distanza nota dell'ancora richiesta
     * @throws IOException se il polling sul modulo dwm non è stato avviato con start()
     */
    public int getDistance(int id) throws IOException {
        if(updateDistanceTimer == null){
            //TODO: trovare eccezione da lanciare più opportuna e rivedere il messaggio
            throw new IOException("Il modulo non stà controllando la distanza.");
        }

        synchronized (lockOfLastKnownDistances)
        {
            return lastKnownDistances[getIndexOfID(id)];
        }
    }

    private int getIndexOfID(int id)
    {
        //TODO: come sono assegnati gli id alle ancore e come ottenere l'indice dell'array corrispondente?
        return -1;
    }

    //ogni secondo salva su variabile locale
    //TODO scanID
    public int[] scanID(){
        int[] ids=new int[1];
// da chiedere: ((byte)0x0c , null) al metodo requestAPI
        return ids;
    }
}
