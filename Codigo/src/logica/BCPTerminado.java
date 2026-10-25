package logica;

import modelo.EstadoProceso;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Snapshot de un BCP terminado.
 *
 * Cuando un proceso termina, se copian sus datos importantes a este
 * objeto y se libera el BCP de la memoria (para que el kernel no se
 * llene con BCPs terminados).
 *
 * Sigue el modelo del Stallings (seccion 3.2): la informacion del
 * proceso se preserva temporalmente para que programas auxiliares
 * (estadisticas, contabilidad) extraigan los datos que necesiten.
 *
 * Proyecto 2: se agregan campos para las estadisticas acumulativas
 * (tiempoLlegada, turnaround, trTs).
 */
public class BCPTerminado {

    private final int id;
    private final EstadoProceso estadoFinal;
    private final LocalDateTime tiempoInicio;
    private final LocalDateTime tiempoFin;
    private final int base;
    private final int alcance;
    private final int prioridad;

    // === NUEVOS CAMPOS (Proyecto 2) ===
    private final int tiempoLlegada;
    private final int turnaround;
    private final double trTs;

    // Crea un snapshot con los datos del proceso terminado.
    public BCPTerminado(int id, EstadoProceso estadoFinal,
                        LocalDateTime tiempoInicio, LocalDateTime tiempoFin,
                        int base, int alcance, int prioridad,
                        int tiempoLlegada, int turnaround, double trTs) {
        this.id = id;
        this.estadoFinal = estadoFinal;
        this.tiempoInicio = tiempoInicio;
        this.tiempoFin = tiempoFin;
        this.base = base;
        this.alcance = alcance;
        this.prioridad = prioridad;
        this.tiempoLlegada = tiempoLlegada;
        this.turnaround = turnaround;
        this.trTs = trTs;
    }

    public int getId() { 
        return id; 
    }
    
    public EstadoProceso getEstado() { 
        return estadoFinal; 
    }
    
    public LocalDateTime getTiempoInicio() { 
        return tiempoInicio; 
    }
    
    public LocalDateTime getTiempoFin() { 
        return tiempoFin; 
    }
    
    public int getBase() { 
        return base; 
    }
    
    public int getAlcance() { 
        return alcance; 
    }
    
    public int getPrioridad() { 
        return prioridad; 
    }

    // === NUEVOS GETTERS (Proyecto 2) ===

    // Devuelve el tiempo de llegada del proceso.
    public int getTiempoLlegada() { 
        return tiempoLlegada; 
    }

    // Devuelve el turnaround (tiempo final - tiempo de llegada).
    public int getTurnaround() { 
        return turnaround; 
    }

    // Devuelve Tr/Ts (turnaround / service time).
    public double getTrTs() { 
        return trTs; 
    }

    // Devuelve la duración del proceso en segundos, o -1 si faltan tiempos.
    public long getDuracionSegundos() {
        if (tiempoInicio == null || tiempoFin == null) return -1;
        return Duration.between(tiempoInicio, tiempoFin).getSeconds();
    }
}