package logica.memoria;

import modelo.Instruccion;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PAGINACION simple, fisica (Stallings, seccion 7.3, Figuras 7.9 a 7.11).
 *
 * La zona de usuario se divide en MARCOS de tamano fijo (parametro) y cada
 * programa en PAGINAS del mismo tamano. Al cargar el proceso se cargan
 * TODAS sus paginas, cada una en cualquier marco libre: no tienen que ser
 * contiguos. El SO mantiene:
 *   - la lista de marcos libres (aqui, los marcos sin dueno);
 *   - una tabla de paginas por proceso: el marco de cada pagina.
 *
 * Traduccion (la hace la CPU en cada fetch): con la direccion logica d,
 *   pagina = d / tamano, desplazamiento = d % tamano,
 *   fisica = inicioUsuario + marco * tamano + desplazamiento.
 *
 * El PC del proceso es una direccion LOGICA: la base del BCP es 0.
 *
 * No hay fragmentacion externa; solo interna, en la ultima pagina de cada
 * proceso. La tabla de paginas de cada proceso se copia en la TablaMemoria
 * del kernel ("0:M4 1:M7 ...").
 */
public class Paginacion extends EstrategiaMemoriaBase {

    public static final String NOMBRE = "Paginacion";

    protected final int tamanoPagina;
    protected final int cantidadMarcos;

    /** Proceso dueno de cada marco, o -1 si esta libre. */
    protected final int[] duenoMarco;
    /** Pagina que tiene cada marco, o -1 si esta vacio. */
    protected final int[] paginaMarco;

    /** Tabla de paginas de cada proceso: marco de cada pagina (-1 = no esta). */
    protected final Map<Integer, int[]> tablas = new HashMap<>();

    public Paginacion(Memoria memoria, int inicioUsuario, int tamanoUsuario, int tamanoPagina) {
        super(memoria, inicioUsuario, tamanoUsuario);
        if (tamanoPagina < 1) {
            throw new IllegalArgumentException("El tamano de pagina debe ser >= 1");
        }
        if (tamanoPagina > tamanoUsuario) {
            throw new IllegalArgumentException("La pagina de " + tamanoPagina
                    + " no cabe en la zona de usuario (" + tamanoUsuario + ")");
        }
        this.tamanoPagina = tamanoPagina;
        this.cantidadMarcos = tamanoUsuario / tamanoPagina;
        this.duenoMarco = new int[cantidadMarcos];
        this.paginaMarco = new int[cantidadMarcos];
        java.util.Arrays.fill(duenoMarco, -1);
        java.util.Arrays.fill(paginaMarco, -1);
    }

    @Override
    public String getNombre() {
        return NOMBRE;
    }

    /* ==================== ESTRATEGIA DE MEMORIA ==================== */

    /** Carga todas las paginas; si no hay marcos para todas, espera. */
    @Override
    public int cargar(int idProceso, List<Instruccion> programa) {
        int paginas = paginasDe(programa.size());
        List<Integer> libres = marcosLibres(paginas);
        if (libres.size() < paginas) return -1;

        imagenes.put(idProceso, new ArrayList<>(programa));
        int[] tabla = new int[paginas];
        for (int p = 0; p < paginas; p++) {
            int marco = libres.get(p);
            duenoMarco[marco] = idProceso;
            tabla[p] = marco;
        }
        tablas.put(idProceso, tabla);
        for (int p = 0; p < paginas; p++) {
            copiarPagina(idProceso, p, tabla[p]);
        }
        actualizarTablaMemoria(idProceso);
        return 0;   // base logica
    }

    @Override
    public void liberar(int idProceso) {
        for (int m = 0; m < cantidadMarcos; m++) {
            if (duenoMarco[m] == idProceso) {
                vaciar(inicioMarco(m), tamanoPagina);
                duenoMarco[m] = -1;
                paginaMarco[m] = -1;
            }
        }
        tablas.remove(idProceso);
        imagenes.remove(idProceso);
        eliminarDeTablaMemoria(idProceso);
    }

    @Override
    public boolean cabeAlgunaVez(int tamano) {
        return tamano > 0 && paginasDe(tamano) <= cantidadMarcos;
    }

