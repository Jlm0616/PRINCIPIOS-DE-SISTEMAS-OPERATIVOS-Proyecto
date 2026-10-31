package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

/**
 * Planificador SRT (Shortest Remaining Time).
 *
 * Variante APROPIATIVA de SPN: siempre ejecuta el proceso con el
 * MENOR tiempo restante. Si llega un proceso nuevo con menor tiempo
 * restante que el que esta en CPU, lo EXPULSA (vuelve a READY) y
 * ejecuta el nuevo.
 *
 * Reglas:
 *   1. Compara por tiempoRestante (no por rafaga).
 *   2. Desempate en seleccionarSiguiente(): tiempoLlegada (FCFS).
 *   3. Desempate final: id (menor).
 *   4. En debeExpulsar(): solo expulsa si el candidato tiene
 *      tiempoRestante ESTRICTAMENTE menor que el de la CPU.
 *      Si empatan, el de la CPU se queda (no se expulsa).
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo implementa la politica SRT. No ejecuta, no expulsa,
 *    no libera memoria. Solo DECIDE.
 *  - OCP: si manana aparece SRT con otra variante, se hereda o se
 *    crea otra clase. No modifica esta.
 *  - LSP: sustituible por cualquier EstrategiaPlanificacion.
 *  - ISP: implementa solo lo que le corresponde (hereda defaults
 *    de la interfaz que no aplican).
 */
public class PlanificadorSRT implements EstrategiaPlanificacion {

    /* ==================== SELECCION DE SIGUIENTE ==================== */

    /**
     * Selecciona el proceso con menor tiempoRestante de la cola READY.
     *
     * Mismo patron que SPN, pero comparando tiempoRestante en lugar
     * de rafaga.
     *
     * Desempate:
     *   1. Menor tiempoRestante.
     *   2. Menor tiempoLlegada (FCFS).
     *   3. Menor id.
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
                // 1. Comparar tiempoRestante (menor es mejor)
                int restanteActual = bcp.getTiempoRestante();
                int restanteMejor = mejorProceso.getTiempoRestante();

                if (restanteActual < restanteMejor) {
                    mejorProceso = bcp;
                }
                // 2. Desempate: Tiempo de llegada (menor es mejor - FCFS)
                else if (restanteActual == restanteMejor) {
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
        return "SRT";
    }

    /* ==================== APROPIATIVIDAD ==================== */

    /**
     * SRT es apropiativo: puede expulsar al proceso en CPU si llega
     * uno con menor tiempo restante.
     */
    @Override
    public boolean esApropiativo() {
        return true;
    }

    /**
     * Decide si el proceso en CPU debe ser expulsado.
     *
     * Regla: solo expulsa si el mejor candidato de READY tiene
     * tiempoRestante ESTRICTAMENTE MENOR que el del proceso en CPU.
     *
     * Si empatan, el de la CPU se queda (no se expulsa). Esto
     * coincide con el ejemplo del profesor: en t=8, P5 llega con
     * rest=2 y P3 tiene rest=2 → NO se expulsa, P3 sigue.
     *
     * @param enCPU proceso actualmente en ejecucion
     * @param listaProcesos cola de procesos en READY
     * @return el BCP que debe sustituir al de la CPU, o null si
     *         el proceso actual debe continuar
     */
    @Override
    public BCP debeExpulsar(BCP enCPU, ListaProcesos listaProcesos) {
        if (enCPU == null) return null;
        if (enCPU.getEstado() != EstadoProceso.RUNNING) return null;

        BCP candidato = seleccionarSiguiente(listaProcesos);
        if (candidato == null) return null;

        // Solo expulsa si el candidato es ESTRICTAMENTE mejor.
        // En caso de empate, el proceso en CPU gana (no se expulsa).
        if (candidato.getTiempoRestante() < enCPU.getTiempoRestante()) {
            return candidato;
        }
        return null;
    }
}