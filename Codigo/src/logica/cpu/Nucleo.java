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

    public void asignarProceso(int direccionBCP) {
        this.despachador.despachar(direccionBCP);
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