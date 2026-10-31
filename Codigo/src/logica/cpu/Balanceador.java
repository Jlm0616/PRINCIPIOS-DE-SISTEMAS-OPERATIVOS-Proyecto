package logica.cpu;

import logica.ListaProcesos;
import logica.Planificador;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;

public class Balanceador {

    // Lleva el control de cuál es la "siguiente CPU" que debería recibir un proceso.
    private int siguienteIndiceEstricto = 0;

    /**
     * Asigna procesos a las CPUs disponibles respetando el orden estricto.
     * Utiliza el planificador para decidir QUÉ proceso debe ir a la CPU.
     * 
     * @param nucleos La lista de núcleos del sistema.
     * @param listaProcesos La cola de procesos en estado READY.
     * @param planificador El planificador para saber a quién sacar de la cola.
     */
    public void asignarProcesos(List<Nucleo> nucleos, ListaProcesos listaProcesos, Planificador planificador) {
        int cantidadNucleos = nucleos.size();
        if (cantidadNucleos == 0) return;

        while (!listaProcesos.estaVacia()) {
            // Si el índice se salió de rango, lo reiniciamos
            if (siguienteIndiceEstricto >= cantidadNucleos) {
                siguienteIndiceEstricto = 0;
            }

            Nucleo nucleo = nucleos.get(siguienteIndiceEstricto);

            // Si la CPU que sigue en la fila está ocupada, NADIE MÁS entra
            if (nucleo.estaOcupado()) {
                return; // Se detiene el balanceo
            }

            // 1. El planificador nos dice QUIÉN debe ir (SPN, FCFS, etc.)
            BCP siguiente = planificador.seleccionarSiguiente();
            if (siguiente == null) break;

            System.out.println("[Balanceador] Asignando proceso ID " + siguiente.getId() 
                    + " a CPU " + nucleo.getIdNucleo());
            
            // 2. Lo sacamos de la lista de procesos (el correcto, no el primero)
            listaProcesos.sacar(siguiente);
            
            // 3. Lo asignamos al núcleo
            nucleo.asignarProceso(siguiente.getDireccionBase());
            
            // El despachador ya lo pone en RUNNING, pero por si acaso
            nucleo.getBcpActual().setEstado(EstadoProceso.RUNNING);

            // Avanzamos al siguiente núcleo
            siguienteIndiceEstricto++;
        }
    }

    // Reinicia el puntero (útil al limpiar el sistema)
    public void reiniciar() {
        siguienteIndiceEstricto = 0;
    }
}