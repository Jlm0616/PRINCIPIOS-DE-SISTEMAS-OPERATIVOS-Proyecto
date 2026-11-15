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
    private final int tiempoInicio;
    private final int tiempoFin;
    private final double promedioTurnaround;
    private final int cantidadCPUs;
    private final String estrategiaMemoria;
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
                            int tiempoInicio,
                            int tiempoFin,
                            double promedioTurnaround,
                            int cantidadCPUs,
                            String estrategiaMemoria,
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
        this.tiempoInicio = tiempoInicio;
        this.tiempoFin = tiempoFin;
        this.promedioTurnaround = promedioTurnaround;
        this.cantidadCPUs = cantidadCPUs;
        this.estrategiaMemoria = (estrategiaMemoria != null) ? estrategiaMemoria : "-";
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
     * @param memoria      nombre de la estrategia de memoria usada
     * @param error        mensaje si la ejecucion termino con error, o null
     * @return el resumen listo para guardar en el historial
     */
    public static ResumenEjecucion desde(int numero,
                                         String algoritmo,
                                         Estadisticas stats,
                                         String memoria,
                                         String error) {
        if (stats == null) {
            return new ResumenEjecucion(numero, algoritmo, 0, 0, 0, 0, 0, 0, 0.0,
                    0, 0, 0.0, 0, memoria, error != null, error);
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
                stats.getTiempoInicio(),
                stats.getTiempoFin(),
                stats.getPromedioTurnaround(),
                stats.getCantidadNucleos(),
                memoria,
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
    public int getTiempoInicio()      { return tiempoInicio; }
    public int getTiempoFin()         { return tiempoFin; }
    public double getPromedioTurnaround() { return promedioTurnaround; }
    public int getCantidadCPUs()      { return cantidadCPUs; }
    public String getEstrategiaMemoria() { return estrategiaMemoria; }
    public boolean terminoConError()  { return terminoConError; }
    public String getErrorEjecucion() { return errorEjecucion; }
}