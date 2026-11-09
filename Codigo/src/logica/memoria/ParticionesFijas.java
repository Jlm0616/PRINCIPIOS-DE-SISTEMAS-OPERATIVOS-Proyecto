package logica.memoria;

import modelo.Instruccion;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Particionamiento FIJO (Stallings, seccion 7.2, "Fixed Partitioning",
 * Figuras 7.2 y 7.3).
 *
 * La zona de usuario se divide al iniciar en particiones que no cambian:
 *   - de IGUAL tamano: el tamano es un parametro y caben tantas como
 *     alcance la zona de usuario;
 *   - de DISTINTO tamano: los tamanos son una lista (parametro), en orden
 *     de direccion. Las que no caben en la zona de usuario se descartan.
 *
 * Colocacion: una sola cola para todas las particiones (Figura 7.3b, la
 * que recomienda el libro). Al cargar un proceso se elige la particion
 * LIBRE MAS PEQUENA donde quepa; con particiones iguales es la primera
 * libre. Si no hay ninguna libre, el proceso espera en el swap.
 *
 * Un programa mas grande que la particion mas grande no se podra ejecutar
 * nunca: se rechaza al cargarlo.
 *
 * Lo que sobra dentro de la particion es FRAGMENTACION INTERNA y se muestra
 * en la tabla de memoria.
 */
public class ParticionesFijas extends EstrategiaMemoriaBase {

    public static final String NOMBRE_IGUAL = "Fija igual";
    public static final String NOMBRE_DISTINTA = "Fija distinta";

    private final String nombre;
    private final int[] inicio;
    private final int[] tamano;
    private final int[] dueno;   // -1 si esta libre
    private final int[] usado;   // instrucciones del dueno

    /**
     * Particiones de igual tamano: tantas como quepan en la zona de usuario.
     */
    public static ParticionesFijas iguales(Memoria memoria, int inicioUsuario,
                                           int tamanoUsuario, int tamanoParticion) {
        if (tamanoParticion < 1) {
            throw new IllegalArgumentException("El tamano de particion debe ser >= 1");
        }
        int cantidad = tamanoUsuario / tamanoParticion;
        if (cantidad < 1) {
            throw new IllegalArgumentException("La particion de " + tamanoParticion
                    + " no cabe en la zona de usuario (" + tamanoUsuario + ")");
        }
        int[] tamanos = new int[cantidad];
        java.util.Arrays.fill(tamanos, tamanoParticion);
        return new ParticionesFijas(NOMBRE_IGUAL, memoria, inicioUsuario, tamanoUsuario, tamanos);
    }

    /**
     * Particiones de distinto tamano, en el orden dado. Se usan las que
     * quepan una tras otra en la zona de usuario.
     */
    public static ParticionesFijas distintas(Memoria memoria, int inicioUsuario,
                                             int tamanoUsuario, int[] tamanos) {
        List<Integer> caben = new ArrayList<>();
        int ocupado = 0;
        for (int t : tamanos) {
            if (t < 1) throw new IllegalArgumentException("Tamano de particion invalido: " + t);
            if (ocupado + t > tamanoUsuario) break;
            caben.add(t);
            ocupado += t;
        }
        if (caben.isEmpty()) {
            throw new IllegalArgumentException("Ninguna particion cabe en la zona de usuario ("
                    + tamanoUsuario + ")");
        }
        int[] arr = caben.stream().mapToInt(Integer::intValue).toArray();
        return new ParticionesFijas(NOMBRE_DISTINTA, memoria, inicioUsuario, tamanoUsuario, arr);
    }

