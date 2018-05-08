package group107.distancealert;

import java.util.List;

//Classe utile per comunicazione con modulo DWM1001-Dev collegato tramite SPI alla Raspberry
public class Dwm {


    //TODO costruttore Dwm
    //verifica che si sia un modulo collegato/resetta la comunicazione
    // se tutto va a buon fine avvia la schedulazione di questo thread ogni secondo
    public Dwm(String busName) {

    }
    //TODO getDistance
    //metodi per la gestione SPI
    //ogni secondo salva su variabile locale
    public int getDistance(int id) {
        int distance;

        return distance;
    }
    //TODO scanID
    public List<Integer> scanID(){

    }

    //TODO getPacket
    //ricevi pacchetto, Thread separato
    private List<Integer> getPacket(int type, int[] value) {

    }

}
