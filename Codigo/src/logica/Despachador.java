package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Memoria;
import modelo.Disco;

import java.util.function.Consumer;

/**
 * Despachador de procesos.
 *
 * Su unica responsabilidad es EJECUTAR la decision del planificador:
 * carga el BCP elegido en la CPU, ejecuta UN SEGUNDO de CPU, y
 * actualiza el estado del proceso.
 *
 * Guarda la DIRECCION del BCP actual (no el objeto BCP).
 * El BCP se reconstruye como VISTA sobre esa direccion cuando se necesita.
 *
 * Modela el peso de las instrucciones: una instruccion de peso N
 * requiere N llamadas a ejecutarUnPaso() para completarse.
 *
 * Implementa los pasos del cambio de contexto descritos por Stallings
 * (seccion 3.4, "Change of Process State").
 */
public class Despachador {

    private final CPU cpu;
    private final Memoria memoria;
    private Disco disco;

    /** Direccion base del BCP actual (-1 si no hay proceso). */
    private int direccionBCPActual;

    /** Ejecutor del ciclo fetch-decode-execute del proceso actual. */
    private EjecutorCPU ejecutorActual;

    /* Callbacks de interrupciones */
    private Consumer<String> salidaPantalla;
    private Consumer<BCP> solicitudArchivo;

    // Crea el despachador con la CPU y memoria indicadas.
    public Despachador(CPU cpu, Memoria memoria) {
        this.cpu = cpu;
        this.memoria = memoria;
        this.direccionBCPActual = -1;
        this.ejecutorActual = null;
    }

    /* ==================== DESPACHO ==================== */

    /**
     * Despacha un BCP: lo carga en la CPU y prepara el ejecutor.
     * Corresponde a los pasos 5 y 7 del cambio de contexto.
     *
     * @param direccionBCP direccion base del BCP a despachar
     */
    public void despachar(int direccionBCP) {
        if (direccionBCP < 0) {
            throw new IllegalArgumentException("Direccion de BCP invalida: " + direccionBCP);
        }

        // Crear vista sobre el BCP
        BCP bcp = new BCP(memoria, direccionBCP);

        // Paso 5: actualizar el BCP del proceso seleccionado
        bcp.setEstado(EstadoProceso.RUNNING);
        bcp.setCpuAsignado(0);
        bcp.marcarInicio();

        // Paso 7: restaurar el contexto del procesador
        bcp.actualizarHaciaCPU(cpu);

        // Crear un ejecutor para este proceso
        this.direccionBCPActual = direccionBCP;
        this.ejecutorActual = new EjecutorCPU(cpu, memoria, bcp, disco);

        // Propagar callbacks de interrupciones
        ejecutorActual.setSalidaPantalla(salidaPantalla);
        ejecutorActual.setSolicitudArchivo(solicitudArchivo);
    }

    /* ==================== EJECUCION ==================== */

    /**
     * Ejecuta UN SEGUNDO de CPU del proceso despachado.
     *
     * @return true si el proceso sigue vivo y no bloqueado
     */
    public boolean ejecutarUnPaso() {
        if (ejecutorActual == null) {
            throw new IllegalStateException("No hay proceso despachado");
        }
        if (ejecutorActual.isProgramaTerminado()) {
            return false;
        }

        // Si el BCP esta BLOCKED, no ejecutar la siguiente instruccion
        if (ejecutorActual.getBcp().getEstado() == EstadoProceso.BLOCKED) {
            return false;
        }

        ejecutorActual.ejecutarSegundoDeCPU();

        return !ejecutorActual.isProgramaTerminado()
            && ejecutorActual.getBcp().getEstado() != EstadoProceso.BLOCKED;
    }

    /**
     * Guarda el contexto del proceso actual (pasos 1 y 2).
     */
    public void guardarContexto() {
        if (direccionBCPActual < 0) return;
        BCP bcp = new BCP(memoria, direccionBCPActual);
        bcp.actualizarDesdeCPU(cpu);
    }

    /* ==================== CONSULTAS ==================== */

    public EjecutorCPU getEjecutorActual() {
        return ejecutorActual;
    }

    /**
     * @return el BCP actual como vista sobre memoria, o null si no hay.
     */
    public BCP getBcpActual() {
        if (direccionBCPActual < 0) return null;
        return new BCP(memoria, direccionBCPActual);
    }

    /**
     * @return la direccion base del BCP actual, o -1 si no hay.
     */
    public int getDireccionBCPActual() {
        return direccionBCPActual;
    }

    // Indica si el proceso actual está listo para seguir ejecutando.
    public boolean procesoActualOcupaCPU() {
        if (ejecutorActual == null) return false;
        if (ejecutorActual.isProgramaTerminado()) return false;
        if (ejecutorActual.getBcp().getEstado() == EstadoProceso.BLOCKED) return false;
        return true;
    }

    // Libera el ejecutor y marca que no hay BCP actual.
    public void limpiarEjecutor() {
        this.ejecutorActual = null;
        this.direccionBCPActual = -1;
    }

    /* ==================== CALLBACKS ==================== */

    // Configura el callback de salida a pantalla.
    public void setSalidaPantalla(Consumer<String> callback) {
        this.salidaPantalla = callback;
    }

    // Configura el callback de solicitud de archivo.
    public void setSolicitudArchivo(Consumer<BCP> callback) {
        this.solicitudArchivo = callback;
    }

    // Asigna el disco que usarán las interrupciones.
    public void setDisco(Disco disco) {
        this.disco = disco;
    }
}