package logica.memoria;

import modelo.Instruccion;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PAGINACION VIRTUAL por demanda (Stallings, seccion 8.1 y 8.2).
 *
 * Igual que la paginacion simple, pero NO se cargan todas las paginas:
 *   - Fetch Policy (p. 390): paginacion por DEMANDA. Al cargar el proceso
 *     solo entra la pagina 0 (la del PC inicial).
 *   - Si el PC cae en una pagina que no esta (bit P = 0 en la tabla de
 *     paginas, Figura 8.2) hay un FALLO DE PAGINA: el proceso pierde ese
 *     segundo mientras la pagina se trae del disco, y la instruccion se
 *     ejecuta en el segundo siguiente.
 *   - Resident Set Management (p. 398): ASIGNACION FIJA con ALCANCE LOCAL.
 *     Cada proceso recibe un numero fijo de marcos (parametro) al cargarse;
 *     cuando estan todos ocupados, reemplaza una de SUS paginas.
 *   - Replacement Policy (p. 392-394, Figura 8.14): FIFO (la pagina que
 *     lleva mas tiempo en memoria) o LRU (la que se uso hace mas tiempo).
 *
 * Las paginas son de codigo y nunca se modifican (bit M = 0), asi que al
 * reemplazar una no hace falta escribirla al disco.
 */
public class PaginacionVirtual extends Paginacion {

    public static final String NOMBRE = "Paginacion virtual";
    public static final String FIFO = "FIFO";
    public static final String LRU = "LRU";

    private final int marcosPorProceso;
    private final String reemplazo;

    /** Contador de eventos: marca cuando se cargo y cuando se uso cada marco. */
    private long reloj = 0;
    private final long[] cargadoEn;
    private final long[] usadoEn;

    private final Map<Integer, Integer> fallos = new HashMap<>();
    private int fallosTotales = 0;

    public PaginacionVirtual(Memoria memoria, int inicioUsuario, int tamanoUsuario,
                             int tamanoPagina, int marcosPorProceso, String reemplazo) {
        super(memoria, inicioUsuario, tamanoUsuario, tamanoPagina);
        if (marcosPorProceso < 1) {
            throw new IllegalArgumentException("Cada proceso necesita al menos 1 marco");
        }
        if (!FIFO.equalsIgnoreCase(reemplazo) && !LRU.equalsIgnoreCase(reemplazo)) {
            throw new IllegalArgumentException("Reemplazo no soportado: " + reemplazo
                    + " (FIFO o LRU)");
        }
        this.marcosPorProceso = marcosPorProceso;
        this.reemplazo = reemplazo.toUpperCase();
        this.cargadoEn = new long[cantidadMarcos];
        this.usadoEn = new long[cantidadMarcos];
    }

    @Override
    public String getNombre() {
        return NOMBRE;
    }

    /* ==================== ESTRATEGIA DE MEMORIA ==================== */

    /**
     * Reserva los marcos del proceso (su conjunto residente) y carga solo
     * la pagina 0. Si no hay marcos libres para todo el conjunto, espera.
     */
    @Override
    public int cargar(int idProceso, List<Instruccion> programa) {
        int paginas = paginasDe(programa.size());
        int reservar = Math.min(marcosPorProceso, paginas);
        List<Integer> libres = marcosLibres(reservar);
        if (libres.size() < reservar) return -1;

        imagenes.put(idProceso, new ArrayList<>(programa));
        int[] tabla = new int[paginas];
        java.util.Arrays.fill(tabla, -1);
        tablas.put(idProceso, tabla);
        for (int marco : libres) {
            duenoMarco[marco] = idProceso;
            paginaMarco[marco] = -1;
        }
        traerPagina(idProceso, 0, libres.get(0));
        fallos.put(idProceso, 0);
        actualizarTablaMemoria(idProceso);
        return 0;   // base logica
    }

    @Override
    public void liberar(int idProceso) {
        super.liberar(idProceso);
        fallos.remove(idProceso);
    }

    /** Solo hace falta que entren los marcos del conjunto residente. */
    @Override
    public boolean cabeAlgunaVez(int tamano) {
        return tamano > 0 && Math.min(marcosPorProceso, paginasDe(tamano)) <= cantidadMarcos;
    }

    @Override
    public String motivoNoCabe(int tamano) {
        return "cada proceso necesita " + marcosPorProceso + " marcos y la memoria solo tiene "
                + cantidadMarcos;
    }

    /** Como traducir, y ademas anota el uso del marco para LRU. */
    @Override
    public int accederInstruccion(int idProceso, int direccion) {
        int fisica = traducir(idProceso, direccion);
        if (fisica != FALLO_DE_PAGINA) {
            usadoEn[marcoEn(fisica)] = ++reloj;
        }
        return fisica;
    }

    @Override
    public String atenderFalloDePagina(int idProceso, int direccion) {
        int pagina = direccion / tamanoPagina;
        fallos.merge(idProceso, 1, Integer::sum);
        fallosTotales++;

        // 1. Un marco del proceso que todavia este vacio.
        for (int m = 0; m < cantidadMarcos; m++) {
            if (duenoMarco[m] == idProceso && paginaMarco[m] < 0) {
                traerPagina(idProceso, pagina, m);
                actualizarTablaMemoria(idProceso);
                return "fallo de pagina: P" + idProceso + " pag " + pagina
                        + " se trae del disco al marco M" + m + " (marco vacio)";
            }
        }

        // 2. Reemplazar una pagina del mismo proceso (alcance local).
        int victima = -1;
        for (int m = 0; m < cantidadMarcos; m++) {
            if (duenoMarco[m] != idProceso) continue;
            long criterio = LRU.equals(reemplazo) ? usadoEn[m] : cargadoEn[m];
            long mejor = victima < 0 ? Long.MAX_VALUE
                    : (LRU.equals(reemplazo) ? usadoEn[victima] : cargadoEn[victima]);
            if (criterio < mejor) victima = m;
        }
        int paginaSaliente = paginaMarco[victima];
        tablas.get(idProceso)[paginaSaliente] = -1;
        traerPagina(idProceso, pagina, victima);
        actualizarTablaMemoria(idProceso);
        return "fallo de pagina: P" + idProceso + " pag " + pagina + " se trae del disco al marco M"
                + victima + ", reemplaza la pag " + paginaSaliente + " (" + reemplazo + ")";
    }

    @Override
    public String resumen() {
        return super.resumen() + " | " + marcosPorProceso + " marcos por proceso | reemplazo "
                + reemplazo + " | fallos de pagina: " + fallosTotales;
    }

    @Override
    public int getFallosDePagina(int idProceso) {
        return fallos.getOrDefault(idProceso, 0);
    }

    public int getFallosTotales() { return fallosTotales; }

    public String getReemplazo() { return reemplazo; }

    public int getMarcosPorProceso() { return marcosPorProceso; }

    /* ==================== AYUDA ==================== */

    private void traerPagina(int idProceso, int pagina, int marco) {
        copiarPagina(idProceso, pagina, marco);
        cargadoEn[marco] = ++reloj;
        usadoEn[marco] = reloj;
    }
}
