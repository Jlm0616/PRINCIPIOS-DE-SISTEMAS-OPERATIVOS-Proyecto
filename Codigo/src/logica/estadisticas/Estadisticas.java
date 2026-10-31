package logica.estadisticas;

import logica.BCPTerminado;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Calculadora de estadisticas agregadas a partir de una lista de
 * BCPTerminado.
 *
 * Es la unica clase responsable de calcular:
 *   - Duracion total (suma).
 *   - Duracion promedio.
 *   - Proceso mas largo / mas corto.
 *   - Promedio de Tr/Ts.
 *   - Lista de LineaProceso (DTO por proceso).
 *   - Rango de instantes y CPUs presentes (para el diagrama de Gantt).
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo CALCULA. No formatea, no imprime, no conoce la GUI.
 *  - OCP: si se agrega "tiempo ocioso" o "promedio de turnaround",
 *    se agrega un campo y su calculo, sin romper lo existente.
 *  - ISP: expone getters concretos. No obliga a la GUI a implementar
 *    metodos que no usa.
 *  - DIP: depende de BCPTerminado (modelo), no de la GUI ni de nada
 *    concreto de presentacion.
 *  - DRY: las formulas de duracion, promedio, etc. viven AQUI. La GUI
 *    ya no las duplica.
 */
public class Estadisticas {

    private final List<LineaProceso> lineas;
    private final int duracionTotal;
    private final int duracionPromedio;
    private final int duracionMasLarga;
    private final int duracionMasCorta;
    private final double promedioTrTs;

    // === NUEVOS CAMPOS (Diagrama de Gantt) ===
    /** Instante minimo registrado (0 si no hay datos). */
    private final int instanteMin;
    /** Instante maximo registrado (0 si no hay datos). */
    private final int instanteMax;
    /** IDs de CPUs presentes (ordenados ascendente). */
    private final List<Integer> cpusPresentes;

    /**
     * Construye las estadisticas a partir de la lista de procesos
     * terminados.
     *
     * @param terminados lista de snapshots BCPTerminado
     */
    public Estadisticas(List<BCPTerminado> terminados) {
        if (terminados == null) {
            terminados = Collections.emptyList();
        }

        // 1. Construir la lista de LineaProceso (DTO por proceso)
        this.lineas = new ArrayList<>(terminados.size());
        for (BCPTerminado pt : terminados) {
            lineas.add(new LineaProceso(pt));
        }

        // 2. Calcular agregados
        int total = 0;
        int masLargo = 0;
        int masCorto = Integer.MAX_VALUE;
        double sumaTrTs = 0.0;
        int conTrTs = 0;

        for (LineaProceso lp : lineas) {
            int dur = lp.getDuracion();
            if (dur > 0) {
                total += dur;
                if (dur > masLargo) masLargo = dur;
                if (dur < masCorto) masCorto = dur;
            }
            if (lp.getTrTs() > 0) {
                sumaTrTs += lp.getTrTs();
                conTrTs++;
            }
        }

        if (masCorto == Integer.MAX_VALUE) masCorto = 0;

        this.duracionTotal = total;
        this.duracionPromedio = lineas.isEmpty() ? 0 : total / lineas.size();
        this.duracionMasLarga = masLargo;
        this.duracionMasCorta = masCorto;
        this.promedioTrTs = (conTrTs > 0) ? (sumaTrTs / conTrTs) : 0.0;

        // 3. Calcular instantes y CPUs presentes (para el Gantt)
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        Set<Integer> cpus = new TreeSet<>();   // ordenado ascendente

        for (LineaProceso lp : lineas) {
            int cpu = lp.getCpuAsignado();
            if (cpu >= 0) cpus.add(cpu);

            for (IntervaloEjecucion ie : lp.getIntervalos()) {
                if (ie.getInicio() < min) min = ie.getInicio();
                if (ie.getFin() > max) max = ie.getFin();
            }
        }

        this.instanteMin = (min == Integer.MAX_VALUE) ? 0 : min;
        this.instanteMax = (max == Integer.MIN_VALUE) ? 0 : max;
        this.cpusPresentes = new ArrayList<>(cpus);
    }

    /* ==================== GETTERS ==================== */

    /**
     * @return lista de LineaProceso (uno por proceso terminado).
     */
    public List<LineaProceso> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    /**
     * @return cantidad de procesos terminados.
     */
    public int getCantidadProcesos() {
        return lineas.size();
    }

    /**
     * @return suma de duraciones (con la convencion fin - inicio + 1).
     */
    public int getDuracionTotal() {
        return duracionTotal;
    }

    /**
     * @return duracion promedio (redondeada hacia abajo).
     */
    public int getDuracionPromedio() {
        return duracionPromedio;
    }

    /**
     * @return duracion del proceso mas largo.
     */
    public int getDuracionMasLarga() {
        return duracionMasLarga;
    }

    /**
     * @return duracion del proceso mas corto.
     */
    public int getDuracionMasCorta() {
        return duracionMasCorta;
    }

    /**
     * @return promedio de Tr/Ts (0.0 si no hay datos).
     */
    public double getPromedioTrTs() {
        return promedioTrTs;
    }

    /**
     * @return true si no hay procesos terminados.
     */
    public boolean estaVacia() {
        return lineas.isEmpty();
    }

    /* ==================== GETTERS GANTT ==================== */

    /**
     * @return instante minimo registrado en algun intervalo (0 si no hay).
     */
    public int getInstanteMin() {
        return instanteMin;
    }

    /**
     * @return instante maximo registrado en algun intervalo (0 si no hay).
     */
    public int getInstanteMax() {
        return instanteMax;
    }

    /**
     * @return cantidad total de instantes (desde instanteMin hasta
     *         instanteMax, inclusive). 0 si no hay datos.
     */
    public int getCantidadInstantes() {
        if (instanteMax < instanteMin) return 0;
        return (instanteMax - instanteMin) + 1;
    }

    /**
     * @return lista ordenada de IDs de CPUs presentes (ascendente).
     */
    public List<Integer> getCpusPresentes() {
        return Collections.unmodifiableList(cpusPresentes);
    }

    /**
     * Devuelve las LineaProceso que corrieron en una CPU especifica,
     * ORDENADAS POR ID ASCENDENTE (P1, P2, P3, ...).
     *
     * @param idCpu id del nucleo
     * @return lista de lineas de esa CPU (puede estar vacia)
     */
    public List<LineaProceso> getLineasDeCPU(int idCpu) {
        List<LineaProceso> resultado = new ArrayList<>();
        for (LineaProceso lp : lineas) {
            if (lp.getCpuAsignado() == idCpu) {
                resultado.add(lp);
            }
        }
        // Ordenar por ID ascendente (P1, P2, P3, ...)
        resultado.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return resultado;
    }

    @Override
    public String toString() {
        return "Estadisticas{procesos=" + lineas.size()
                + ", duracionTotal=" + duracionTotal
                + ", duracionPromedio=" + duracionPromedio
                + ", promedioTrTs=" + String.format("%.2f", promedioTrTs)
                + ", instantes=[" + instanteMin + ".." + instanteMax + "]"
                + ", cpus=" + cpusPresentes + "}";
    }
}