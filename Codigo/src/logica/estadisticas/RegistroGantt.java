package logica.estadisticas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Registro del diagrama de Gantt EN VIVO.
 *
 * Guarda, para cada nucleo y cada proceso que paso por el, en que
 * instantes ejecuto y en cuales ocupo el nucleo esperando el disco
 * (INT 21H). Lo llena GestorProcesos en cada segundo y lo dibuja
 * PanelGanttVivo en la ventana principal mientras la simulacion corre.
 *
 * A diferencia de Estadisticas (que solo ve los procesos terminados),
 * aqui estan tambien los que siguen en ejecucion.
 *
 * Una fila es la pareja (nucleo, proceso): si un proceso ejecuta en
 * varios nucleos a lo largo del tiempo, aparece en la fila de cada uno.
 *
 * Los metodos son synchronized porque en la ejecucion automatica el
 * registro se llena desde un hilo de fondo mientras la GUI lo dibuja.
 */
public class RegistroGantt {

    /** nucleo -> (proceso -> fila), filas en orden de primera aparicion. */
    private final Map<Integer, Map<Integer, Fila>> filasPorNucleo = new TreeMap<>();

    private int ultimoInstante = 0;
    private int primerInstante = Integer.MAX_VALUE;

    /**
     * Registra que el proceso ejecuto en el nucleo durante el instante t.
     */
    public synchronized void registrarEjecucion(int idNucleo, int idProceso, int t) {
        fila(idNucleo, idProceso).ejecucion.add(t);
        actualizarLimites(t);
    }

    /**
     * Registra que el proceso ocupo el nucleo en el instante t esperando
     * el disco (BLOCKED por INT 21H): el nucleo no ejecuto a nadie mas.
     */
    public synchronized void registrarEspera(int idNucleo, int idProceso, int t) {
        fila(idNucleo, idProceso).espera.add(t);
        actualizarLimites(t);
    }

    /**
     * @return copia de las filas, agrupadas por nucleo (ascendente) y, dentro
     *         de cada nucleo, en el orden en que los procesos aparecieron.
     */
    public synchronized List<Fila> getFilas() {
        List<Fila> copia = new ArrayList<>();
        for (Map<Integer, Fila> filas : filasPorNucleo.values()) {
            for (Fila f : filas.values()) {
                copia.add(f.copiar());
            }
        }
        return copia;
    }

    /**
     * @return el ultimo instante registrado (0 si no hay ninguno).
     */
    public synchronized int getUltimoInstante() {
        return ultimoInstante;
    }

    /**
     * @return el primer instante registrado, o -1 si no hay ninguno.
     */
    public synchronized int getPrimerInstante() {
        return primerInstante == Integer.MAX_VALUE ? -1 : primerInstante;
    }

    private Fila fila(int idNucleo, int idProceso) {
        return filasPorNucleo
                .computeIfAbsent(idNucleo, k -> new LinkedHashMap<>())
                .computeIfAbsent(idProceso, k -> new Fila(idNucleo, idProceso));
    }

    private void actualizarLimites(int t) {
        if (t > ultimoInstante) ultimoInstante = t;
        if (t < primerInstante) primerInstante = t;
    }

    /**
     * Una fila del diagrama: un proceso dentro de un nucleo.
     */
    public static class Fila {
        private final int idNucleo;
        private final int idProceso;
        private final TreeSet<Integer> ejecucion = new TreeSet<>();
        private final TreeSet<Integer> espera = new TreeSet<>();

        Fila(int idNucleo, int idProceso) {
            this.idNucleo = idNucleo;
            this.idProceso = idProceso;
        }

        private Fila copiar() {
            Fila f = new Fila(idNucleo, idProceso);
            f.ejecucion.addAll(ejecucion);
            f.espera.addAll(espera);
            return f;
        }

        public int getIdNucleo()  { return idNucleo; }
        public int getIdProceso() { return idProceso; }

        /** @return instantes en que el proceso ejecuto en este nucleo. */
        public java.util.Set<Integer> getEjecucion() {
            return Collections.unmodifiableSet(ejecucion);
        }

        /** @return instantes en que el proceso ocupo el nucleo esperando el disco. */
        public java.util.Set<Integer> getEspera() {
            return Collections.unmodifiableSet(espera);
        }
    }
}
