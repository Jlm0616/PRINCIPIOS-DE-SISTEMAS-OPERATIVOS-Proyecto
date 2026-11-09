package logica.memoria;

import modelo.Instruccion;

import java.util.List;

/**
 * Estrategia de asignacion de la memoria de usuario (patron Estrategia,
 * igual que EstrategiaPlanificacion para la CPU).
 *
 * Implementaciones (Stallings, cap. 7 y 8):
 *   - Dinamica First Fit (ParticionadorDinamico), Next Fit, Best Fit.
 *   - Particiones fijas de igual tamano y de distinto tamano.
 *   - Paginacion simple (fisica) y paginacion virtual por demanda.
 *
 * DIRECCIONES. La CPU trabaja con la direccion que guarda el PC:
 *   - en las estrategias contiguas el PC es la direccion fisica (base del
 *     BCP = inicio del bloque o de la particion), y traducir la devuelve
 *     igual;
 *   - en paginacion el PC es una direccion LOGICA (base del BCP = 0) y
 *     traducir la convierte en marco + desplazamiento con la tabla de
 *     paginas del proceso.
 * Asi EjecutorCPU no cambia segun la estrategia: siempre pide traducir.
 */
public interface EstrategiaMemoria {

    /** Valor que devuelve traducir cuando la pagina no esta en memoria. */
    int FALLO_DE_PAGINA = -1;

    /** @return nombre de la estrategia, como se muestra en la GUI. */
    String getNombre();

    /**
     * Ubica el programa en la memoria de usuario y escribe sus instrucciones.
     *
     * @return la base para el BCP (fisica en las contiguas, 0 en paginacion),
     *         o -1 si ahora no hay lugar (el proceso espera en el swap)
     */
    int cargar(int idProceso, List<Instruccion> programa);

    /** Libera todo lo que ocupa el proceso: celdas, particion o marcos. */
    void liberar(int idProceso);

    /**
     * @return false si el programa no podra cargarse nunca (por ejemplo, es
     *         mas grande que la particion mas grande): se rechaza al cargar.
     */
    boolean cabeAlgunaVez(int tamano);

    /** @return el motivo por el que nunca cabe, para el mensaje de error. */
    String motivoNoCabe(int tamano);

    /**
     * Traduce la direccion del PC a direccion fisica, sin efectos.
     *
     * @return la direccion fisica, o FALLO_DE_PAGINA si la pagina no esta
     */
    int traducir(int idProceso, int direccion);

    /**
     * Lo llama la CPU en cada acceso: igual que traducir, pero ademas anota
     * el uso de la pagina (lo necesita el reemplazo LRU).
     */
    default int accederInstruccion(int idProceso, int direccion) {
        return traducir(idProceso, direccion);
    }

    /**
     * Atiende un fallo de pagina: trae la pagina del disco a un marco,
     * reemplazando otra si hace falta. Solo en paginacion virtual.
     *
     * @return descripcion de lo que paso, para la bitacora de la GUI
     */
    default String atenderFalloDePagina(int idProceso, int direccion) {
        throw new IllegalStateException(getNombre() + " no tiene fallos de pagina");
    }

    /** @return copia del programa del proceso (para devolverlo al swap). */
    List<Instruccion> leerImagen(int idProceso);

    /**
     * @return que hay en una celda de la zona de usuario, para la columna
     *         "Zona" de la tabla de memoria (por ejemplo "P2", "Part 3 libre",
     *         "M4: P2 pag 1"), o null si no se sabe.
     */
    String describirCelda(int direccion);

    /** @return id del proceso dueno de la celda, o -1 (para el color). */
    int procesoEnCelda(int direccion);

    /** @return una linea con el estado de la estrategia (huecos, marcos...). */
    String resumen();

    /** @return fallos de pagina del proceso (0 si la estrategia no pagina). */
    default int getFallosDePagina(int idProceso) {
        return 0;
    }
}
