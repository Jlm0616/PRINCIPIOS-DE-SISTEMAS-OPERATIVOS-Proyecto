package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;

/**
 * Planificador RR (Round Robin).
 *
 * Politica: cada proceso recibe la CPU durante un QUANTUM de tiempo.
 * Si no termina en ese quantum, se le expulsa y va al final de la
 * cola READY. Si termina antes, sale del sistema.
 *
 * Seleccion: FIFO (el primero de la cola READY).
 *
 * NO implementa debeExpulsar porque la rotacion por quantum NO es
 * una decision de la estrategia: es una mecanica del orquestador
 * (GestorProcesos). La estrategia solo decide QUIEN sigue cuando
 * hay que elegir de la cola.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo implementa la seleccion FIFO y expone el quantum.
 *    La rotacion por quantum la maneja GestorProcesos.
 *  - OCP: agregar RR no modifica la interfaz ni los demas algoritmos.
 *  - LSP: sustituible por FCFS / SPN / SRT sin romper el sistema.
 *  - ISP: hereda debeExpulsar() del default (null), porque no aplica.
 *  - DIP: depende de ListaProcesos (abstraccion), no de nadie mas.
 */
public class PlanificadorRR implements EstrategiaPlanificacion {

    private final int quantum;

    /**
     * Crea un planificador Round Robin con el quantum indicado.
     *
     * @param quantum cantidad de instantes por turno (>= 1)
     * @throws IllegalArgumentException si quantum < 1
     */
    public PlanificadorRR(int quantum) {
        if (quantum < 1) {
            throw new IllegalArgumentException(
                "El quantum debe ser >= 1 (recibido: " + quantum + ")");
        }
        this.quantum = quantum;
    }

    /* ==================== SELECCION ==================== */

    /**
     * Devuelve el primer proceso de la cola READY (FIFO).
     *
     * RR no prioriza por rafaga ni por tiempo restante. El orden
     * de la cola define quien ejecuta.
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        return listaProcesos.verPrimero();
    }

    /* ==================== NOMBRE ==================== */

    @Override
    public String getNombre() {
        return "RR";
    }

    /* ==================== APROPIATIVIDAD ==================== */

    /**
     * RR es apropiativo: expulsa al proceso al agotar su quantum.
     * La rotacion concreta la maneja GestorProcesos (necesita el
     * contador de quantum).
     */
    @Override
    public boolean esApropiativo() {
        return true;
    }

    /* ==================== QUANTUM ==================== */

    /**
     * @return el quantum configurado (instantes por turno).
     */
    public int getQuantum() {
        return quantum;
    }

    @Override
    public String toString() {
        return "PlanificadorRR{q=" + quantum + "}";
    }
}