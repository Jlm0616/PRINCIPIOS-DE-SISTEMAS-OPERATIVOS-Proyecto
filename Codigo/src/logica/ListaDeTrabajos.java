package logica;

import modelo.Memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de trabajos: cola FCFS de procesos que estan en DISCO
 * esperando ser cargados en RAM.
 *
 * Esta lista es una VISTA sobre una zona del kernel en la Memoria.
 * Cada entrada ocupa 4 posiciones:
 *   [0] nombre
 *   [1] inicio
 *   [2] fin
 *   [3] zona
 *
 * La ListaDeTrabajos en RAM apunta a los indices del disco:
 * guarda la misma info que el indice, pero solo para los .asm
 * que estan esperando entrar a RAM.
 *
 * Cuando un trabajo entra a RAM:
 *   - Se elimina la entrada de la ListaDeTrabajos (nombre = null).
 *   - El indice del disco se actualiza (zona VIRTUAL -> PRINCIPAL).
 *
 * El BCP NO existe hasta que el proceso entra a RAM (estado NEW).
 * Los datos extra del proceso en NEW (id, tiempo de llegada, prioridad)
 * los lleva el GestorProcesos en objetos TrabajoNuevo; esta lista es
 * solo el espejo en memoria del kernel.
 */
public class ListaDeTrabajos {

    private final Memoria memoria;

    /**
     * Crea la ListaDeTrabajos como vista sobre la zona de memoria.
     */
    public ListaDeTrabajos(Memoria memoria) {
        if (memoria == null) {
            throw new IllegalArgumentException("La memoria no puede ser nula");
        }
        this.memoria = memoria;
    }

    /* ==================== OPERACIONES ==================== */

    /**
     * Agrega una entrada al final de la lista (FCFS).
     *
     * @param nombre nombre del archivo .asm
     * @param inicio posicion de inicio en el disco
     * @param fin    posicion de fin en el disco
     * @param zona   "PRINCIPAL" o "VIRTUAL"
     * @return true si se agrego, false si la lista esta llena
     */
    public boolean agregar(String nombre, int inicio, int fin, String zona) {
        if (nombre == null || nombre.isEmpty()) return false;

        int entrada = memoria.buscarListaTrabajosLibre();
        if (entrada == -1) {
            return false;   // lista llena
        }

        memoria.escribirListaTrabajos(entrada, 0, nombre);
        memoria.escribirListaTrabajos(entrada, 1, inicio);
        memoria.escribirListaTrabajos(entrada, 2, fin);
        memoria.escribirListaTrabajos(entrada, 3, zona);
        return true;
    }

    /**
     * Saca la primera entrada de la lista (FCFS) y devuelve sus datos.
     *
     * @return arreglo [nombre, inicio, fin, zona], o null si la lista esta vacia
     */
    public Object[] sacarPrimero() {
        int entrada = buscarPrimeraEntrada();
        if (entrada == -1) return null;

        Object nombre = memoria.leerListaTrabajos(entrada, 0);
        Object inicio = memoria.leerListaTrabajos(entrada, 1);
        Object fin    = memoria.leerListaTrabajos(entrada, 2);
        Object zona   = memoria.leerListaTrabajos(entrada, 3);

        // Vaciar la entrada
        memoria.escribirListaTrabajos(entrada, 0, null);
        memoria.escribirListaTrabajos(entrada, 1, null);
        memoria.escribirListaTrabajos(entrada, 2, null);
        memoria.escribirListaTrabajos(entrada, 3, null);

        return new Object[]{nombre, inicio, fin, zona};
    }

    /**
     * Elimina una entrada concreta (la que tenga ese nombre y ese inicio
     * en el disco). Se usa cuando un proceso en NEW llega a su tiempo de
     * llegada y sale del swap: ya no es el primero de la cola, es el que
     * le toca por su TL.
     *
     * @return true si se encontro y elimino, false si no estaba
     */
    public boolean eliminar(String nombre, int inicio) {
        if (nombre == null) return false;

        int maxEntradas = memoria.getMaxArchivos();
        for (int i = 0; i < maxEntradas; i++) {
            Object n = memoria.leerListaTrabajos(i, 0);
            Object ini = memoria.leerListaTrabajos(i, 1);
            if (n != null && n.equals(nombre) && ini != null && ini.equals(inicio)) {
                for (int campo = 0; campo < 4; campo++) {
                    memoria.escribirListaTrabajos(i, campo, null);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Devuelve (sin sacar) la primera entrada de la lista.
     *
     * @return arreglo [nombre, inicio, fin, zona], o null si la lista esta vacia
     */
    public Object[] verPrimero() {
        int entrada = buscarPrimeraEntrada();
        if (entrada == -1) return null;

        return new Object[]{
            memoria.leerListaTrabajos(entrada, 0),
            memoria.leerListaTrabajos(entrada, 1),
            memoria.leerListaTrabajos(entrada, 2),
            memoria.leerListaTrabajos(entrada, 3)
        };
    }

    /**
     * Busca el indice de la primera entrada no vacia de la lista.
     *
     * @return el indice de la entrada, o -1 si la lista esta vacia
     */
    private int buscarPrimeraEntrada() {
        for (int i = 0; i < memoria.getTamanoListaTrabajos() / Memoria.POSICIONES_POR_ENTRADA_LISTA_TRABAJOS; i++) {
            Object nombre = memoria.leerListaTrabajos(i, 0);
            if (nombre != null) {
                return i;
            }
        }
        return -1;
    }

    /* ==================== CONSULTAS ==================== */

    // Indica si no hay trabajos en la lista.
    public boolean estaVacia() {
        return memoria.contarListaTrabajos() == 0;
    }

    // Devuelve cuántos trabajos hay en la lista.
    public int getCantidad() {
        return memoria.contarListaTrabajos();
    }

    // Devuelve cuántos trabajos caben en la lista.
    public int getCapacidad() {
        return memoria.getMaxArchivos();
    }

    // Indica si la lista está llena.
    public boolean estaLlena() {
        return getCantidad() >= getCapacidad();
    }

    /**
     * @return lista de arreglos [nombre, inicio, fin, zona] de los trabajos no vacios.
     */
    public List<Object[]> toList() {
        List<Object[]> lista = new ArrayList<>();
        int maxEntradas = memoria.getMaxArchivos();

        for (int i = 0; i < maxEntradas; i++) {
            Object nombre = memoria.leerListaTrabajos(i, 0);
            if (nombre != null) {
                lista.add(new Object[]{
                    nombre,
                    memoria.leerListaTrabajos(i, 1),
                    memoria.leerListaTrabajos(i, 2),
                    memoria.leerListaTrabajos(i, 3)
                });
            }
        }
        return lista;
    }

    @Override
    public String toString() {
        return "ListaDeTrabajos[" + getCantidad() + "/" + getCapacidad() + " en RAM]";
    }
}