    @Override
    public String motivoNoCabe(int tamano) {
        return "el programa necesita " + paginasDe(tamano) + " paginas de " + tamanoPagina
                + " y la memoria solo tiene " + cantidadMarcos + " marcos";
    }

    @Override
    public int traducir(int idProceso, int direccion) {
        int[] tabla = tablas.get(idProceso);
        if (tabla == null || direccion < 0) return FALLO_DE_PAGINA;
        int pagina = direccion / tamanoPagina;
        if (pagina >= tabla.length || tabla[pagina] < 0) return FALLO_DE_PAGINA;
        return inicioMarco(tabla[pagina]) + direccion % tamanoPagina;
    }

    @Override
    public String describirCelda(int direccion) {
        int m = marcoEn(direccion);
        if (m < 0) return "Sin marco";
        String etq = "M" + m;
        if (duenoMarco[m] < 0) return etq + " libre";
        if (paginaMarco[m] < 0) return etq + ": P" + duenoMarco[m] + " vacio";
        int logica = paginaMarco[m] * tamanoPagina + (direccion - inicioMarco(m));
        String base = etq + ": P" + duenoMarco[m] + " pag " + paginaMarco[m];
        return logica < imagenes.get(duenoMarco[m]).size() ? base : base + " (frag.)";
    }

    @Override
    public int procesoEnCelda(int direccion) {
        int m = marcoEn(direccion);
        if (m < 0 || paginaMarco[m] < 0) return -1;
        int logica = paginaMarco[m] * tamanoPagina + (direccion - inicioMarco(m));
        return logica < imagenes.get(duenoMarco[m]).size() ? duenoMarco[m] : -1;
    }

    @Override
    public String resumen() {
        int ocupados = 0;
        for (int d : duenoMarco) if (d >= 0) ocupados++;
        return getNombre() + " | pagina de " + tamanoPagina + " | marcos: " + ocupados
                + " de " + cantidadMarcos + " ocupados";
    }

    /* ==================== CONSULTAS ==================== */

    public int getTamanoPagina()   { return tamanoPagina; }
    public int getCantidadMarcos() { return cantidadMarcos; }

    /** @return copia de la tabla de paginas del proceso (-1 = no esta). */
    public int[] getTablaPaginas(int idProceso) {
        int[] t = tablas.get(idProceso);
        return t == null ? new int[0] : t.clone();
    }

    /* ==================== AYUDA ==================== */

    protected int paginasDe(int tamano) {
        return (tamano + tamanoPagina - 1) / tamanoPagina;
    }

    protected int inicioMarco(int marco) {
        return inicioUsuario + marco * tamanoPagina;
    }

    protected int marcoEn(int direccion) {
        if (direccion < inicioUsuario) return -1;
        int m = (direccion - inicioUsuario) / tamanoPagina;
        return m < cantidadMarcos ? m : -1;
    }

    /** Lista de marcos libres, en orden de direccion, hasta cantidad. */
    protected List<Integer> marcosLibres(int cantidad) {
        List<Integer> libres = new ArrayList<>();
        for (int m = 0; m < cantidadMarcos && libres.size() < cantidad; m++) {
            if (duenoMarco[m] < 0) libres.add(m);
        }
        return libres;
    }

    /** Copia la pagina p del programa (desde la imagen del disco) al marco. */
    protected void copiarPagina(int idProceso, int pagina, int marco) {
        List<Instruccion> imagen = imagenes.get(idProceso);
        vaciar(inicioMarco(marco), tamanoPagina);
        for (int k = 0; k < tamanoPagina; k++) {
            int logica = pagina * tamanoPagina + k;
            if (logica >= imagen.size()) break;
            memoria.escribir(inicioMarco(marco) + k, imagen.get(logica));
        }
        paginaMarco[marco] = pagina;
        tablas.get(idProceso)[pagina] = marco;
    }

    /** Copia la tabla de paginas del proceso en la TablaMemoria del kernel. */
    protected void actualizarTablaMemoria(int idProceso) {
        int[] tabla = tablas.get(idProceso);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < tabla.length; p++) {
            if (p > 0) sb.append(' ');
            sb.append(p).append(':').append(tabla[p] < 0 ? "-" : "M" + tabla[p]);
        }
        registrarEnTablaMemoria(idProceso, "Tabla de paginas (" + tabla.length + " pag.)",
                sb.toString());
    }
}
