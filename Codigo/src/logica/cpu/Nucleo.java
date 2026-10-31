package logica.cpu;

import logica.Despachador;
import modelo.BCP;
import modelo.CPU;
import modelo.Disco;
import modelo.EstadoProceso;
import modelo.Memoria;

public class Nucleo {

    private final int idNucleo;
    private final CPU cpu;
    private final Memoria memoria;
    private final Despachador despachador;

    private boolean ocupado;

    public Nucleo(int idNucleo, Memoria memoria, int limiteKernel, Disco disco) {
        this.idNucleo = idNucleo;
        this.memoria = memoria;
        this.cpu = new CPU(limiteKernel);
        this.despachador = new Despachador(cpu, memoria);
        this.despachador.setDisco(disco);
        this.ocupado = false;
    }

    /**
     * Asigna un proceso a este nucleo.
     *
     * PROYECTO 2: recibe el relojGlobal para que el Despachador marque
     * el inicio simulado del proceso (usado en estadisticas).
     *
     * @param direccionBCP direccion base del BCP a ejecutar
     * @param relojGlobal  instante simulado actual
     */
    public void asignarProceso(int direccionBCP, int relojGlobal) {
        this.despachador.despachar(direccionBCP, relojGlobal);
        this.ocupado = true;

        BCP bcp = despachador.getBcpActual();
        if (bcp != null) {
            bcp.setCpuAsignado(idNucleo);
        }
    }

    /**
     * Ejecuta un paso de CPU en este núcleo.
     * @return true si el proceso sigue en el núcleo (vivo o bloqueado esperando I/O),
     *         false si terminó (EXIT) y el núcleo quedó libre.
     */
    public boolean ejecutarPaso() {
        if (!ocupado) return false;

        boolean sigueVivo = despachador.ejecutarUnPaso();

        BCP bcp = despachador.getBcpActual();

        // Si terminó (EXIT), liberamos el núcleo
        if (bcp == null || bcp.getEstado() == EstadoProceso.EXIT) {
            if (bcp != null) {
                bcp.setCpuAsignado(-1);
            }
            despachador.limpiarEjecutor();
            ocupado = false;
            return false;
        }

        // Si está BLOCKED, se queda en el núcleo pero el ejecutarPaso() devolvió false.
        // Retornamos true porque el proceso AÚN ocupa este núcleo (solo está esperando I/O).
        if (bcp.getEstado() == EstadoProceso.BLOCKED) {
            return true;
        }

        return sigueVivo;
    }

    /**
     * Expulsa al proceso actual del nucleo SIN terminarlo.
     *
     * PROYECTO 2 (SRT): se usa cuando el planificador apropiativo decide
     * que otro proceso de la cola READY debe ejecutarse en lugar del actual.
     *
     * Diferencia con {@link #liberar()}:
     *   - liberar(): se usa cuando el proceso ya termino (EXIT) o se descarta.
     *   - expulsarProceso(): el proceso sigue vivo, vuelve a READY.
     *
     * Efectos:
     *   1. Guarda el contexto del proceso (registros de CPU → BCP).
     *   2. Marca el proceso como READY y desasigna la CPU.
     *   3. Limpia el ejecutor y libera el nucleo.
     *   4. NO libera memoria del proceso (eso solo pasa en EXIT).
     *   5. NO marca fin ni libera particion.
     *
     * El llamador (GestorProcesos) debe re-encolar el BCP en la
     * ListaProcesos despues de llamar a este metodo.
     */
    public void expulsarProceso() {
        if (!ocupado) return;

        // Paso 1: guardar el contexto del proceso actual en su BCP
        despachador.guardarContexto();

        BCP bcp = despachador.getBcpActual();
        if (bcp != null) {
            // Paso 2: el proceso sigue vivo, vuelve a READY
            bcp.setEstado(EstadoProceso.READY);
            bcp.setCpuAsignado(-1);
        }

        // Paso 3: liberar el nucleo
        despachador.limpiarEjecutor();
        ocupado = false;
    }

    public void liberar() {
        BCP bcp = despachador.getBcpActual();
        if (bcp != null) {
            bcp.setCpuAsignado(-1);
        }
        despachador.limpiarEjecutor();
        ocupado = false;
    }

    public int getIdNucleo() { return idNucleo; }
    public boolean estaOcupado() { return ocupado; }
    public BCP getBcpActual() { return despachador.getBcpActual(); }
    public Despachador getDespachador() { return despachador; }
}