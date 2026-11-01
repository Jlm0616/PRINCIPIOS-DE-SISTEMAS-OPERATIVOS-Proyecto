package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

/**
 * Planificador HRRN (Highest Response Ratio Next).
 *
 * Politica: elige el proceso con MAYOR tasa de respuesta.
 *
 *   RR = (W + S) / S
 *
 * donde:
 *   W = tiempo de espera = relojGlobal - tiempoLlegada
 *   S = tiempo de servicio = rafaga original del proceso
 *
 * A diferencia de SPN (que solo mira la rafaga), HRRN pondera el
 * tiempo de espera: los procesos que han esperado mucho ven crecer
 * su RR y eventualmente son elegidos. Asi se evita el starvation
 * sin perder la preferencia por trabajos cortos.
 *
 * NO es apropiativo: una vez elegido, el proceso corre hasta
 * terminar o bloquearse.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo implementa la politica HRRN.
 *  - OCP: agregar HRRN no modifica la interfaz ni los otros
 *    algoritmos.
 *  - LSP: sustituible por cualquier EstrategiaPlanificacion.
 *  - ISP: hereda los defaults de debeExpulsar() y esApropiativo()
 *    porque HRRN no es apropiativo.
 *  - DIP: depende de ListaProcesos (abstraccion), no de nada
 *    concreto del sistema.
 */
public class PlanificadorHRRN implements EstrategiaPlanificacion {

    /**
     * Reloj global simulado actual.
     * GestorProcesos lo actualiza en cada paso llamando a
     * {@link #setRelojGlobal(int)}.
     */
    private int relojGlobalActual = 0;

    /* ==================== SELECCION ==================== */

    /**
     * Selecciona el proceso READY con MAYOR tasa de respuesta HRR.
     *
     * Reglas:
     *   1. Solo se consideran procesos en estado READY.
     *   2. Se calcula RR = (W + S) / S para cada uno.
     *   3. Gana el de mayor RR.
     *   4. Desempate: menor tiempoLlegada (FCFS).
     *   5. Ultimo desempate: menor id.
     *
     * Si la rafaga es 0 (no deberia pasar), se trata como rafaga 1
     * para evitar division por cero.
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        List<BCP> procesos = listaProcesos.toList();

        if (procesos.isEmpty()) {
            return null;
        }

        BCP mejorProceso = null;
        double mejorRR = Double.NEGATIVE_INFINITY;

        for (BCP bcp : procesos) {
            // Solo consideramos procesos en estado READY
            if (bcp.getEstado() != EstadoProceso.READY) {
                continue;
            }

            double rr = calcularResponseRatio(bcp);

            if (mejorProceso == null) {
                mejorProceso = bcp;
                mejorRR = rr;
            } else if (rr > mejorRR) {
                mejorProceso = bcp;
                mejorRR = rr;
            } else if (rr == mejorRR) {
                // Desempate 1: menor tiempoLlegada (FCFS)
                int llegadaActual = bcp.getTiempoLlegada();
                int llegadaMejor = mejorProceso.getTiempoLlegada();

                if (llegadaActual < llegadaMejor) {
                    mejorProceso = bcp;
                } else if (llegadaActual == llegadaMejor) {
                    // Desempate 2: menor id
                    if (bcp.getId() < mejorProceso.getId()) {
                        mejorProceso = bcp;
                    }
                }
            }
        }

        return mejorProceso;
    }

    /* ==================== CALCULO ==================== */

    /**
     * Calcula la tasa de respuesta de un proceso:
     *
     *   RR = (W + S) / S
     *
     * donde:
     *   W = relojGlobalActual - bcp.getTiempoLlegada()   (espera)
     *   S = bcp.getRafaga()                              (servicio)
     *
     * Si la rafaga es 0, se asume 1 para evitar division por cero.
     * Si W es negativo (proceso que aun no ha llegado), se trata
     * como 0.
     *
     * @param bcp proceso
     * @return la tasa de respuesta (mayor es mejor)
     */
    private double calcularResponseRatio(BCP bcp) {
        int s = bcp.getRafaga();
        if (s <= 0) s = 1;   // evitar division por cero

        int w = relojGlobalActual - bcp.getTiempoLlegada();
        if (w < 0) w = 0;    // proceso que aun no ha llegado

        return (double) (w + s) / s;
    }

    /* ==================== NOMBRE ==================== */

    @Override
    public String getNombre() {
        return "HRRN";
    }

    /* ==================== RELOJ ==================== */

    /**
     * Actualiza el reloj global simulado.
     *
     * GestorProcesos llama a este metodo en cada paso, ANTES de
     * balancear, para que el calculo de W use el instante correcto.
     */
    @Override
    public void setRelojGlobal(int relojGlobal) {
        this.relojGlobalActual = relojGlobal;
    }
}