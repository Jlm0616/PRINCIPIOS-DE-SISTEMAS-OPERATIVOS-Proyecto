package logica.memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de asignacion de memoria elegida y sus parametros. Viaja
 * junta desde config.txt hasta la ventana de configuracion y el
 * GestorProcesos, para no pasar seis valores sueltos.
 *
 * Inmutable: para cambiar un valor se crea otro objeto.
 */
public final class ParametrosMemoria {

    /** Estrategias disponibles, en el orden del combo de la configuracion. */
    public static final String[] ESTRATEGIAS = {
        logica.ParticionadorDinamico.NOMBRE, DinamicaNextFit.NOMBRE, DinamicaBestFit.NOMBRE,
        ParticionesFijas.NOMBRE_IGUAL, ParticionesFijas.NOMBRE_DISTINTA,
        Paginacion.NOMBRE, PaginacionVirtual.NOMBRE
    };

    public static final String[] REEMPLAZOS = { PaginacionVirtual.FIFO, PaginacionVirtual.LRU };

    public static final String ESTRATEGIA_DEFAULT = logica.ParticionadorDinamico.NOMBRE;
    public static final int PARTICION_TAMANO_DEFAULT = 16;
    public static final String PARTICIONES_DEFAULT = "8,16,32,64";
    public static final int PAGINA_TAMANO_DEFAULT = 4;
    public static final int MARCOS_POR_PROCESO_DEFAULT = 2;
    public static final String REEMPLAZO_DEFAULT = PaginacionVirtual.FIFO;

    private final String estrategia;
    private final int particionTamano;
    private final String particiones;
    private final int paginaTamano;
    private final int marcosPorProceso;
    private final String reemplazo;

    public ParametrosMemoria(String estrategia, int particionTamano, String particiones,
                             int paginaTamano, int marcosPorProceso, String reemplazo) {
        this.estrategia = normalizarEstrategia(estrategia);
        this.particionTamano = particionTamano;
        this.particiones = particiones == null ? PARTICIONES_DEFAULT : particiones.trim();
        this.paginaTamano = paginaTamano;
        this.marcosPorProceso = marcosPorProceso;
        this.reemplazo = reemplazo == null ? REEMPLAZO_DEFAULT : reemplazo.trim().toUpperCase();
    }

    /** @return los valores por defecto (First Fit). */
    public static ParametrosMemoria porDefecto() {
        return new ParametrosMemoria(ESTRATEGIA_DEFAULT, PARTICION_TAMANO_DEFAULT,
                PARTICIONES_DEFAULT, PAGINA_TAMANO_DEFAULT, MARCOS_POR_PROCESO_DEFAULT,
                REEMPLAZO_DEFAULT);
    }

    /**
     * @return el nombre tal como esta en ESTRATEGIAS, sin importar las
     *         mayusculas con que venga (config.txt), o null si no existe.
     */
    public static String normalizarEstrategia(String nombre) {
        if (nombre == null) return null;
        for (String e : ESTRATEGIAS) {
            if (e.equalsIgnoreCase(nombre.trim())) return e;
        }
        return null;
    }

    /**
     * Revisa los valores y devuelve un mensaje por cada problema (lista
     * vacia si todo esta bien). Solo se revisan los parametros de la
     * estrategia elegida.
     */
    public List<String> validar() {
        List<String> errores = new ArrayList<>();
        if (estrategia == null) {
            errores.add("Estrategia de memoria desconocida. Disponibles: "
                    + String.join(", ", ESTRATEGIAS));
            return errores;
        }
        switch (estrategia) {
            case ParticionesFijas.NOMBRE_IGUAL:
                if (particionTamano < 1) errores.add("El tamano de particion debe ser >= 1.");
                break;
            case ParticionesFijas.NOMBRE_DISTINTA:
                try {
                    getListaParticiones();
                } catch (IllegalArgumentException e) {
                    errores.add(e.getMessage());
                }
                break;
            case PaginacionVirtual.NOMBRE:
                if (marcosPorProceso < 1) errores.add("Cada proceso necesita al menos 1 marco.");
                if (!PaginacionVirtual.FIFO.equals(reemplazo) && !PaginacionVirtual.LRU.equals(reemplazo)) {
                    errores.add("El reemplazo debe ser FIFO o LRU.");
                }
                // sigue: tambien usa el tamano de pagina
            case Paginacion.NOMBRE:
                if (paginaTamano < 1) errores.add("El tamano de pagina debe ser >= 1.");
                break;
            default:
                break;
        }
        return errores;
    }

    /**
     * @return los tamanos de "particiones" ("8,16,32,64") como enteros.
     * @throws IllegalArgumentException si alguno no es un entero positivo
     */
    public int[] getListaParticiones() {
        String[] partes = particiones.split("\\s*,\\s*");
        int[] tamanos = new int[partes.length];
        for (int i = 0; i < partes.length; i++) {
            try {
                tamanos[i] = Integer.parseInt(partes[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Las particiones deben ser enteros separados"
                        + " por comas, por ejemplo 8,16,32,64 (se recibio \"" + particiones + "\").");
            }
            if (tamanos[i] < 1) {
                throw new IllegalArgumentException("Cada particion debe medir al menos 1"
                        + " (se recibio \"" + particiones + "\").");
            }
        }
        return tamanos;
    }

    public String getEstrategia()     { return estrategia; }
    public int getParticionTamano()   { return particionTamano; }
    public String getParticiones()    { return particiones; }
    public int getPaginaTamano()      { return paginaTamano; }
    public int getMarcosPorProceso()  { return marcosPorProceso; }
    public String getReemplazo()      { return reemplazo; }

    /** @return la estrategia con sus parametros, para los mensajes de la GUI. */
    public String describir() {
        if (estrategia == null) return "(desconocida)";
        switch (estrategia) {
            case ParticionesFijas.NOMBRE_IGUAL:
                return estrategia + " (particiones de " + particionTamano + ")";
            case ParticionesFijas.NOMBRE_DISTINTA:
                return estrategia + " (particiones " + particiones + ")";
            case Paginacion.NOMBRE:
                return estrategia + " (pagina de " + paginaTamano + ")";
            case PaginacionVirtual.NOMBRE:
                return estrategia + " (pagina de " + paginaTamano + ", " + marcosPorProceso
                        + " marcos por proceso, " + reemplazo + ")";
            default:
                return estrategia;
        }
    }

    @Override
    public String toString() {
        return "ParametrosMemoria{" + describir() + "}";
    }
}
