package logica;

/**
 * Proceso en estado NEW: todavia NO tiene BCP.
 *
 * Su programa vive en la zona Swap del disco. Solo cuando llega su
 * tiempo de llegada (y hay particion libre) se crea el BCP, se cargan
 * las instrucciones en RAM y pasa a READY.
 *
 * Guarda los datos que el usuario puede asignar desde la GUI mientras
 * el proceso no tiene BCP: tiempo de llegada y prioridad.
 */
public class TrabajoNuevo {

    private final int id;
    private final String nombre;
    private final int dirSwap;
    private final int tamano;
    private final boolean archivoCopiado;

    private int tiempoLlegada;
    private int prioridad;

    public TrabajoNuevo(int id, String nombre, int tiempoLlegada, int prioridad,
                        int dirSwap, int tamano, boolean archivoCopiado) {
        this.id = id;
        this.nombre = nombre;
        this.tiempoLlegada = tiempoLlegada;
        this.prioridad = prioridad;
        this.dirSwap = dirSwap;
        this.tamano = tamano;
        this.archivoCopiado = archivoCopiado;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getDirSwap() { return dirSwap; }
    public int getTamano() { return tamano; }

    /** true si el .asm ya fue copiado a la zona de archivos del disco. */
    public boolean isArchivoCopiado() { return archivoCopiado; }

    public int getTiempoLlegada() { return tiempoLlegada; }
    public void setTiempoLlegada(int tiempoLlegada) { this.tiempoLlegada = tiempoLlegada; }

    public int getPrioridad() { return prioridad; }
    public void setPrioridad(int prioridad) { this.prioridad = prioridad; }

    @Override
    public String toString() {
        return "NEW[id=" + id + ", " + nombre + ", TL=" + tiempoLlegada
                + ", swap@" + dirSwap + "]";
    }
}