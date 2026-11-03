package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;
import modelo.EstadoProceso;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Planificador Selfish Round Robin (SRR, Kleinrock).
 *
 * Los procesos ACEPTADOS se atienden por turnos, igual que en RR, con
 * el mismo quantum. Un proceso NUEVO no entra a los turnos de inmediato:
 * espera en la cola de nuevos hasta que su prioridad alcanza la de los
 * aceptados.
 *
 * Reglas:
 *   - Al llegar, la prioridad del proceso es 0.
 *   - Cada segundo, la prioridad de cada nuevo sube en a y la de cada
 *     aceptado (incluido el que esta en CPU) sube en b.
 *   - Un nuevo es aceptado cuando su prioridad es mayor o igual a la
 *     menor prioridad de los aceptados, o cuando no hay aceptados.
 *
 * Casos limite (sirven para verificarlo):
 *   - b = 0:  los nuevos se aceptan al llegar, se comporta como RR.
 *   - b >= a: un nuevo nunca alcanza a los aceptados, se acepta uno a
 *             la vez cuando la cola se vacia, se comporta como FCFS.
 *
 * Los nuevos esperan dentro de la ListaProcesos (estado READY), asi se
 * siguen viendo en la GUI, pero seleccionarSiguiente() los salta.
 * GestorProcesos los mueve al final de la cola cuando son aceptados,
 * con las mismas reglas de llegada que RR.
 *
 * La prioridad de SRR se guarda en el campo Prioridad del BCP, asi se
 * ve en la memoria. La prioridad que el usuario haya puesto en la
 * ventana de llegadas no se usa con este algoritmo.
 */
public class PlanificadorSelfishRR implements EstrategiaPlanificacion {

    public static final String NOMBRE = "Selfish RR";

    private final int quantum;
    private final int incrementoNuevos;      // a
    private final int incrementoAceptados;   // b

    /** IDs de los procesos que el planificador ya registro. */
    private final Set<Integer> conocidos = new HashSet<>();

    /** IDs de los procesos aceptados (los que entran a los turnos). */
    private final Set<Integer> aceptados = new HashSet<>();

    /**
     * @param quantum             segundos de CPU por turno (>= 1)
     * @param incrementoNuevos    a: cuanto sube por segundo la prioridad de un nuevo (>= 1)
     * @param incrementoAceptados b: cuanto sube por segundo la de un aceptado (>= 0)
     */
    public PlanificadorSelfishRR(int quantum, int incrementoNuevos, int incrementoAceptados) {
        if (quantum < 1) {
            throw new IllegalArgumentException(
                "El quantum debe ser >= 1 (recibido: " + quantum + ")");
        }
        if (incrementoNuevos < 1) {
            throw new IllegalArgumentException(
                "El incremento de los nuevos (a) debe ser >= 1 (recibido: " + incrementoNuevos + ")");
        }
        if (incrementoAceptados < 0) {
            throw new IllegalArgumentException(
                "El incremento de los aceptados (b) debe ser >= 0 (recibido: " + incrementoAceptados + ")");
        }
        this.quantum = quantum;
        this.incrementoNuevos = incrementoNuevos;
        this.incrementoAceptados = incrementoAceptados;
    }

    /* ==================== SELECCION ==================== */

