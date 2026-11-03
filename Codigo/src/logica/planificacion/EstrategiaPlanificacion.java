package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;

/**
 * Contrato para las estrategias de planificacion de CPU.
 *
 * Cada algoritmo (FCFS, SPN, SRT, RR, HRRN) implementa esta
 * interfaz para decidir cual proceso ejecutar a continuacion.
 *
 * El Planificador delega la decision a la estrategia activa.
 *
 * Trabaja sobre la ListaProcesos (procesos en RAM). La
 * ListaDeTrabajos (procesos en disco) se gestiona aparte.
 *
 * PROYECTO 2:
 *  - Interface Segregation (ISP): los metodos apropiativos y el
 *    setRelojGlobal tienen implementacion por defecto, asi FCFS,
 *    SPN y los demas no necesitan cambiar ni una linea.
 *  - Open/Closed (OCP): agregar un nuevo planificador que necesite
 *    el reloj (HRRN) solo requiere overridear setRelojGlobal() y
 *    leer el campo correspondiente, sin tocar la interfaz.
 *  - Liskov (LSP): cualquier EstrategiaPlanificacion es sustituible
 *    sin romper el sistema.
 */
public interface EstrategiaPlanificacion {

    /**
     * Selecciona el siguiente proceso a ejecutar segun la politica.
     *
     * @param listaProcesos cola de procesos en RAM
     * @return el BCP elegido, o null si no hay procesos
     */
    BCP seleccionarSiguiente(ListaProcesos listaProcesos);

    /**
     * @return nombre legible de la estrategia (para la UI).
     */
    String getNombre();

    /**
     * Indica si la estrategia es apropiativa (puede expulsar al
     * proceso que esta en la CPU).
     *
     * Por defecto false (FCFS, SPN, HRRN...).
     * Los apropiativos (SRT, RR) deben overridear a true.
     *
     * @return true si la estrategia puede expulsar procesos en ejecucion
     */
    default boolean esApropiativo() {
        return false;
    }

    /**
     * Decide si el proceso en CPU debe ser expulsado por otro de la
     * cola READY, segun la politica de la estrategia.
     *
     * Solo se invoca si {@link #esApropiativo()} devuelve true.
     *
     * Por defecto devuelve null (nunca expulsa).
     *
     * @param enCPU          proceso actualmente en ejecucion
     * @param listaProcesos  cola de procesos en READY
     * @return el BCP que debe sustituir al de la CPU, o null si
     *         el proceso actual debe continuar
     */
    default BCP debeExpulsar(BCP enCPU, ListaProcesos listaProcesos) {
        return null;
    }

    /**
     * Actualiza el reloj global simulado de la estrategia.
     *
     * Solo las estrategias que dependen del reloj (ej. HRRN, que
     * calcula el tiempo de espera W = (relojGlobal - 1) - tiempoLlegada)
     * necesitan overridear este metodo. Las demas lo heredan como
     * no-op.
     *
     * GestorProcesos llama a este metodo en cada paso, antes de
     * balancear, para que la estrategia tenga el reloj actualizado
     * al momento de elegir.
     *
     * @param relojGlobal instante simulado actual
     */
    default void setRelojGlobal(int relojGlobal) {
        // No-op por defecto. Solo HRRN (u otras que dependan del
        // reloj) necesitan overridear.
    }
}