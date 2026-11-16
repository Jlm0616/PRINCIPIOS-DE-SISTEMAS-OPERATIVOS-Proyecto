
package modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Programa cargado cuyo tiempo de llegada aun no se cumple.
 *
 * No tiene BCP y no ocupa RAM ni memoria virtual (swap).
 * Permanece pendiente hasta que el reloj alcance su TL.
 */
public class ProgramaPendiente {

    private final int id;
    private final String nombreArchivo;
    private int tiempoLlegada;
    private int prioridad;
    private final List<Instruccion> instrucciones;

    public ProgramaPendiente(int id, String nombreArchivo,
                             int tiempoLlegada, int prioridad,
                             List<Instruccion> instrucciones) {
        this.id = id;
        this.nombreArchivo = nombreArchivo;
        this.tiempoLlegada = tiempoLlegada;
        this.prioridad = prioridad;
        this.instrucciones = new ArrayList<>(instrucciones);
    }

    public int getId() {
        return id;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public int getTiempoLlegada() {
        return tiempoLlegada;
    }

    public void setTiempoLlegada(int tiempoLlegada) {
        this.tiempoLlegada = tiempoLlegada;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public int getRafaga() {
        return instrucciones.size();
    }

    public List<Instruccion> getInstrucciones() {
        return Collections.unmodifiableList(instrucciones);
    }
}
