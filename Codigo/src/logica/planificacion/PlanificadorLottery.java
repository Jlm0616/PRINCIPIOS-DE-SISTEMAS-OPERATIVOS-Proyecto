package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;
import java.util.Random;

/**
 * Planificador Lottery (Lotería).
 *
 * Politica (Tanenbaum, seccion 2.4.3): cada proceso tiene boletos; en
 * cada sorteo se elige un boleto al azar y gana su dueno. Mas boletos =
 * mas probabilidad de recibir la CPU.
 *
 * Los boletos de cada proceso los da una funcion (GestorProcesos usa los
 * que el usuario puso en la ventana de llegadas; por defecto, la rafaga).
 * Con una semilla fija los sorteos se repiten igual en cada ejecucion.
 *
 * Es apropiativo por quantum: el proceso ganador ejecuta 1 quantum y luego
 * vuelve a la cola para un nuevo sorteo.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo implementa la politica Lottery.
 *  - OCP: agregar Lottery no modifica la interfaz ni los demas algoritmos.
 *  - LSP: sustituible por cualquier EstrategiaPlanificacion.
 */
public class PlanificadorLottery implements EstrategiaPlanificacion {

    private final Random random;

    /** Boletos de cada proceso. Por defecto, la rafaga. */
    private java.util.function.ToIntFunction<BCP> boletos = BCP::getRafaga;

    public PlanificadorLottery() {
        this.random = new Random();
    }

    /**
     * Constructor con semilla para pruebas.
     */
    public PlanificadorLottery(long semilla) {
        this.random = new Random(semilla);
    }

    /* ==================== SELECCION ==================== */

    /**
     * Selecciona el proceso ganador del sorteo de lotería.
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        List<BCP> procesos = listaProcesos.toList();

        if (procesos.isEmpty()) {
            return null;
        }

        // 1. Calcular el total de tickets
        int totalTickets = 0;
        for (BCP bcp : procesos) {
            if (bcp.getEstado() == EstadoProceso.READY) {
                totalTickets += getBoletos(bcp); // Minimo 1 ticket
            }
        }

        if (totalTickets == 0) {
            return null;
        }

        // 2. Sortear un número ganador
        int ganador = random.nextInt(totalTickets) + 1;

        // 3. Recorrer y ver quién gana
        int acumulado = 0;
        for (BCP bcp : procesos) {
            if (bcp.getEstado() == EstadoProceso.READY) {
                int tickets = getBoletos(bcp);
                acumulado += tickets;
                if (acumulado >= ganador) {
                    return bcp;
                }
            }
        }

        return null;
    }

    /** Indica de donde salen los boletos de cada proceso. */
    public void setBoletos(java.util.function.ToIntFunction<BCP> boletos) {
        if (boletos != null) this.boletos = boletos;
    }

    /** @return los boletos del proceso (al menos 1). */
    public int getBoletos(BCP bcp) {
        return Math.max(1, boletos.applyAsInt(bcp));
    }

    @Override
    public String getNombre() {
        return "Lottery";
    }

    /**
     * Lottery es apropiativo: el proceso ejecuta su quantum y vuelve a la cola.
     */
    @Override
    public boolean esApropiativo() {
        return true;
    }
}