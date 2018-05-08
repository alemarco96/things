package group107.distancealert;

//classe controller
public class DistanceController {

    //TODO costruttore Dwm
    // prende in ingresso la scelta della comunicazione via SPI o UART
    // crea l'oggetto DriverDWM
    // se tutto va a buon fine avvia la schedulazione di questo thread ogni secondo
    public DistanceController(String busName) {

    }

    //TODO getDistance
    //ogni secondo salva su variabile locale
    public int getDistance(int id) {
        int distance=1000;

        return distance;
    }

    //ogni secondo salva su variabile locale
    //TODO scanID
    public int[] scanID(){
        int[] ids=new int[1];

        return ids;
    }
}
