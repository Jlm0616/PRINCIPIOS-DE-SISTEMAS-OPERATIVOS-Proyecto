package logica.estadisticas;

import logica.BCPTerminado;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DTO inmutable con los datos calculados de UN proceso terminado.
 *
 * Es la vista "enriquecida" que las estadisticas exponen a la GUI.
 * Incluye campos derivados (duracion, turnaround, trTs) para que la
 * GUI no tenga que calcular NADA.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo representa datos. No calcula, no formatea, no imprime.
 *  - OCP: agregar campos (ej. tiempoOcioso) se hace extendiendo el
 *    constructor, no modificando la logica existente.
 *  - ISP: expone solo getters, sin setters. Inmutable.
 *  - DIP: no depende de nadie.
 *
 * PROYECTO 2 - DIAGRAMA GANTT:
 *  - Se agregan cpuAsignado e intervalos para que la GUI pueda
 *    dibujar el diagrama sin recalcular nada.
 *  - La lista de intervalos es una COPIA DEFENSIVA (inmutable desde
 *    afuera) para evitar modificaciones accidentales.
 */
public class LineaProceso {

    private final int id;
    private final int inicio;        // instante simulado de inicio (o -1)
    private final int fin;           // instante simulado de fin (o -1)
    private final int duracion;      // fin - inicio + 1 (convencion del curso)
    private final int turnaround;    // ya calculado en BCPTerminado
    private final double trTs;       // ya calculado en BCPTerminado
    private final int prioridad;
    private final int base;
    private final int alcance;

    // === NUEVOS CAMPOS (Diagrama de Gantt) ===
    private final int cpuAsignado;
    private final List<IntervaloEjecucion> intervalos;

    /**
     * Construye la linea a partir de un snapshot BCPTerminado.
     *
     * @param pt snapshot del proceso terminado
     */
    public LineaProceso(BCPTerminado pt) {
        this.id = pt.getId();
        this.inicio = pt.getTiempoInicio();
        this.fin = pt.getTiempoFin();
        this.duracion = calcularDuracion(pt);
        this.turnaround = pt.getTurnaround();
        this.trTs = pt.getTrTs();
        this.prioridad = pt.getPrioridad();
        this.base = pt.getBase();
        this.alcance = pt.getAlcance();

        // Copia defensiva de los intervalos y la CPU
        this.cpuAsignado = pt.getCpuAsignado();
        this.intervalos = new ArrayList<>(pt.getIntervalosEjecucion());
    }

    /**
     * Duracion segun la convencion del curso:
     *   duracion = fin - inicio + 1
     *
     * Si los tiempos no estan marcados (-1), devuelve 0.
     */
    private static int calcularDuracion(BCPTerminado pt) {
        int ini = pt.getTiempoInicio();
        int fin = pt.getTiempoFin();
        if (ini == BCPTerminado.TIEMPO_NO_MARCADO || fin == BCPTerminado.TIEMPO_NO_MARCADO) {
            return 0;
        }
        return fin - ini + 1;
    }

    /* ==================== GETTERS ==================== */

    public int getId()           { return id; }
    public int getInicio()       { return inicio; }
    public int getFin()          { return fin; }
    public int getDuracion()     { return duracion; }
    public int getTurnaround()   { return turnaround; }
    public double getTrTs()      { return trTs; }
    public int getPrioridad()    { return prioridad; }
    public int getBase()         { return base; }
    public int getAlcance()      { return alcance; }

    /* ==================== GETTERS GANTT ==================== */

    /**
     * @return id del nucleo en el que corrio el proceso, o -1 si N/A.
     */
    public int getCpuAsignado() {
        return cpuAsignado;
    }

    /**
     * @return lista inmutable de intervalos de ejecucion.
     */
    public List<IntervaloEjecucion> getIntervalos() {
        return Collections.unmodifiableList(intervalos);
    }

    /**
     * @return true si el proceso tiene al menos un intervalo registrado.
     */
    public boolean tieneIntervalos() {
        return !intervalos.isEmpty();
    }

    /* ==================== TOSTRING ==================== */

    @Override
    public String toString() {
        return "LineaProceso{id=" + id
                + ", cpu=" + cpuAsignado
                + ", inicio=" + inicio
                + ", fin=" + fin
                + ", duracion=" + duracion
                + ", turnaround=" + turnaround
                + ", trTs=" + String.format("%.2f", trTs)
                + ", intervalos=" + intervalos + "}";
    }
}