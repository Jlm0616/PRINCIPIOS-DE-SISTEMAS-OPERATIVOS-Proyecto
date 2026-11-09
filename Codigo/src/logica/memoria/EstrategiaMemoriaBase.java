package logica.memoria;

import modelo.Instruccion;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo comun a todas las estrategias de memoria:
 *   - los limites de la zona de usuario;
 *   - la TablaMemoria del kernel: cada proceso cargado ocupa 3 posiciones
 *     (id, dato 1, dato 2). Las contiguas guardan inicio y tamano; la
 *     paginacion guarda la tabla de paginas como texto;
 *   - la copia del programa de cada proceso (la que esta en el disco), que
 *     se usa para devolverlo al swap y para traer paginas en un fallo.
 */
public abstract class EstrategiaMemoriaBase implements EstrategiaMemoria {

    /** Posiciones de cada entrada en la TablaMemoria (id, dato1, dato2). */
    public static final int POSICIONES_POR_BLOQUE = 3;

    protected final Memoria memoria;
    protected final int inicioUsuario;
    protected final int finUsuario;   // exclusivo

    /** Programa de cada proceso cargado, tal como esta en el disco. */
    protected final Map<Integer, List<Instruccion>> imagenes = new HashMap<>();

    protected EstrategiaMemoriaBase(Memoria memoria, int inicioUsuario, int tamanoUsuario) {
        if (tamanoUsuario <= 0) {
            throw new IllegalArgumentException("El espacio de usuario debe ser mayor a 0");
        }
        this.memoria = memoria;
        this.inicioUsuario = inicioUsuario;
        this.finUsuario = inicioUsuario + tamanoUsuario;
    }

    public int getInicioZonaUsuario() { return inicioUsuario; }
    public int getFinZonaUsuario()    { return finUsuario; }
    public int getEspacioTotal()      { return finUsuario - inicioUsuario; }

    @Override
    public List<Instruccion> leerImagen(int idProceso) {
        List<Instruccion> imagen = imagenes.get(idProceso);
        return imagen == null ? new ArrayList<>() : new ArrayList<>(imagen);
    }

    @Override
    public boolean cabeAlgunaVez(int tamano) {
        return tamano > 0 && tamano <= getEspacioTotal();
    }

    @Override
    public String motivoNoCabe(int tamano) {
        return "el programa tiene " + tamano + " instrucciones y la zona de usuario solo tiene "
                + getEspacioTotal() + " posiciones";
    }

    /* ==================== CELDAS ==================== */

    // Escribe el programa en posiciones consecutivas desde base.
    protected void escribirContiguo(int base, List<Instruccion> programa) {
        for (int i = 0; i < programa.size(); i++) {
            memoria.escribir(base + i, programa.get(i));
        }
    }

    // Deja vacias las posiciones [base, base + tamano).
    protected void vaciar(int base, int tamano) {
        memoria.liberarBloqueUsuario(base, tamano);
    }

    /* ==================== TABLA MEMORIA (kernel) ==================== */

    /**
     * Anota al proceso en la TablaMemoria del kernel. Si ya tenia una
     * entrada, la reescribe (la paginacion la actualiza en cada fallo).
     */
    protected void registrarEnTablaMemoria(int idProceso, Object dato1, Object dato2) {
        int bloques = memoria.getTamanoTablaMemoria() / POSICIONES_POR_BLOQUE;
        int libre = -1;
        for (int i = 0; i < bloques; i++) {
            int posId = i * POSICIONES_POR_BLOQUE;
            Object v = memoria.leerTablaMemoria(posId);
            if (v != null && v.equals(idProceso)) {
                libre = i;
                break;
            }
            if (v == null && libre < 0) libre = i;
        }
        if (libre < 0) {
            System.err.println("[TABLA_MEMORIA] Llena: no se pudo registrar P" + idProceso);
            return;
        }
        int posId = libre * POSICIONES_POR_BLOQUE;
        memoria.escribirTablaMemoria(posId, idProceso);
        memoria.escribirTablaMemoria(posId + 1, dato1);
        memoria.escribirTablaMemoria(posId + 2, dato2);
    }

    /** Borra la entrada del proceso en la TablaMemoria. */
    protected void eliminarDeTablaMemoria(int idProceso) {
        int bloques = memoria.getTamanoTablaMemoria() / POSICIONES_POR_BLOQUE;
        for (int i = 0; i < bloques; i++) {
            int posId = i * POSICIONES_POR_BLOQUE;
            Object v = memoria.leerTablaMemoria(posId);
            if (v != null && v.equals(idProceso)) {
                memoria.escribirTablaMemoria(posId, null);
                memoria.escribirTablaMemoria(posId + 1, null);
                memoria.escribirTablaMemoria(posId + 2, null);
                return;
            }
        }
    }
}
