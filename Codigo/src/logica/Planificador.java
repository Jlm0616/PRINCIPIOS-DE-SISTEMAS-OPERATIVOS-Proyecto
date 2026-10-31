package logica;

import logica.planificacion.EstrategiaPlanificacion;
import logica.planificacion.PlanificadorFCFS;
import modelo.BCP;

/**
 * Planificador de procesos.
 *
 * Delega la decision a una {@link EstrategiaPlanificacion} concreta.
 * Por defecto usa FCFS. Se puede cambiar en runtime con
 * {@link #setEstrategia(EstrategiaPlanificacion)}.
 *
 * Trabaja sobre la ListaProcesos (procesos en RAM). La ListaDeTrabajos
 * (procesos en disco) se gestiona desde GestorProcesos.
 *
 * PROYECTO 2 - SOLID:
 *  - Dependency Inversion (DIP): esta clase depende de la abstraccion
 *    EstrategiaPlanificacion, no de implementaciones concretas.
 *  - Single Responsibility (SRP): solo orquesta/delega, no decide.
 *  - Open/Closed (OCP): agregar un nuevo planificador no requiere
 *    tocar esta clase.
 */
public class Planificador {

    private EstrategiaPlanificacion estrategia;
    private ListaProcesos listaProcesos;

    // Crea un planificador con FCFS como estrategia por defecto.
    public Planificador(ListaProcesos listaProcesos) {
        this.listaProcesos = listaProcesos;
        this.estrategia = new PlanificadorFCFS();   // default
    }

    /**
     * Selecciona el siguiente BCP a ejecutar segun la estrategia activa.
     */
    public BCP seleccionarSiguiente() {
        return estrategia.seleccionarSiguiente(listaProcesos);
    }

    /**
     * @return true si hay algun proceso listo para ejecutar.
     */
    public boolean hayProcesosPendientes() {
        return !listaProcesos.estaVacia();
    }

    // Devuelve la estrategia activa.
    public EstrategiaPlanificacion getEstrategia() {
        return estrategia;
    }

    // Cambia la estrategia activa (no puede ser null).
    public void setEstrategia(EstrategiaPlanificacion estrategia) {
        if (estrategia == null) {
            throw new IllegalArgumentException("La estrategia no puede ser nula");
        }
        this.estrategia = estrategia;
    }

    // Devuelve el nombre legible de la estrategia activa.
    public String getNombreEstrategia() {
        return estrategia.getNombre();
    }

    /* ==================== APROPIATIVIDAD (Proyecto 2) ==================== */

    /**
     * Indica si la estrategia activa es apropiativa (puede expulsar
     * al proceso que esta en la CPU).
     *
     * Delega a {@link EstrategiaPlanificacion#esApropiativo()}.
     * FCFS y SPN retornan false (no apropiativos).
     * SRT y RR retornaran true.
     *
     * @return true si la estrategia puede expulsar procesos en ejecucion
     */
    public boolean esApropiativo() {
        return estrategia.esApropiativo();
    }

    /**
     * Pregunta a la estrategia activa si el proceso en CPU debe ser
     * expulsado por otro de la cola READY.
     *
     * Delega a {@link EstrategiaPlanificacion#debeExpulsar(BCP, ListaProcesos)}.
     * Si la estrategia no es apropiativa, devuelve null (no expulsa).
     *
     * @param enCPU proceso actualmente en ejecucion
     * @return el BCP que debe sustituir al de la CPU, o null si debe continuar
     */
    public BCP debeExpulsar(BCP enCPU) {
        return estrategia.debeExpulsar(enCPU, listaProcesos);
    }
}