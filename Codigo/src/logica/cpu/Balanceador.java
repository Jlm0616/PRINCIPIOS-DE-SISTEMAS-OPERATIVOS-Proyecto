package logica.cpu;

import logica.ListaProcesos;
import logica.Planificador;
import modelo.BCP;

import java.util.List;

public class Balanceador {

    // Lleva el control de cuál es la "siguiente CPU" que debería recibir un proceso.
    private int siguienteIndiceEstricto = 0;

    /**
     * Asigna procesos a las CPUs disponibles respetando el orden estricto.
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

            // Si está libre, sacamos el proceso del planificador y lo asignamos
            BCP siguiente = planificador.seleccionarSiguiente();
            if (siguiente == null) break;

            System.out.println("[Balanceador] Asignando proceso ID " + siguiente.getId() 
                    + " a CPU " + nucleo.getIdNucleo());
            
            int dir = listaProcesos.sacarPrimeraDireccion();
            nucleo.asignarProceso(dir);
            
            // El despachador ya lo pone en RUNNING, pero por si acaso
            nucleo.getBcpActual().setEstado(modelo.EstadoProceso.RUNNING);

            // Avanzamos al siguiente núcleo
            siguienteIndiceEstricto++;
        }
    }

    // Reinicia el puntero (útil al limpiar el sistema)
    public void reiniciar() {
        siguienteIndiceEstricto = 0;
    }
}