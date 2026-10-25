package logica.cpu;

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

    public GestorCPUs(int cantidadNucleos, Memoria memoria, int limiteKernel, Disco disco) {
        this.memoria = memoria;
        this.disco = disco;
        this.nucleos = new ArrayList<>();

        int cantidad = Math.min(cantidadNucleos, 5);
        for (int i = 0; i < cantidad; i++) {
            nucleos.add(new Nucleo(i, memoria, limiteKernel, disco));
        }
    }

    public Nucleo buscarNucleoLibre() {
        for (Nucleo nucleo : nucleos) {
            if (!nucleo.estaOcupado()) {
                return nucleo;
            }
        }
        return null;
    }

    public void ejecutarPasoEnTodos(List<BCP> procesosTerminados) {
        for (Nucleo nucleo : nucleos) {
            if (nucleo.estaOcupado()) {
                BCP bcpAntes = nucleo.getBcpActual();
                boolean sigueEnNucleo = nucleo.ejecutarPaso();
                
                // Si el proceso hizo EXIT, el núcleo se liberó y lo agregamos a la lista
                if (bcpAntes != null && bcpAntes.getEstado() == EstadoProceso.EXIT) {
                    procesosTerminados.add(bcpAntes);
                }
            }
        }
    }

    public List<Nucleo> getNucleos() {
        return nucleos;
    }

    public int getCantidadNucleos() {
        return nucleos.size();
    }
}