    private ParticionesFijas(String nombre, Memoria memoria, int inicioUsuario,
                             int tamanoUsuario, int[] tamanos) {
        super(memoria, inicioUsuario, tamanoUsuario);
        this.nombre = nombre;
        int n = tamanos.length;
        this.inicio = new int[n];
        this.tamano = tamanos.clone();
        this.dueno = new int[n];
        this.usado = new int[n];
        int pos = inicioUsuario;
        for (int i = 0; i < n; i++) {
            inicio[i] = pos;
            pos += tamano[i];
            dueno[i] = -1;
        }
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    /* ==================== ESTRATEGIA DE MEMORIA ==================== */

    @Override
    public int cargar(int idProceso, List<Instruccion> programa) {
        int n = programa.size();
        int elegida = -1;
        for (int i = 0; i < tamano.length; i++) {
            if (dueno[i] < 0 && tamano[i] >= n
                    && (elegida < 0 || tamano[i] < tamano[elegida])) {
                elegida = i;
            }
        }
        if (elegida < 0) return -1;

        dueno[elegida] = idProceso;
        usado[elegida] = n;
        escribirContiguo(inicio[elegida], programa);
        imagenes.put(idProceso, new ArrayList<>(programa));
        registrarEnTablaMemoria(idProceso, inicio[elegida], tamano[elegida]);
        return inicio[elegida];
    }

    @Override
    public void liberar(int idProceso) {
        int i = particionDe(idProceso);
        if (i < 0) return;
        vaciar(inicio[i], usado[i]);
        dueno[i] = -1;
        usado[i] = 0;
        imagenes.remove(idProceso);
        eliminarDeTablaMemoria(idProceso);
    }

    @Override
    public boolean cabeAlgunaVez(int tamanoPrograma) {
        return tamanoPrograma > 0 && tamanoPrograma <= particionMasGrande();
    }

    @Override
    public String motivoNoCabe(int tamanoPrograma) {
        return "el programa tiene " + tamanoPrograma
                + " instrucciones y la particion mas grande es de " + particionMasGrande();
    }

    /** Contigua dentro de la particion: el PC ya es la direccion fisica. */
    @Override
    public int traducir(int idProceso, int direccion) {
        return direccion;
    }

    @Override
    public String describirCelda(int direccion) {
        int i = particionEn(direccion);
        if (i < 0) return "Sin particion";
        String etq = "Part " + i;
        if (dueno[i] < 0) {
            return direccion == inicio[i] ? etq + " libre (" + tamano[i] + ")" : etq + " libre";
        }
        if (direccion - inicio[i] < usado[i]) return etq + ": P" + dueno[i];
        return etq + ": frag. int.";
    }

    @Override
    public int procesoEnCelda(int direccion) {
        int i = particionEn(direccion);
        if (i < 0 || dueno[i] < 0) return -1;
        return (direccion - inicio[i] < usado[i]) ? dueno[i] : -1;
    }

    @Override
    public String resumen() {
        int ocupadas = 0, fragmentacion = 0;
        for (int i = 0; i < tamano.length; i++) {
            if (dueno[i] >= 0) {
                ocupadas++;
                fragmentacion += tamano[i] - usado[i];
            }
        }
        return getNombre() + " | particiones: " + ocupadas + " de " + tamano.length + " ocupadas"
                + " | fragmentacion interna: " + fragmentacion
                + " | mayor: " + particionMasGrande();
    }

    /* ==================== CONSULTAS ==================== */

    public int getCantidadParticiones() { return tamano.length; }

    public int getTamanoParticion(int i) { return tamano[i]; }

    public int getInicioParticion(int i) { return inicio[i]; }

    /** @return el proceso de la particion, o -1 si esta libre. */
    public int getDueno(int i) { return dueno[i]; }

    private int particionMasGrande() {
        int max = 0;
        for (int t : tamano) max = Math.max(max, t);
        return max;
    }

    private int particionDe(int idProceso) {
        for (int i = 0; i < dueno.length; i++) if (dueno[i] == idProceso) return i;
        return -1;
    }

    private int particionEn(int direccion) {
        for (int i = 0; i < inicio.length; i++) {
            if (direccion >= inicio[i] && direccion < inicio[i] + tamano[i]) return i;
        }
        return -1;
    }
}
