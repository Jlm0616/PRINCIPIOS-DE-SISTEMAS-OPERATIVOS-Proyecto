package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

/**
 * Planificador SPN (Shortest Process Next).
 * 
 * Selecciona el proceso con la ráfaga (cantidad de instrucciones) más corta.
 * NO es apropiativo (el proceso se ejecuta hasta terminar o bloquearse).
 * 
 * Regla de desempate: Si hay dos procesos con la misma ráfaga,
 * se elige el que tenga el tiempo de llegada más antiguo (FCFS).
 * Si siguen empatados, se elige el de menor ID.
 */
public class PlanificadorSPN implements EstrategiaPlanificacion {

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
                // 1. Comparar ráfaga (menor es mejor)
                int rafagaActual = bcp.getRafaga();
                int rafagaMejor = mejorProceso.getRafaga();

                if (rafagaActual < rafagaMejor) {
                    mejorProceso = bcp;
                } 
                // 2. Desempate: Tiempo de llegada (menor es mejor)
                else if (rafagaActual == rafagaMejor) {
                    int llegadaActual = bcp.getTiempoLlegada();
                    int llegadaMejor = mejorProceso.getTiempoLlegada();

                    if (llegadaActual < llegadaMejor) {
                        mejorProceso = bcp;
                    } 
                    // 3. Último desempate: ID (menor es mejor)
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

    @Override
    public String getNombre() {
        return "SPN";
    }
}