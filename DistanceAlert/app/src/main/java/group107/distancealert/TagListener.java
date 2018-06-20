package group107.distancealert;

/**
 * Interfaccia utile per gestire in maniera asincrona i dati ricevuti dal DistanceController.
 * Questo listener gestisce uno specifico tag.
 */
@SuppressWarnings("WeakerAccess")
public interface TagListener
{
    /**
     * Callback usata nel caso in cui il tag si è appena connesso con il modulo DWM.
     * @param tagDistance Distanza del tag.
     */
    void onTagHasConnected(final int tagDistance);

    /**
     * Callback usata nel caso in cui il tag si è appena disconnesso dal modulo DWM.
     * @param tagLastKnownDistance Ultima distanza nota del tag.
     */
    @SuppressWarnings("unused")
    void onTagHasDisconnected(final int tagLastKnownDistance);

    /**
     * Callback usata in caso di nuovi dati disponibili per il tag dal modulo DWM.
     * @param tagDistance Distanza del tag.
     */
    void onTagDataAvailable(final int tagDistance);

    /**
     * Callback usata in presenza di errori con il modulo hardware.
     * @param shortDescription Descrizione sintetica dell'errore.
     * @param error Eccezione relativa all'errore.
     */
    @SuppressWarnings({"EmptyMethod", "unused"})
    void onError(final String shortDescription, final Exception error);
}