package logica;

import modelo.EstadoProceso;

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

    // Crea un snapshot con los datos del proceso terminado.
    public BCPTerminado(int id, EstadoProceso estadoFinal,
                        int tiempoInicio, int tiempoFin,
                        int base, int alcance, int prioridad,
                        int tiempoLlegada, int turnaround, double trTs) {
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
    }

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

    // === NUEVOS GETTERS (Proyecto 2) ===

    // Devuelve el tiempo de llegada del proceso.
    public int getTiempoLlegada() {
        return tiempoLlegada;
    }

    // Devuelve el turnaround (tiempo final - tiempo de llegada).
    public int getTurnaround() {
        return turnaround;
    }

    // Devuelve Tr/Ts (turnaround / service time).
    public double getTrTs() {
        return trTs;
    }

    /**
     * Devuelve la duracion simulada (tiempoFin - tiempoInicio),
     * o -1 si faltan tiempos.
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
                + ", inicio=" + tiempoInicio
                + ", fin=" + tiempoFin
                + ", turnaround=" + turnaround
                + ", trTs=" + String.format("%.2f", trTs) + "]";
    }
}