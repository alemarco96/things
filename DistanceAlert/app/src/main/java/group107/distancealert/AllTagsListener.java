package group107.distancealert;

import java.util.List;

/**
 * Interfaccia utile per gestire in maniera asincrona i dati ricevuti dal DistanceController.
 * Gestisce i dati relativi a tutti i tag connessi con il controller.
 */
@SuppressWarnings("WeakerAccess")
public interface AllTagsListener
{
    /**
     * Callback usata per fornire i dati relativi ai tag che si sono appena connessi con il modulo DWM.
     *
     * @param tags Lista dei tag appena connessi.
     */
    void onTagHasConnected(final List<DistanceController.Entry> tags);

    /**
     * Callback usata per fornire i dati relativi ai tag che si sono appena disconnessi dal modulo DWM.
     *
     * @param tags Lista dei tag appena disconnessi.
     */
    void onTagHasDisconnected(final List<DistanceController.Entry> tags);

    /**
     * Callback usata per fornire i dati relativi ai tag di cui sono disponibili nuovi dati.
     *
     * @param tags Lista dei tag aggiornati.
     */
    void onTagDataAvailable(final List<DistanceController.Entry> tags);

    /**
     * Callback usata in presenza di errori con il modulo hardware.
     *
     * @param shortDescription Descrizione sintetica dell'errore.
     * @param error Eccezione relativa all'errore.
     */
    @SuppressWarnings("unused")
    void onError(final String shortDescription, final Exception error);
}