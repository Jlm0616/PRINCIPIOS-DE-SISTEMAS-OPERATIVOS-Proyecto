package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

/**
 * Planificador Guaranteed Scheduling (Planificacion Garantizada).
 *
 * No esta en Stallings; la definicion es de Tanenbaum, "Modern Operating
 * Systems", seccion 2.4.3: si hay n procesos, a cada uno le corresponde
 * 1/n del tiempo de CPU desde que llego. Para cada proceso se calcula
 *
 *     merecido = (tiempo en el sistema) / n
 *     razon    = (CPU recibida) / merecido
 *
 * Una razon de 0.5 quiere decir que recibio la mitad de lo que le
 * correspondia; una de 2.0, el doble. Se ejecuta el proceso con la razon
 * MAS BAJA (el mas atrasado), y corre hasta que su razon pasa la de su
 * competidor mas cercano.
 *
 * ES APROPIATIVO: en cada segundo se recalculan las razones y, si un
 * proceso de la cola tiene una razon estrictamente menor que el que esta
 * en CPU, lo expulsa. Con empate sigue el que esta en CPU (evita cambios
 * de contexto inutiles).
 *
 * Como n es el mismo para todos los procesos, ordenar por razon es lo
 * mismo que ordenar por cpuRecibida / tiempoEnSistema; aqui se usa esa
 * forma, que no necesita contar los procesos de todos los nucleos.
 *
 * Un proceso que acaba de llegar (tiempo en el sistema = 0) todavia no
 * recibio nada de lo que le toca: su razon es 0 y tiene la maxima
 * prioridad.
 *
 * Desempate entre procesos de la cola: menor tiempo de llegada (FCFS) y
 * despues menor id.
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
        BCP mejorProceso = null;
        double mejorRazon = Double.MAX_VALUE;

        for (BCP bcp : procesos) {
            if (bcp.getEstado() != EstadoProceso.READY) continue;

            double razon = calcularRazon(bcp);
            if (mejorProceso == null || razon < mejorRazon - EPSILON) {
                mejorRazon = razon;
                mejorProceso = bcp;
            } else if (Math.abs(razon - mejorRazon) < EPSILON
                    && llegoAntes(bcp, mejorProceso)) {
                mejorProceso = bcp;
            }
        }
        return mejorProceso;
    }

    /**
     * Expulsa al proceso en CPU si en la cola hay uno con una razon
     * ESTRICTAMENTE menor (mas atrasado respecto de lo que le toca).
     */
    @Override
    public BCP debeExpulsar(BCP enCPU, ListaProcesos listaProcesos) {
        if (enCPU == null || enCPU.getEstado() != EstadoProceso.RUNNING) return null;

        BCP candidato = seleccionarSiguiente(listaProcesos);
        if (candidato == null) return null;

        if (calcularRazon(candidato) < calcularRazon(enCPU) - EPSILON) {
            return candidato;
        }
        return null;
    }

    @Override
    public String motivoExpulsion(BCP enCPU, BCP candidato) {
        return "Guaranteed expulsa a P" + enCPU.getId()
                + " (razon " + formatear(calcularRazon(enCPU)) + ") porque P" + candidato.getId()
                + " esta mas atrasado (razon " + formatear(calcularRazon(candidato)) + ")";
    }

    /**
     * Razon = cpu_recibida / tiempo_en_sistema (proporcional a la de
     * Tanenbaum, ver la descripcion de la clase).
     *
     * La decision se toma al inicio del paso, en relojGlobal - 1 (el paso N
     * cubre el intervalo de N-1 a N), igual que HRRN.
     */
    public double calcularRazon(BCP bcp) {
        int tiempoEnSistema = (relojGlobalActual - 1) - bcp.getTiempoLlegada();
        int cpuRecibida = bcp.getRafaga() - bcp.getTiempoRestante();

        if (tiempoEnSistema <= 0) {
            // Recien llegado: todavia no recibio nada de lo que le toca.
            return 0.0;
        }
        return (double) cpuRecibida / (double) tiempoEnSistema;
    }

    // Desempate: menor tiempo de llegada (FCFS) y despues menor id.
    private static boolean llegoAntes(BCP a, BCP b) {
        if (a.getTiempoLlegada() != b.getTiempoLlegada()) {
            return a.getTiempoLlegada() < b.getTiempoLlegada();
        }
        return a.getId() < b.getId();
    }

    private static String formatear(double razon) {
        return String.format("%.2f", razon);
    }

    @Override
    public String getNombre() {
        return "Guaranteed";
    }

    @Override
    public boolean esApropiativo() {
        // Se reevalua cada segundo: el mas atrasado toma la CPU.
        return true;
    }
}
