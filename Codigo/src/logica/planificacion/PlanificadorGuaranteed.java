package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

/**
 * Planificador Guaranteed Scheduling (Planificacion Garantizada).
 *
 * Politica (Stallings): si hay n procesos, cada uno merece 1/n del tiempo de CPU.
 * En cada paso se calcula la razon:
 *
 *     Razon = (tiempo desde que llego) / (CPU que ha recibido)
 *
 * y se ejecuta el proceso con la razon MAS BAJA (el que mas se ha quedado atras
 * en proporcion a lo que lleva esperando).
 *
 * ES NO APROPIATIVO: una vez que un proceso toma la CPU, la conserva hasta
 * terminar o bloquearse. Solo se re-evalua en cada paso de planificacion
 * (cuando la CPU queda libre).
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo implementa la politica Guaranteed.
 *  - OCP: agregar el algoritmo no modifica la interfaz.
 *  - LSP: sustituible por cualquier EstrategiaPlanificacion.
 */
public class PlanificadorGuaranteed implements EstrategiaPlanificacion {

    private static final double EPSILON = 1e-9;

    private int relojGlobalActual = 0;

    @Override
    public void setRelojGlobal(int relojGlobal) {
        this.relojGlobalActual = relojGlobal;
    }

    /**
     * Selecciona el proceso READY con la razon mas baja.
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        List<BCP> procesos = listaProcesos.toList();
        if (procesos.isEmpty()) return null;

        // Contar procesos activos (READY + RUNNING) para saber si alguno ya corrio.
        // (No usamos n en la formula, pero sirve para decidir el caso "todos con razon infinita".)
        int activos = 0;
        for (BCP bcp : procesos) {
            if (bcp.getEstado() == EstadoProceso.READY || bcp.getEstado() == EstadoProceso.RUNNING) {
                activos++;
            }
        }
        if (activos == 0) return null;

        BCP mejorProceso = null;
        double mejorRazon = Double.MAX_VALUE;

        for (BCP bcp : procesos) {
            if (bcp.getEstado() != EstadoProceso.READY) continue;

            double razon = calcularRazon(bcp);

            if (mejorProceso == null || razon < mejorRazon - EPSILON) {
                mejorRazon = razon;
                mejorProceso = bcp;
            } else if (Math.abs(razon - mejorRazon) < EPSILON) {
                // Desempate: menor tiempo de llegada (FCFS), luego menor ID.
                if (bcp.getTiempoLlegada() < mejorProceso.getTiempoLlegada() ||
                    (bcp.getTiempoLlegada() == mejorProceso.getTiempoLlegada()
                        && bcp.getId() < mejorProceso.getId())) {
                    mejorProceso = bcp;
                }
            }
        }
        return mejorProceso;
    }

    /**
     * Razon = tiempo_en_sistema / cpu_recibida.
     *
     * Casos especiales:
     *  - Si cpu_recibida == 0 (nunca corrio) y tiempo_en_sistema > 0:
     *      razon = +infinito -> PIERDE frente a cualquier otro que ya corrio.
     *      PERO si todos estan en ese caso, gana el de mayor tiempo de espera
     *      (equivalente a FCFS). Lo resolvemos devolviendo un valor grande
     *      proporcional al tiempo de espera negativo, o simplemente Double.MAX_VALUE
     *      y el desempate por tiempo de llegada hara FCFS.
     *  - Si tiempo_en_sistema <= 0: acaba de llegar -> razon = 0 (gana).
     *  - Si cpu_recibida == 0 y tiempo_en_sistema == 0: razon = 0 (gana, recien llegado).
     */
    private double calcularRazon(BCP bcp) {
        int tiempoEnSistema = relojGlobalActual - bcp.getTiempoLlegada();
        int cpuRecibida = bcp.getRafaga() - bcp.getTiempoRestante();

        if (tiempoEnSistema <= 0) {
            // Recien llegado en el mismo instante: maxima prioridad.
            return 0.0;
        }
        if (cpuRecibida <= 0) {
            // Lleva esperando pero no ha corrido: razon infinita.
            // Como Double.MAX_VALUE, pierde contra cualquiera que ya corrio,
            // y el desempate por tiempo de llegada hara FCFS entre ellos.
            return Double.MAX_VALUE;
        }
        return (double) tiempoEnSistema / (double) cpuRecibida;
    }

    @Override
    public String getNombre() {
        return "Guaranteed";
    }

    @Override
    public boolean esApropiativo() {
        // NO apropiativo: el proceso corre hasta terminar o bloquearse.
        return false;
    }
}