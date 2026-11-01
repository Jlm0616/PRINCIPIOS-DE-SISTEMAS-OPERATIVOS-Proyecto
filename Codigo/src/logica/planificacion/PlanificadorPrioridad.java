package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

/**
 * Planificador por Prioridad (Stallings).
 *
 * Politica: elige el proceso READY con MENOR numero de prioridad.
 * Convencion clasica de SO: prioridad 1 > prioridad 5 > prioridad 10.
 *
 * NO es apropiativo: una vez elegido, el proceso corre hasta terminar
 * o bloquearse.
 *
 * Desempate:
 *   1. Menor tiempoLlegada (FCFS).
 *   2. Menor id.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo implementa la seleccion por prioridad.
 *  - OCP: agregar Prioridad no modifica la interfaz ni los demas
 *    algoritmos.
 *  - LSP: sustituible por cualquier EstrategiaPlanificacion.
 *  - ISP: hereda debeExpulsar() y esApropiativo() del default.
 *  - DIP: depende de ListaProcesos (abstraccion).
 */
public class PlanificadorPrioridad implements EstrategiaPlanificacion {

    /* ==================== SELECCION ==================== */

    /**
     * Selecciona el proceso READY con MENOR prioridad numerica.
     *
     * Si dos procesos tienen la misma prioridad, gana el que llego
     * primero (FCFS). Si empatan tambien en llegada, gana el de
     * menor id.
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        List<BCP> procesos = listaProcesos.toList();

        if (procesos.isEmpty()) {
            return null;
        }

        BCP mejorProceso = null;

        for (BCP bcp : procesos) {
            // Solo consideramos procesos en estado READY
            if (bcp.getEstado() != EstadoProceso.READY) {
                continue;
            }

            if (mejorProceso == null) {
                mejorProceso = bcp;
            } else {
                // 1. Comparar prioridad (MENOR es mejor)
                int prioActual = bcp.getPrioridad();
                int prioMejor = mejorProceso.getPrioridad();

                if (prioActual < prioMejor) {
                    mejorProceso = bcp;
                }
                // 2. Desempate: Tiempo de llegada (menor es mejor - FCFS)
                else if (prioActual == prioMejor) {
                    int llegadaActual = bcp.getTiempoLlegada();
                    int llegadaMejor = mejorProceso.getTiempoLlegada();

                    if (llegadaActual < llegadaMejor) {
                        mejorProceso = bcp;
                    }
                    // 3. Ultimo desempate: ID (menor es mejor)
                    else if (llegadaActual == llegadaMejor) {
                        if (bcp.getId() < mejorProceso.getId()) {
                            mejorProceso = bcp;
                        }
                    }
                }
            }
        }

        return mejorProceso;
    }

    /* ==================== NOMBRE ==================== */

    @Override
    public String getNombre() {
        return "Prioridad";
    }
}