    /**
     * Devuelve el primer proceso ACEPTADO de la cola. Los nuevos se
     * saltan: todavia esperan a que su prioridad alcance a los aceptados.
     */
    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        for (BCP bcp : listaProcesos.toList()) {
            if (bcp.getEstado() == EstadoProceso.READY && aceptados.contains(bcp.getId())) {
                return bcp;
            }
        }
        return null;
    }

    /* ==================== COLAS Y PRIORIDADES ==================== */

    /**
     * Avanza un segundo las prioridades y acepta a los nuevos que
     * alcanzaron a los aceptados. GestorProcesos lo llama una vez por
     * segundo, antes de rotar por quantum.
     *
     * Un proceso que el planificador ve por primera vez entra como nuevo
     * con prioridad 0 y no sube en ese segundo.
     *
     * @param listaProcesos procesos READY (nuevos y aceptados)
     * @param enCPU         procesos que ocupan un nucleo (todos aceptados)
     * @return los aceptados en este segundo, en el orden en que deben
     *         entrar a la cola (mayor prioridad primero)
     */
    public List<BCP> actualizarPrioridades(ListaProcesos listaProcesos, List<BCP> enCPU) {
        List<BCP> enCola = listaProcesos.toList();
        olvidarTerminados(enCola, enCPU);

        // 1. Subir las prioridades de los que ya estaban.
        for (BCP bcp : enCPU) {
            if (conocidos.add(bcp.getId())) {
                // Nunca deberia pasar: solo se despachan aceptados.
                aceptados.add(bcp.getId());
                bcp.setPrioridad(0);
            } else {
                bcp.setPrioridad(bcp.getPrioridad() + incrementoAceptados);
            }
        }
        for (BCP bcp : enCola) {
            if (conocidos.add(bcp.getId())) {
                bcp.setPrioridad(0);
            } else if (aceptados.contains(bcp.getId())) {
                bcp.setPrioridad(bcp.getPrioridad() + incrementoAceptados);
            } else {
                bcp.setPrioridad(bcp.getPrioridad() + incrementoNuevos);
            }
        }

        // 2. Aceptar a los nuevos que alcanzaron la menor prioridad de los
        //    aceptados. Si no hay aceptados, entra el de mayor prioridad.
        List<BCP> nuevos = new ArrayList<>();
        for (BCP bcp : enCola) {
            if (bcp.getEstado() == EstadoProceso.READY && !aceptados.contains(bcp.getId())) {
                nuevos.add(bcp);
            }
        }
        if (nuevos.isEmpty()) return new ArrayList<>();

        Integer menorAceptado = null;
        for (BCP bcp : enCPU) {
            menorAceptado = menor(menorAceptado, bcp.getPrioridad());
        }
        for (BCP bcp : enCola) {
            if (aceptados.contains(bcp.getId())) {
                menorAceptado = menor(menorAceptado, bcp.getPrioridad());
            }
        }

        int umbral;
        if (menorAceptado != null) {
            umbral = menorAceptado;
        } else {
            umbral = Integer.MIN_VALUE;
            for (BCP bcp : nuevos) umbral = Math.max(umbral, bcp.getPrioridad());
        }

        List<BCP> entrantes = new ArrayList<>();
        for (BCP bcp : nuevos) {
            if (bcp.getPrioridad() >= umbral) entrantes.add(bcp);
        }
        // Mayor prioridad primero; con empate se respeta el orden de la cola
        // (List.sort es estable).
        entrantes.sort((x, y) -> Integer.compare(y.getPrioridad(), x.getPrioridad()));

        for (BCP bcp : entrantes) {
            aceptados.add(bcp.getId());
            System.out.println("[SRR] Proceso " + bcp.getId() + " aceptado (prioridad "
                    + bcp.getPrioridad() + ", menor de los aceptados: "
                    + (menorAceptado == null ? "ninguno" : menorAceptado) + ")");
        }
        return entrantes;
    }

    /**
     * @return true si hay algun proceso aceptado esperando en la cola.
     *         GestorProcesos lo usa para elegir el orden de llegada de RR
     *         (los nuevos no cuentan).
     */
    public boolean hayAceptadosEnCola(ListaProcesos listaProcesos) {
        for (BCP bcp : listaProcesos.toList()) {
            if (aceptados.contains(bcp.getId())) return true;
        }
        return false;
    }

    /**
     * @return true si el proceso ya fue aceptado (para la GUI).
     */
    public boolean esAceptado(BCP bcp) {
        return bcp != null && aceptados.contains(bcp.getId());
    }

    // Quita los procesos que ya no estan ni en la cola ni en un nucleo,
    // asi un ID que se reutilice entra como nuevo.
    private void olvidarTerminados(List<BCP> enCola, List<BCP> enCPU) {
        Set<Integer> presentes = new HashSet<>();
        for (BCP bcp : enCola) presentes.add(bcp.getId());
        for (BCP bcp : enCPU) presentes.add(bcp.getId());
        conocidos.retainAll(presentes);
        aceptados.retainAll(presentes);
    }

    private static Integer menor(Integer actual, int valor) {
        return (actual == null || valor < actual) ? valor : actual;
    }

    /* ==================== NOMBRE ==================== */

    @Override
    public String getNombre() {
        return NOMBRE;
    }

    /* ==================== APROPIATIVIDAD ==================== */

    @Override
    public boolean esApropiativo() {
        return true;
    }

    /* ==================== PARAMETROS ==================== */

    public int getQuantum() {
        return quantum;
    }

    public int getIncrementoNuevos() {
        return incrementoNuevos;
    }

    public int getIncrementoAceptados() {
        return incrementoAceptados;
    }

    @Override
    public String toString() {
        return "PlanificadorSelfishRR{q=" + quantum + ", a=" + incrementoNuevos
                + ", b=" + incrementoAceptados + "}";
    }
}
