package logica;

import logica.estadisticas.IntervaloEjecucion;
import modelo.EstadoProceso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot de un BCP terminado.
 *
 * Cuando un proceso termina, se copian sus datos importantes a este
 * objeto y se libera el BCP de la memoria (para que el kernel no se
 * llene con BCPs terminados).
 *
 * Sigue el modelo del Stallings (seccion 3.2): la informacion del
 * proceso se preserva temporalmente para que programas auxiliares
 * (estadisticas, contabilidad) extraigan los datos que necesiten.
 *
 * Proyecto 2:
 *  - Se agregan campos para las estadisticas acumulativas
 *    (tiempoLlegada, turnaround, trTs).
 *  - Los tiempos de inicio/fin ahora son INT (reloj global simulado),
 *    NO LocalDateTime. Esto corrige el bug de estadisticas que
 *    mostraban tiempo real en vez de tiempo simulado.
 *  - El centinela -1 indica "no marcado".
 *  - Se agrega la lista de IntervaloEjecucion (para el diagrama
 *    de Gantt por CPU) y el id de la CPU en la que corrio.
 */
public class BCPTerminado {

    /** Centinela: el tiempo simulado aun no fue marcado. */
    public static final int TIEMPO_NO_MARCADO = -1;

    private final int id;
    private final EstadoProceso estadoFinal;
    private final int tiempoInicio;
    private final int tiempoFin;
    private final int base;
    private final int alcance;
    private final int prioridad;

    // === NUEVOS CAMPOS (Proyecto 2) ===
    private final int tiempoLlegada;
    private final int turnaround;
    private final double trTs;

    // === NUEVOS CAMPOS (Diagrama de Gantt) ===
    private final int cpuAsignado;
    private final List<IntervaloEjecucion> intervalosEjecucion;

    /**
     * Constructor completo (con CPU e intervalos).
     *
     * @param id                id del proceso
     * @param estadoFinal       estado al terminar (EXIT)
     * @param tiempoInicio      instante simulado de inicio (o -1)
     * @param tiempoFin         instante simulado de fin (o -1)
     * @param base              direccion base en memoria
     * @param alcance           tamano del proceso
     * @param prioridad         prioridad original
     * @param tiempoLlegada     instante de llegada
     * @param turnaround        TF - TL + 1
     * @param trTs              turnaround / rafaga
     * @param cpuAsignado       id del nucleo donde termino (-1 si N/A)
     * @param intervalos        lista de intervalos de ejecucion
     */
    public BCPTerminado(int id, EstadoProceso estadoFinal,
                        int tiempoInicio, int tiempoFin,
                        int base, int alcance, int prioridad,
                        int tiempoLlegada, int turnaround, double trTs,
                        int cpuAsignado,
                        List<IntervaloEjecucion> intervalos) {
        this.id = id;
        this.estadoFinal = estadoFinal;
        this.tiempoInicio = tiempoInicio;
        this.tiempoFin = tiempoFin;
        this.base = base;
        this.alcance = alcance;
        this.prioridad = prioridad;
        this.tiempoLlegada = tiempoLlegada;
        this.turnaround = turnaround;
        this.trTs = trTs;
        this.cpuAsignado = cpuAsignado;
        this.intervalosEjecucion = (intervalos != null)
                ? new ArrayList<>(intervalos)
                : new ArrayList<>();
    }

    /**
     * Constructor legacy (sin CPU ni intervalos).
     * Mantiene compatibilidad con codigo existente.
     */
    public BCPTerminado(int id, EstadoProceso estadoFinal,
                        int tiempoInicio, int tiempoFin,
                        int base, int alcance, int prioridad,
                        int tiempoLlegada, int turnaround, double trTs) {
        this(id, estadoFinal, tiempoInicio, tiempoFin,
             base, alcance, prioridad,
             tiempoLlegada, turnaround, trTs,
             -1, new ArrayList<>());
    }

    /* ==================== GETTERS BASICOS ==================== */

    public int getId() {
        return id;
    }

    public EstadoProceso getEstado() {
        return estadoFinal;
    }

    /**
     * Devuelve el reloj simulado en que el proceso comenzo.
     * @return el instante simulado, o {@link #TIEMPO_NO_MARCADO}.
     */
    public int getTiempoInicio() {
        return tiempoInicio;
    }

    /**
     * Devuelve el reloj simulado en que el proceso termino.
     * @return el instante simulado, o {@link #TIEMPO_NO_MARCADO}.
     */
    public int getTiempoFin() {
        return tiempoFin;
    }

    public int getBase() {
        return base;
    }

    public int getAlcance() {
        return alcance;
    }

    public int getPrioridad() {
        return prioridad;
    }

    /* ==================== GETTERS PROYECTO 2 ==================== */

    // Devuelve el tiempo de llegada del proceso.
    public int getTiempoLlegada() {
        return tiempoLlegada;
    }

    // Devuelve el turnaround (tiempo final - tiempo de llegada + 1).
    public int getTurnaround() {
        return turnaround;
    }

    // Devuelve Tr/Ts (turnaround / service time).
    public double getTrTs() {
        return trTs;
    }

    /* ==================== GETTERS DIAGRAMA GANTT ==================== */

    /**
     * @return id del nucleo donde corrio el proceso, o -1 si no aplica.
     */
    public int getCpuAsignado() {
        return cpuAsignado;
    }

    /**
     * @return lista inmutable de intervalos de ejecucion.
     */
    public List<IntervaloEjecucion> getIntervalosEjecucion() {
        return Collections.unmodifiableList(intervalosEjecucion);
    }

    /**
     * @return true si el proceso tiene al menos un intervalo registrado.
     */
    public boolean tieneIntervalos() {
        return !intervalosEjecucion.isEmpty();
    }

    /* ==================== DERIVADOS ==================== */

    /**
     * Devuelve la duracion simulada (tiempoFin - tiempoInicio),
     * o -1 si faltan tiempos.
     *
     * NOTA: esta es la duracion "Stallings" (sin +1). Para la
     * duracion con la convencion del curso (fin - inicio + 1), usar
     * LineaProceso.getDuracion().
     */
    public int getDuracionSimulada() {
        if (tiempoInicio == TIEMPO_NO_MARCADO || tiempoFin == TIEMPO_NO_MARCADO) {
            return -1;
        }
        return tiempoFin - tiempoInicio;
    }

    @Override
    public String toString() {
        return "BCPTerminado[id=" + id
                + ", cpu=" + cpuAsignado
                + ", inicio=" + tiempoInicio
                + ", fin=" + tiempoFin
                + ", turnaround=" + turnaround
                + ", trTs=" + String.format("%.2f", trTs)
                + ", intervalos=" + intervalosEjecucion.size() + "]";
    }
}