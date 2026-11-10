package logica.estadisticas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Historial de resumenes de ejecuciones anteriores. Cada vez que el
 * usuario termina una ejecucion (normal o por error), se agrega un
 * ResumenEjecucion aqui.
 *
 * La rubrica pide que las estadisticas de cada ejecucion sean
 * ACUMULATIVAS: este historial las acumula para mostrarlas en la
 * pestaña "Acumulados" de VentanaEstadisticas.
 *
 * Los metodos son synchronized porque la GUI puede leer el historial
 * mientras la ejecucion automatica lo modifica (en el mismo hilo de
 * Swing, pero por seguridad).
 */
public class HistorialEjecuciones {

    private final List<ResumenEjecucion> resumenes = new ArrayList<>();
    private int siguienteNumero = 1;

    /**
     * Agrega un resumen al historial, numerandolo automaticamente.
     *
     * @param stats  estadisticas de la ejecucion
     * @param alg    nombre del algoritmo activo
     * @param error  mensaje si la ejecucion termino por error, o null
     * @return el resumen agregado
     */
    public synchronized ResumenEjecucion agregar(Estadisticas stats,
                                                 String alg,
                                                 String error) {
        ResumenEjecucion r = ResumenEjecucion.desde(siguienteNumero, alg, stats, error);
        resumenes.add(r);
        siguienteNumero++;
        return r;
    }

    /**
     * @return copia de los resumenes (nunca la lista interna).
     */
    public synchronized List<ResumenEjecucion> getResumenes() {
        return Collections.unmodifiableList(new ArrayList<>(resumenes));
    }

    /**
     * @return cuantas ejecuciones se han registrado.
     */
    public synchronized int getCantidad() {
        return resumenes.size();
    }

    /**
     * @return true si no hay ningun resumen guardado.
     */
    public synchronized boolean estaVacio() {
        return resumenes.isEmpty();
    }

    /**
     * Borra todo el historial y reinicia la numeracion. Se llama al
     * presionar "Limpiar" en la ventana principal.
     */
    public synchronized void limpiar() {
        resumenes.clear();
        siguienteNumero = 1;
    }
}