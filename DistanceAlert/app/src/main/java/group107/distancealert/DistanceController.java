package group107.distancealert;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

//classe controller
public class DistanceController {

    //TODO: come gestiamo il fatto che si possono collegare più ancore al modulo?
    private int[] lastKnownDistance;
    //oggetto usato per gestire l'accesso in mutua esclusione a lastKnownDistance
    private final Object lockOfLastKnownDistance = new Object();

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
                synchronized (lockOfLastKnownDistance)
                {
                    lastKnownDistance = distances;
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
    }

    /**
     * Recupera le informazioni sulle distanze delle ancore dal pacchetto di risposta del modulo DWM
     * @param dwmResponse Il pacchetto di risposta del modulo DWM
     * @return La distanza delle ancore
     */
    private int[] getDistanceFromDWMResponse(int[] dwmResponse)
    {
        //TODO: recuperare la distanza dalla risposta ottenuta
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
        lastKnownDistance = null;
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

        synchronized (lockOfLastKnownDistance)
        {
            return lastKnownDistance[getIndexOfID(id)];
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
