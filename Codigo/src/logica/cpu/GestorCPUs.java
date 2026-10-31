package logica.cpu;

import logica.ListaProcesos;
import logica.Planificador;
import modelo.BCP;
import modelo.Disco;
import modelo.EstadoProceso;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.List;

public class GestorCPUs {

    private final List<Nucleo> nucleos;
    private final Memoria memoria;
    private final Disco disco;
    private final Balanceador balanceador;

    public GestorCPUs(int cantidadNucleos, Memoria memoria, int limiteKernel, Disco disco) {
        this.memoria = memoria;
        this.disco = disco;
        this.balanceador = new Balanceador();
        this.nucleos = new ArrayList<>();

        int cantidad = Math.min(cantidadNucleos, 5);
        for (int i = 0; i < cantidad; i++) {
            nucleos.add(new Nucleo(i, memoria, limiteKernel, disco));
        }
    }

    /**
     * Delega al Balanceador la asignacion de procesos a los nucleos.
     *
     * PROYECTO 2: recibe el relojGlobal para propagarlo hasta el Despachador
     * y que este marque el inicio simulado del proceso.
     *
     * @param listaProcesos cola de procesos READY
     * @param planificador  estrategia de planificacion (FCFS, SPN, ...)
     * @param relojGlobal   instante simulado actual
     */
    public void balancear(ListaProcesos listaProcesos, Planificador planificador, int relojGlobal) {
        balanceador.asignarProcesos(nucleos, listaProcesos, planificador, relojGlobal);
    }

    public void reiniciarBalanceador() {
        balanceador.reiniciar();
    }

    public void ejecutarPasoEnTodos(List<BCP> procesosTerminados) {
        for (Nucleo nucleo : nucleos) {
            if (nucleo.estaOcupado()) {
                BCP bcpAntes = nucleo.getBcpActual();
                nucleo.ejecutarPaso();

                if (bcpAntes != null && bcpAntes.getEstado() == EstadoProceso.EXIT) {
                    procesosTerminados.add(bcpAntes);
                }
            }
        }
    }

    public List<Nucleo> getNucleos() { return nucleos; }
    public int getCantidadNucleos() { return nucleos.size(); }
}