package logica.estadisticas;

/**
 * Resumen de UNA ejecucion completa: los datos que la rubrica pide
 * como "estadisticas de la ejecucion" y que se acumulan entre corridas.
 *
 * Es un DTO inmutable: una vez construido, no cambia.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo guarda los datos de una ejecucion. No calcula nada.
 *  - Inmutable: no tiene setters, todos los campos son final.
 */
public final class ResumenEjecucion {

    private final int numeroEjecucion;
    private final String algoritmo;
    private final int cantidadProcesos;
    private final int duracionTotal;
    private final int duracionPromedio;
    private final int duracionMasLarga;
    private final int duracionMasCorta;
    private final int tiempoOcioso;
    private final double promedioTrTs;
    private final boolean terminoConError;
    private final String errorEjecucion;

    public ResumenEjecucion(int numeroEjecucion,
                            String algoritmo,
                            int cantidadProcesos,
                            int duracionTotal,
                            int duracionPromedio,
                            int duracionMasLarga,
                            int duracionMasCorta,
                            int tiempoOcioso,
                            double promedioTrTs,
                            boolean terminoConError,
                            String errorEjecucion) {
        this.numeroEjecucion = numeroEjecucion;
        this.algoritmo = (algoritmo != null) ? algoritmo : "(desconocido)";
        this.cantidadProcesos = cantidadProcesos;
        this.duracionTotal = duracionTotal;
        this.duracionPromedio = duracionPromedio;
        this.duracionMasLarga = duracionMasLarga;
        this.duracionMasCorta = duracionMasCorta;
        this.tiempoOcioso = tiempoOcioso;
        this.promedioTrTs = promedioTrTs;
        this.terminoConError = terminoConError;
        this.errorEjecucion = errorEjecucion;
    }

    /**
     * Construye un resumen a partir de las estadisticas de la ejecucion
     * que acaba de terminar.
     *
     * @param numero       numero de ejecucion (1, 2, 3, ...)
     * @param algoritmo    nombre del algoritmo activo
     * @param stats        estadisticas calculadas
     * @param error        mensaje si la ejecucion termino con error, o null
     * @return el resumen listo para guardar en el historial
     */
    public static ResumenEjecucion desde(int numero,
                                         String algoritmo,
                                         Estadisticas stats,
                                         String error) {
        if (stats == null) {
            return new ResumenEjecucion(numero, algoritmo, 0, 0, 0, 0, 0, 0, 0.0,
                    error != null, error);
        }
        return new ResumenEjecucion(
                numero,
                algoritmo,
                stats.getCantidadProcesos(),
                stats.getDuracionTotal(),
                stats.getDuracionPromedio(),
                stats.getDuracionMasLarga(),
                stats.getDuracionMasCorta(),
                stats.getTiempoOcioso(),
                stats.getPromedioTrTs(),
                error != null,
                error);
    }

    public int getNumeroEjecucion()   { return numeroEjecucion; }
    public String getAlgoritmo()      { return algoritmo; }
    public int getCantidadProcesos()  { return cantidadProcesos; }
    public int getDuracionTotal()     { return duracionTotal; }
    public int getDuracionPromedio()  { return duracionPromedio; }
    public int getDuracionMasLarga()  { return duracionMasLarga; }
    public int getDuracionMasCorta()  { return duracionMasCorta; }
    public int getTiempoOcioso()      { return tiempoOcioso; }
    public double getPromedioTrTs()   { return promedioTrTs; }
    public boolean terminoConError()  { return terminoConError; }
    public String getErrorEjecucion() { return errorEjecucion; }
}