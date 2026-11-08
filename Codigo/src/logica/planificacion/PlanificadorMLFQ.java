package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Planificador MLFQ (Multilevel Feedback Queue).
 *
 * Configuracion fija (clasica, Silberschatz / Stallings):
 *   - 3 niveles: Q1, Q2, Q3.
 *   - Quantum: Q1 = 1, Q2 = 2, Q3 = 4.
 *   - Un proceso nuevo entra a Q1.
 *   - Si agota su quantum sin terminar, baja un nivel (Q1 -> Q2 -> Q3).
 *   - En Q3 se queda (Round Robin puro con quantum 4).
 *   - Al volver de I/O (BLOCKED -> READY) vuelve a Q1.
 *   - Dentro de cada nivel, FCFS (el que llego primero al nivel).
 *
 * ES APROPIATIVO por quantum: el GestorProcesos le pregunta el quantum
 * actual del proceso en CPU y, si lo agota, lo expulsa y este planificador
 * le baja el nivel.
 *
 * ORDEN DE LLEGADA AL NIVEL:
 *   Los procesos que todavia no tienen turno asignado (recien llegados)
 *   se registran la primera vez que se ven, en el orden en que estan en
 *   la cola de listos. Asi el FCFS dentro del nivel respeta el orden de
 *   llegada real y NO el ID del proceso (con tiempos de llegada aleatorios
 *   el ID no refleja quien llego primero).
 *
 */
public class PlanificadorMLFQ implements EstrategiaPlanificacion {

    // ==== Configuracion fija de niveles y quantums ====
    public static final int NUM_NIVELES = 3;
    /** Nivel 1 (mas prioritario), 2 y 3 (menos prioritario). Indice 0 = nivel 1. */
    private static final int[] QUANTUMS = { 1, 2, 4 };

    // ==== Estado interno por proceso ====
    /** Nivel actual de cada proceso (1, 2 o 3). Por defecto: 1. */
    private final Map<Integer, Integer> nivelPorProceso = new HashMap<>();
    /** Orden de llegada al nivel actual (para FCFS dentro del nivel). */
    private final Map<Integer, Long> secuenciaLlegadaNivel = new HashMap<>();
    private long contadorSecuencia = 0L;

    private int relojGlobalActual = 0;

    @Override
    public void setRelojGlobal(int relojGlobal) {
        this.relojGlobalActual = relojGlobal;
    }

    /**
     * Devuelve el quantum que le corresponde al proceso segun su nivel.
     * Si el proceso es nuevo (sin nivel asignado), se asume Q1.
     */
    public int getQuantumActual(BCP bcp) {
        int nivel = getNivel(bcp);
        return QUANTUMS[nivel - 1];
    }

    /**
     * @return el nivel actual (1..NUM_NIVELES) del proceso.
     */
    public int getNivel(BCP bcp) {
        return nivelPorProceso.getOrDefault(bcp.getId(), 1);
    }

    /**
     * Asigna un nivel concreto a un proceso y lo registra como el mas
     * reciente en ese nivel (para FCFS dentro del nivel).
     */
    public void setNivel(BCP bcp, int nivel) {
        if (nivel < 1) nivel = 1;
        if (nivel > NUM_NIVELES) nivel = NUM_NIVELES;
        nivelPorProceso.put(bcp.getId(), nivel);
        secuenciaLlegadaNivel.put(bcp.getId(), ++contadorSecuencia);
    }

    /**
     * Baja un nivel al proceso (Q1->Q2->Q3). Si ya esta en Q3, se queda
     * alli (Round Robin puro). Se llama cuando el proceso agota su quantum.
     */
    public void bajarNivel(BCP bcp) {
        int actual = getNivel(bcp);
        int nuevo = (actual < NUM_NIVELES) ? actual + 1 : NUM_NIVELES;
        setNivel(bcp, nuevo);
    }

    /**
     * Reinicia el nivel del proceso a Q1. Se llama cuando un proceso
     * vuelve de I/O (BLOCKED -> READY), para no penalizarlo por haberse
     * bloqueado.
     */
    public void resetearNivel(BCP bcp) {
        setNivel(bcp, 1);
    }

    /**
     * Limpia el estado interno de un proceso que termino o salio del
     * sistema, para no dejar basura en los mapas.
     */
    public void olvidar(BCP bcp) {
        int id = bcp.getId();
        nivelPorProceso.remove(id);
        secuenciaLlegadaNivel.remove(id);
    }

    /**
     * Elige el siguiente proceso READY:
     *   1. Mira los niveles de mayor prioridad a menor (Q1, luego Q2, luego Q3).
     *   2. Del primer nivel con procesos READY, elige el de menor secuencia
     *      de llegada a ese nivel (FCFS).
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        List<BCP> procesos = listaProcesos.toList();
        if (procesos.isEmpty()) return null;

        // Registrar a los que aun no tienen turno (recien llegados), en el
        // orden en que estan en la cola. Sin esto todos empatarian con
        // Long.MAX_VALUE y ganaria el de menor ID, no el que llego primero.
        for (BCP bcp : procesos) {
            if (!secuenciaLlegadaNivel.containsKey(bcp.getId())) {
                secuenciaLlegadaNivel.put(bcp.getId(), ++contadorSecuencia);
            }
        }

        // Recorremos los niveles 1..NUM_NIVELES
        for (int nivel = 1; nivel <= NUM_NIVELES; nivel++) {
            BCP mejor = null;
            long mejorSecuencia = Long.MAX_VALUE;

            for (BCP bcp : procesos) {
                if (bcp.getEstado() != EstadoProceso.READY) continue;
                if (getNivel(bcp) != nivel) continue;

                long seq = secuenciaLlegadaNivel.getOrDefault(bcp.getId(), Long.MAX_VALUE);
                if (mejor == null || seq < mejorSecuencia ||
                    (seq == mejorSecuencia && bcp.getId() < mejor.getId())) {
                    mejor = bcp;
                    mejorSecuencia = seq;
                }
            }
            if (mejor != null) return mejor;
        }
        return null;
    }

    @Override
    public String getNombre() {
        return "MLFQ";
    }

    @Override
    public boolean esApropiativo() {
        // Apropiativo por quantum: el GestorProcesos consulta getQuantumActual
        // y expulsa al proceso cuando lo agota.
        return true;
    }

    /** Para debug/logs: nombre legible del nivel. */
    public static String nombreNivel(int nivel) {
        return "Q" + nivel;
    }
}