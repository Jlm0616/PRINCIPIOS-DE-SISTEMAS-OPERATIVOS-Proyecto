package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;
import java.util.Random;

/**
 * Planificador Lottery (Lotería).
 *
 * Politica: cada proceso recibe una cantidad de tickets proporcional a su
 * peso (usamos la rafaga como peso). En cada paso se realiza un sorteo
 * para elegir el siguiente proceso.
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
                totalTickets += Math.max(1, bcp.getRafaga()); // Minimo 1 ticket
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
                int tickets = Math.max(1, bcp.getRafaga());
                acumulado += tickets;
                if (acumulado >= ganador) {
                    return bcp;
                }
            }
        }

        return null;
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