package logica;

import logica.memoria.EstrategiaMemoriaBase;
import modelo.Instruccion;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estrategia de particionamiento DINAMICO de la zona usuario.
 *
 * Usa FIRST-FIT (Stallings, seccion 7.2, "Placement Algorithm"): recorre
 * la memoria desde el inicio y usa el primer hueco que alcance. Next Fit
 * y Best Fit (logica.memoria) heredan de esta clase y solo cambian
 * {@link #elegirHueco(int)}.
 *
 * Cada asignacion/liberacion se sincroniza con la TablaMemoria del kernel.
 * Cada bloque asignado ocupa 3 posiciones:
 *   [0] = idProceso (Integer)
 *   [1] = inicio    (Integer)
 *   [2] = tamano    (Integer)
 *
 * El tamano de la TablaMemoria es proporcional a bcpsQueCaben:
 *   tamanoTablaMemoria = bcpsQueCaben × 3
 * (un bloque por cada BCP que cabe en el kernel).
 */
public class ParticionadorDinamico extends EstrategiaMemoriaBase {

    /** Cuantas posiciones ocupa cada bloque en la TablaMemoria. */
    public static final int POSICIONES_POR_BLOQUE = EstrategiaMemoriaBase.POSICIONES_POR_BLOQUE;

    public static final String NOMBRE = "First Fit";

    protected final List<BloqueLibre> bloquesLibres;

    /** Base del bloque de cada proceso cargado. */
    private final Map<Integer, Integer> basePorProceso = new HashMap<>();

    // Crea el particionador con un unico hueco libre que cubre toda la zona usuario.
    public ParticionadorDinamico(Memoria memoria,
                                  int inicioZonaUsuario,
                                  int espacioUsuarioDisponible) {
        super(memoria, inicioZonaUsuario, espacioUsuarioDisponible);
        this.bloquesLibres = new ArrayList<>();
        this.bloquesLibres.add(new BloqueLibre(inicioZonaUsuario, espacioUsuarioDisponible));
    }

    @Override
    public String getNombre() {
        return NOMBRE;
    }

    /* ==================== ESTRATEGIA DE MEMORIA ==================== */

    @Override
    public int cargar(int idProceso, List<Instruccion> programa) {
        int base = asignarParticion(programa.size(), idProceso);
        if (base == -1) return -1;
        escribirContiguo(base, programa);
        basePorProceso.put(idProceso, base);
        imagenes.put(idProceso, new ArrayList<>(programa));
        return base;
    }

    @Override
    public void liberar(int idProceso) {
        Integer base = basePorProceso.remove(idProceso);
        List<Instruccion> imagen = imagenes.remove(idProceso);
        if (base == null || imagen == null) return;
        vaciar(base, imagen.size());
        liberarParticion(base, imagen.size(), idProceso);
    }

    /** Contigua: el PC ya es la direccion fisica. */
    @Override
    public int traducir(int idProceso, int direccion) {
        return direccion;
    }

    @Override
    public String describirCelda(int direccion) {
        int dueno = procesoEnCelda(direccion);
        if (dueno >= 0) return "P" + dueno;
        for (BloqueLibre b : bloquesLibres) {
            if (direccion >= b.inicio && direccion < b.inicio + b.tamano) {
                return direccion == b.inicio ? "Hueco (" + b.tamano + ")" : "Hueco";
            }
        }
        return null;
    }

    @Override
    public int procesoEnCelda(int direccion) {
        for (Map.Entry<Integer, Integer> e : basePorProceso.entrySet()) {
            int base = e.getValue();
            int tamano = imagenes.get(e.getKey()).size();
            if (direccion >= base && direccion < base + tamano) return e.getKey();
        }
        return -1;
    }

    @Override
    public String resumen() {
        return getNombre() + " | huecos: " + getCantidadHuecos()
                + " | libre: " + getEspacioLibre() + " de " + getEspacioTotal()
                + " | hueco mayor: " + getHuecoMasGrande()
                + " (fragmentacion externa: " + (getEspacioLibre() - getHuecoMasGrande()) + ")";
    }

    /* ==================== ASIGNACION ==================== */

    // Asigna una particion con la politica de elegirHueco y registra el bloque en la TablaMemoria.
    public int asignarParticion(int tamano, int idProceso) {
        if (tamano <= 0) return -1;

        int i = elegirHueco(tamano);
        if (i < 0) return -1;

        BloqueLibre bloque = bloquesLibres.get(i);
        int base = bloque.inicio;
        if (bloque.tamano == tamano) {
            bloquesLibres.remove(i);
        } else {
            bloque.inicio += tamano;
            bloque.tamano -= tamano;
        }

        registrarEnTablaMemoria(idProceso, base, tamano);
        despuesDeAsignar(base, tamano);
        return base;
    }

    /**
     * Elige el hueco donde va el bloque. First Fit: el primero, en orden de
     * direccion, que alcance.
     *
     * @return indice en bloquesLibres, o -1 si ninguno alcanza
     */
    protected int elegirHueco(int tamano) {
        for (int i = 0; i < bloquesLibres.size(); i++) {
            if (bloquesLibres.get(i).tamano >= tamano) return i;
        }
        return -1;
    }

    /** Aviso para las subclases (Next Fit guarda donde quedo). */
    protected void despuesDeAsignar(int base, int tamano) {
    }

    /* ==================== LIBERACION ==================== */

    // Libera una particion, fusiona huecos vecinos y limpia la TablaMemoria.
    public void liberarParticion(int base, int tamano, int idProceso) {
        if (tamano <= 0) return;

        bloquesLibres.add(new BloqueLibre(base, tamano));
        fusionarBloques();

        eliminarDeTablaMemoria(idProceso);
    }

    // Fusiona huecos contiguos en uno solo. Deja la lista ordenada por direccion.
    private void fusionarBloques() {
        bloquesLibres.sort((a, b) -> Integer.compare(a.inicio, b.inicio));

        int i = 0;
        while (i < bloquesLibres.size() - 1) {
            BloqueLibre actual = bloquesLibres.get(i);
            BloqueLibre siguiente = bloquesLibres.get(i + 1);

            if (actual.inicio + actual.tamano == siguiente.inicio) {
                actual.tamano += siguiente.tamano;
                bloquesLibres.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /* ==================== CONSULTAS ==================== */

    // Devuelve el espacio libre total en la zona usuario.
    public int getEspacioLibre() {
        int total = 0;
        for (BloqueLibre b : bloquesLibres) total += b.tamano;
        return total;
    }

    // Devuelve cuántos huecos libres hay actualmente.
    public int getCantidadHuecos() { return bloquesLibres.size(); }

    // Devuelve el tamaño del hueco libre más grande.
    public int getHuecoMasGrande() {
        int max = 0;
        for (BloqueLibre b : bloquesLibres) if (b.tamano > max) max = b.tamano;
        return max;
    }

    // Indica si existe algún hueco con espacio suficiente.
    public boolean hayEspacioPara(int tamano) {
        for (BloqueLibre b : bloquesLibres) if (b.tamano >= tamano) return true;
        return false;
    }

    // Representa un hueco libre como (inicio, tamano).
    protected static class BloqueLibre {
        public int inicio;
        public int tamano;
        BloqueLibre(int inicio, int tamano) {
            this.inicio = inicio;
            this.tamano = tamano;
        }
    }

    @Override
    public String toString() {
        return "ParticionadorDinamico[" + bloquesLibres.size() + " huecos, "
                + getEspacioLibre() + "/" + getEspacioTotal() + " libre]";
    }
}
