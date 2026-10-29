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

    // Nuevo método que delega al Balanceador
    public void balancear(ListaProcesos listaProcesos, Planificador planificador) {
        balanceador.asignarProcesos(nucleos, listaProcesos, planificador);
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