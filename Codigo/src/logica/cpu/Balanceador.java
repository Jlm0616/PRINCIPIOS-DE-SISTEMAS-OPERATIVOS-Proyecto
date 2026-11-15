package logica.cpu;

import logica.ListaProcesos;
import logica.Planificador;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Reparte los procesos READY entre los nucleos (Stallings, seccion 10.1,
 * "Assignment of Processes to Processors").
 *
 * Cada proceso puede:
 *   - ir a CUALQUIER nucleo (por defecto): es la cola global o "load
 *     sharing" del libro. Cuando un nucleo queda libre toma el siguiente
 *     proceso que elija el algoritmo, asi ningun nucleo queda ocioso
 *     mientras haya trabajo (p. 467);
 *   - estar ASIGNADO a un nucleo (lo elige el usuario en la ventana de
 *     tiempos de llegada): solo ese nucleo lo ejecuta. Es la asignacion
 *     estatica del libro, con una cola por procesador (p. 463).
 *
 * Que proceso le toca a cada nucleo lo decide el algoritmo de
 * planificacion, mirando solo los procesos que ese nucleo puede ejecutar.
 */
public class Balanceador {

    /**
     * Asigna un proceso a cada nucleo libre, en orden de nucleo.
     *
     * PROYECTO 2: recibe el relojGlobal para que el Despachador (via Nucleo)
     * pueda marcar el inicio simulado del proceso.
     *
     * @param nucleos       La lista de núcleos del sistema.
     * @param listaProcesos La cola de procesos en estado READY.
     * @param planificador  El planificador para saber a quién sacar de la cola.
     * @param relojGlobal   Instante simulado actual.
     * @param puede         si el proceso puede ejecutarse en el nucleo (id)
     */
    public void asignarProcesos(List<Nucleo> nucleos,
                                ListaProcesos listaProcesos,
                                Planificador planificador,
                                int relojGlobal,
                                BiPredicate<BCP, Integer> puede) {
        for (Nucleo nucleo : nucleos) {
            if (listaProcesos.estaVacia()) return;
            if (nucleo.estaOcupado()) continue;

            // El algoritmo elige entre los procesos que este nucleo puede ejecutar.
            ListaProcesos elegibles = filtrar(listaProcesos, nucleo.getIdNucleo(), puede);
            BCP siguiente = planificador.seleccionarDe(elegibles);
            if (siguiente == null) continue;

            System.out.println("[Balanceador] Asignando proceso ID " + siguiente.getId()
                    + " a CPU " + nucleo.getIdNucleo());

            listaProcesos.sacar(siguiente);
            nucleo.asignarProceso(siguiente.getDireccionBase(), relojGlobal);

            // El despachador ya lo pone en RUNNING, pero por si acaso
            nucleo.getBcpActual().setEstado(EstadoProceso.RUNNING);
        }
    }

    /**
     * @return una cola con los procesos de listaProcesos que el nucleo puede
     *         ejecutar, en el mismo orden (el orden importa a FCFS y RR).
     */
    public static ListaProcesos filtrar(ListaProcesos listaProcesos, int idNucleo,
                                        BiPredicate<BCP, Integer> puede) {
        ListaProcesos elegibles = new ListaProcesos();
        for (BCP bcp : listaProcesos.toList()) {
            if (puede.test(bcp, idNucleo)) elegibles.agregar(bcp);
        }
        return elegibles;
    }

    // Se conserva para no cambiar a quien lo llama; ya no hay turno que reiniciar.
    public void reiniciar() {
    }
}
