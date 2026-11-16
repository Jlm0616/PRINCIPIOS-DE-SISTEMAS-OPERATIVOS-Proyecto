package logica;

import logica.cpu.GestorCPUs;
import logica.cpu.Nucleo;
import logica.estadisticas.IntervaloEjecucion;
import logica.estadisticas.RegistroGantt;
import logica.memoria.EstrategiaMemoria;
import logica.planificacion.PlanificadorFCFS;
import logica.planificacion.PlanificadorGuaranteed;
import logica.planificacion.PlanificadorHRRN;
import logica.planificacion.PlanificadorLottery;
import logica.planificacion.PlanificadorMLFQ;
import logica.planificacion.PlanificadorPrioridad;
import logica.planificacion.PlanificadorRR;
import logica.planificacion.PlanificadorSelfishRR;
import logica.planificacion.PlanificadorSPN;
import logica.planificacion.PlanificadorSRT;
import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;
import modelo.Disco;
import modelo.ProgramaPendiente;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 *
 * PROYECTO 2:
 *  - Todo el tiempo usa el RELOJ GLOBAL SIMULADO (int), no LocalDateTime.
 *  - Convencion de tiempo (la del libro, Stallings tabla 9.5): el paso N
 *    cubre el intervalo de N-1 a N, que es la columna N del Gantt. Un
 *    proceso que llega en t puede ejecutarse desde el paso t+1.
 *  - procesoTerminado() marca el fin con el relojGlobal y calcula el
 *    turnaround como (relojGlobal - tiempoLlegada).
 *  - El balanceo recibe el relojGlobal para propagarlo al Despachador.
 *  - SRT: expulsa si un proceso en READY tiene menor tiempo restante.
 *  - RR:  expulsa por quantum (reglas R4/R5 segun cola vacia o no).
 *  - HRRN: elige por mayor (W+S)/S. Necesita el relojGlobal.
 *  - Prioridad: elige por MENOR prioridad numerica (Stallings).
 *  - Lottery: elige al azar por boletos. Es apropiativo por quantum.
 *  - MLFQ: 3 niveles (Q1, Q2, Q3) con quantums 1, 2, 4. Apropiativo por
 *    quantum. Un proceso nuevo entra a Q1; al agotar su quantum baja de
 *    nivel; en Q3 se queda (RR puro). Al volver de I/O vuelve a Q1.
 *  - DIAGRAMA DE GANTT: registra que proceso ejecuto en cada instante.
 *
 * ESTADO NEW (tiempo de llegada futuro):
 *  - Un proceso cuyo TL aun no llega NO tiene BCP. Su programa esta en la
 *    zona Swap del disco y se representa con un TrabajoNuevo (id, nombre,
 *    TL, prioridad, direccion de swap).
 *  - El TL se genera al cargar (RandomizadorTiempos: [reloj, reloj + 6]).
 *    Si es futuro, el proceso va directo al swap, sin particion ni BCP.
 *  - Cuando relojGlobal supera su TL y hay particion libre, se crea el BCP
 *    (con el mismo id), se cargan las instrucciones en RAM y pasa a READY.
 *  - Un proceso es un TrabajoNuevo O un BCP, nunca los dos a la vez.
 *  - Los trabajos que ya llegaron pero no caben en RAM tambien esperan en
 *    NEW (en swap) hasta que haya particion libre.
 *
 * ACTUALIZACION:
 *  - Los programas con TL futuro permanecen en pendientesPorLlegar.
 *  - Mientras estan pendientes no tienen BCP ni ocupan RAM o swap.
 *  - Al alcanzar su llegada, pasan a READY si hay memoria disponible.
 *  - Si ya llegaron y no hay memoria, pasan a NEW en swap.
 */
public class GestorProcesos {

    public static final int MAX_PROCESOS = 5;
    public static final int QUANTUM_DEFAULT = 1;
    public static final int SRR_A_DEFAULT = 2;
    public static final int SRR_B_DEFAULT = 1;

    private static final int PRIORIDAD_DEFAULT  = 1;

    private static final int TARDANZA_CREAR     = 1;
    private static final int TARDANZA_ABRIR     = 1;
    // Cada INT 21H ocupa 1 s, como cualquier instruccion (requisito 14).
    private static final int TARDANZA_LEER      = 1;
    private static final int TARDANZA_ESCRIBIR  = 1;
    private static final int TARDANZA_ELIMINAR  = 1;
    private static final int TARDANZA_DEFAULT   = 1;

    private Memoria memoria;
    private ListaProcesos listaProcesos;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private GestorCPUs gestorCPUs;
    // Estrategia de asignacion de la memoria de usuario (First Fit, Next Fit,
    // Best Fit, particiones fijas, paginacion o paginacion virtual).
    private EstrategiaMemoria particionador;
    private Disco disco;

    // Genera el tiempo de llegada aleatorio de cada proceso al cargarlo
    private RandomizadorTiempos randomizador = new RandomizadorTiempos();

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosInput;

    // === Reloj global ===
    private int relojGlobal = 0;

    // Diagrama de Gantt en vivo: que proceso ocupo cada nucleo en cada instante.
    private final RegistroGantt registroGantt = new RegistroGantt();

    // Recibe los cambios de contexto para mostrarlos en la GUI.
    private java.util.function.Consumer<String> bitacoraContexto;

    // Procesos en estado NEW (sin BCP, programa en Swap).
    // CopyOnWriteArrayList: la GUI lee esta lista desde otro hilo mientras
    // la ejecucion automatica la modifica.
    private final List<TrabajoNuevo> trabajosNuevos = new CopyOnWriteArrayList<>();

    // Programas cuyo tiempo de llegada aun no se cumple.
    // No tienen BCP, no ocupan RAM ni swap y no aparecen en PanelProcesos.
    private final List<ProgramaPendiente> pendientesPorLlegar =
            new CopyOnWriteArrayList<>();

    // Nombre del .asm de cada proceso (solo para mostrar en la GUI)
    private final Map<Integer, String> nombresPorId = new HashMap<>();

    /** Procesos que se pueden asignar a un mismo nucleo (grupos de 5 por CPU). */
    public static final int PROCESOS_POR_NUCLEO = 5;

    /** Valor de nucleo que significa "cualquier nucleo libre". */
    public static final int NUCLEO_CUALQUIERA = -1;

    // Nucleo asignado por el usuario a cada proceso (id -> nucleo). Si no
    // esta, el proceso puede ir a cualquier nucleo libre.
    private final Map<Integer, Integer> nucleoPorProceso = new java.util.concurrent.ConcurrentHashMap<>();

    // === (Diagrama Gantt): ejecuciones por proceso y CPU ===
    private Map<Integer, List<IntervaloEjecucion>> intervalosPorProceso = new HashMap<>();
    private Map<Integer, Integer> cpuPorProceso = new HashMap<>();

    // === (RR, Lottery, MLFQ): contador de quantum por proceso ===
    private Map<Integer, Integer> quantumConsumidoPorProceso = new HashMap<>();
    private int quantumRR = QUANTUM_DEFAULT;

    private java.util.function.Consumer<String> salidaPantalla;

    // === Error de la ultima ejecucion (si la hubo) ===
    // Se llena cuando ejecutarAutomatico() o ejecutarUnPaso() detectan
    // un problema: timeout por posible bucle infinito, InterruptedException,
    // o una excepcion no controlada. Se muestra en VentanaEstadisticas.
    private String errorEjecucion = null;

    /**
     * Constructor legacy (sin quantum). Usa QUANTUM_DEFAULT.
     */
    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          int cantidadCPUs,
                          String algoritmoCPU,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          EstrategiaMemoria particionador,
                          Disco disco) {
        this(memoria, cpu, cantidadCPUs, algoritmoCPU,
             listaProcesos, listaDeTrabajos, particionador, disco,
             QUANTUM_DEFAULT);
    }

    /**
     * Constructor con quantum (sin parametros de Selfish RR).
     * Usa SRR_A_DEFAULT y SRR_B_DEFAULT.
     */
    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          int cantidadCPUs,
                          String algoritmoCPU,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          EstrategiaMemoria particionador,
                          Disco disco,
                          int quantumRR) {
        this(memoria, cpu, cantidadCPUs, algoritmoCPU,
             listaProcesos, listaDeTrabajos, particionador, disco,
             quantumRR, SRR_A_DEFAULT, SRR_B_DEFAULT);
    }

    /**
     * Constructor completo: quantum y parametros de Selfish RR.
     *
     * @param srrA cuanto sube por segundo la prioridad de un proceso nuevo
     * @param srrB cuanto sube por segundo la prioridad de un aceptado
     */
    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          int cantidadCPUs,
                          String algoritmoCPU,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          EstrategiaMemoria particionador,
                          Disco disco,
                          int quantumRR,
                          int srrA,
                          int srrB) {
        this.memoria = memoria;
        this.listaProcesos = listaProcesos;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;

        if (quantumRR < 1) quantumRR = QUANTUM_DEFAULT;
        this.quantumRR = quantumRR;
        if (srrA < 1) srrA = SRR_A_DEFAULT;
        if (srrB < 0) srrB = SRR_B_DEFAULT;

        this.planificador = new Planificador(listaProcesos);
        // Seleccion del algoritmo segun la configuracion. Sin distinguir
        // mayusculas: config.txt guarda el nombre en mayusculas y el combo
        // de la configuracion lo da como se muestra ("Prioridad").
        if ("SPN".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorSPN());
        } else if ("SRT".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorSRT());
        } else if ("RR".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorRR(quantumRR));
        } else if ("HRRN".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorHRRN());
        } else if ("Prioridad".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorPrioridad());
        } else if (PlanificadorSelfishRR.NOMBRE.equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorSelfishRR(quantumRR, srrA, srrB));
        } else if ("Lottery".equalsIgnoreCase(algoritmoCPU)) {
            PlanificadorLottery lottery = new PlanificadorLottery();
            lottery.setBoletos(bcp -> getBoletos(bcp.getId(), bcp.getRafaga()));
            this.planificador.setEstrategia(lottery);
        } else if ("Guaranteed".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorGuaranteed());
        } else if ("MLFQ".equalsIgnoreCase(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorMLFQ());
        } else {
            this.planificador.setEstrategia(new PlanificadorFCFS());
        }

        this.gestorCPUs = new GestorCPUs(cantidadCPUs, memoria, memoria.getLimiteKernelUsuario(), disco);
        for (Nucleo n : gestorCPUs.getNucleos()) {
            n.setBitacora(this::anunciarContexto);
            // La CPU traduce cada direccion con la estrategia de memoria
            // (en paginacion, el PC es una direccion logica).
            n.getDespachador().setEstrategiaMemoria(particionador);
        }

        this.siguienteId = 1;
        this.procesosTerminados = new ArrayList<>();
        this.procesosBloqueadosInput = new ArrayList<>();
        this.disco = disco;
    }

    public int getRelojGlobal() {
        return relojGlobal;
    }

    /** Permite inyectar un randomizador con semilla (pruebas). */
    public void setRandomizador(RandomizadorTiempos randomizador) {
        if (randomizador != null) this.randomizador = randomizador;
    }

    /**
     * @return el mensaje de error de la ultima ejecucion, o null si
     *         no hubo error. Se muestra en la ventana de estadisticas.
     */
    public String getErrorEjecucion() {
        return errorEjecucion;
    }

    /** Limpia el error de la ultima ejecucion (al reiniciar el sistema). */
    public void limpiarErrorEjecucion() {
        this.errorEjecucion = null;
    }

    /**
     * Permite a la GUI marcar un error de ejecucion que no ocurrio dentro
     * de GestorProcesos (por ejemplo, una cancelacion del SwingWorker o
     * una excepcion en el hilo de fondo de la GUI).
     */
    public void setErrorEjecucionExterno(String mensaje) {
        if (mensaje != null) {
            this.errorEjecucion = mensaje;
        }
    }

    /** Envia un mensaje a la pantalla de la GUI (si hay callback). */
    private void anunciar(String mensaje) {
        if (salidaPantalla != null) salidaPantalla.accept(mensaje);
    }

    /* ==================== CREACIÓN DE PROCESOS ==================== */

    public ResultadoCarga cargarPrograma(File archivo) {
        Ensamblador ensamblador = new Ensamblador();

        if (!ensamblador.esArchivoValido(archivo)) {
            return ResultadoCarga.error(ensamblador.getErroresComoTexto());
        }

        List<Instruccion> instrucciones = ensamblador.leerArchivo(archivo);
        int tamanoNecesario = instrucciones.size();
        int id = siguienteId;
        String nombreArchivo = archivo.getName();

        // Con particiones fijas o paginacion, un programa puede no caber
        // NUNCA (mas grande que la particion mas grande o que todos los
        // marcos): se rechaza en vez de dejarlo esperando para siempre.
        if (!particionador.cabeAlgunaVez(tamanoNecesario)) {
            return ResultadoCarga.error(nombreArchivo + " no se puede cargar con "
                    + particionador.getNombre() + ": "
                    + particionador.motivoNoCabe(tamanoNecesario) + ".");
        }

        // TL aleatorio en [relojGlobal, relojGlobal + 6]
        int tiempoLlegada = randomizador.generarTiempoLlegada(relojGlobal);

        // CASO 1: llega en el futuro -> queda PENDIENTE.
        // No se reserva particion, BCP ni espacio en swap.
        if (tiempoLlegada > relojGlobal) {
            pendientesPorLlegar.add(new ProgramaPendiente(
                    id, nombreArchivo, tiempoLlegada,
                    PRIORIDAD_DEFAULT, instrucciones));

            nombresPorId.put(id, nombreArchivo);
            siguienteId++;

            anunciar(">> ID " + id + " (" + nombreArchivo
                    + ") pendiente de llegada en t=" + tiempoLlegada + ".");

            return ResultadoCarga.pendiente(id);
        }

        // CASO 2: llega ya (TL = reloj) -> intentar cargarlo en RAM.
        // Primero se mira que quede un BCP libre: sin BCP no se pide memoria.
        int base = (memoria.getBCPsLibres() > 0)
                ? particionador.cargar(id, instrucciones) : -1;
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base, id);
            if (bcp != null) {
                bcp.setTiempoLlegada(tiempoLlegada);
                copiarAsmAArchivos(nombreArchivo, instrucciones);
                nombresPorId.put(id, nombreArchivo);
                siguienteId++;
                return ResultadoCarga.exito(bcp);
            }
            particionador.liberar(id);
        }

        // CASO 3: llega ya pero no cupo en RAM -> NEW en swap esperando hueco
        String error = guardarEnSwap(instrucciones, id, nombreArchivo,
                tiempoLlegada, PRIORIDAD_DEFAULT, false);
        if (error == null) {
            nombresPorId.put(id, nombreArchivo);
            siguienteId++;
            anunciar(">> ID " + id + " (" + nombreArchivo
                    + ") en NEW: sin hueco en RAM, esperando en swap.");
            return ResultadoCarga.enEspera();
        }

        return ResultadoCarga.error(
            "No hay espacio en memoria principal (ni kernel ni partición), "
            + "y la memoria virtual del disco está llena.\n"
            + "Libera espacio o aumenta el disco en 'Configurar'.");
    }

    private void copiarAsmAArchivos(String nombreArchivo, List<Instruccion> instrucciones) {
        int tamano = instrucciones.size();
        int inicio = disco.reservarBloqueArchivo(tamano);
        if (inicio == -1) {
            System.out.println("[WARNING] Sin espacio en zona de archivos para " + nombreArchivo);
            return;
        }
        disco.escribirBloqueArchivo(inicio, instrucciones.toArray());
        System.out.println("[DISCO] .asm guardado en zona archivos: " + nombreArchivo);
    }

    /**
     * Guarda el programa en la zona Swap, lo registra en el indice ASM y en
     * la ListaDeTrabajos, y crea el TrabajoNuevo (estado NEW, sin BCP).
     *
     * @return null si todo salio bien, o el mensaje de error
     */
    private String guardarEnSwap(List<Instruccion> instrucciones, int id, String nombreArchivo,
                                 int tiempoLlegada, int prioridad, boolean archivoCopiado) {
        int tamano = instrucciones.size();

        if (disco.getEspacioSwapLibre() < tamano) {
            return "La memoria virtual del disco no tiene espacio.";
        }

        int dirSwap = disco.reservarBloqueSwap(tamano);
        if (dirSwap == -1) {
            return "No hay bloque contiguo libre en el swap.";
        }

        disco.escribirBloqueSwap(dirSwap, instrucciones.toArray());

        int finSwap = dirSwap + tamano - 1;
        boolean registrado = disco.registrarArchivoAsm(nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!registrado) {
            disco.liberarBloqueSwap(dirSwap, tamano);
            return "No se pudo registrar en el indice ASM.";
        }

        boolean agregado = listaDeTrabajos.agregar(nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!agregado) {
            disco.liberarBloqueSwap(dirSwap, tamano);
            disco.eliminarDelIndice(nombreArchivo, Disco.TIPO_ASM);
            return "La ListaDeTrabajos esta llena.";
        }

        trabajosNuevos.add(new TrabajoNuevo(id, nombreArchivo, tiempoLlegada,
                prioridad, dirSwap, tamano, archivoCopiado));
        return null;
    }

    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int base, int id) {
        int direccionBase = memoria.reservarBloqueBCP();
        if (direccionBase == -1) return null;

        BCP bcp = new BCP(memoria, direccionBase, id, PRIORIDAD_DEFAULT);
        admitirEnParticion(bcp, instrucciones, base);
        return bcp;
    }

    /* ============ PROCESOS EN NEW (sin BCP) <-> READY ============ */

    /**
     * Devuelve un proceso READY al estado NEW: copia su programa al swap,
     * libera su particion y DESTRUYE su BCP. Solo aplica si todavia no
     * empezo a ejecutar.
     *
     * @return true si el proceso quedo en NEW
     */
    private boolean enviarANuevo(BCP bcp) {
        int id = bcp.getId();
        String nombre = nombresPorId.getOrDefault(id, "proceso" + id + ".asm");

        // La estrategia guarda la copia del programa: en paginacion virtual
        // no todas sus paginas estan en la memoria.
        List<Instruccion> instrucciones = particionador.leerImagen(id);

        String error = guardarEnSwap(instrucciones, id, nombre,
                bcp.getTiempoLlegada(), bcp.getPrioridad(), true);
        if (error != null) {
            System.out.println("[NEW] No se pudo mandar ID " + id + " a swap: " + error
                + " Sigue en RAM.");
            return false;
        }

        listaProcesos.sacar(bcp);
        particionador.liberar(id);
        memoria.liberarBloqueBCP(bcp.getDireccionBase());

        System.out.println("[NEW] Proceso " + id + " vuelve a NEW (sin BCP, en swap).");
        return true;
    }

    /**
     * Devuelve un proceso READY al estado pendiente cuando su nuevo TL
     * es futuro. Libera la particion y el BCP sin ocupar swap.
     *
     * @return true si el proceso quedo pendiente
     */
    private boolean enviarAPendiente(BCP bcp) {
        int id = bcp.getId();
        String nombre = nombresPorId.getOrDefault(id, "proceso" + id + ".asm");

        // Se conserva una copia de sus instrucciones antes de liberar la RAM.
        List<Instruccion> instrucciones = particionador.leerImagen(id);

        if (instrucciones == null || instrucciones.isEmpty()) {
            return false;
        }

        pendientesPorLlegar.add(new ProgramaPendiente(
                id, nombre, bcp.getTiempoLlegada(),
                bcp.getPrioridad(), instrucciones));

        listaProcesos.sacar(bcp);
        particionador.liberar(id);
        memoria.liberarBloqueBCP(bcp.getDireccionBase());

        System.out.println("[PENDIENTE] Proceso " + id
                + " vuelve a pendiente (sin BCP, RAM ni swap).");

        return true;
    }

    /**
     * Devuelve un trabajo NEW a la lista de pendientes cuando el TL
     * se modifica a un instante futuro. Libera su espacio de swap.
     */
    private boolean enviarNuevoAPendiente(TrabajoNuevo t) {
        Object[] instruccionesArr =
                disco.leerBloqueSwap(t.getDirSwap(), t.getTamano());

        List<Instruccion> instrucciones = new ArrayList<>();

        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        if (instrucciones.size() != t.getTamano()) {
            return false;
        }

        pendientesPorLlegar.add(new ProgramaPendiente(
                t.getId(), t.getNombre(),
                t.getTiempoLlegada(), t.getPrioridad(),
                instrucciones));

        disco.liberarBloqueSwap(t.getDirSwap(), t.getTamano());

        if (t.getNombre() != null
                && disco.existe(t.getNombre(), Disco.TIPO_ASM)) {
            disco.eliminarDelIndice(t.getNombre(), Disco.TIPO_ASM);
        }

        listaDeTrabajos.eliminar(t.getNombre(), t.getDirSwap());
        trabajosNuevos.remove(t);

        System.out.println("[PENDIENTE] Proceso " + t.getId()
                + " vuelve a pendiente (sin BCP, RAM ni swap).");

        return true;
    }

    /**
     * Un proceso solo puede volver a NEW si esta en la cola de listos, sin
     * haber ejecutado nada.
     */
    private boolean puedeVolverANuevo(BCP bcp) {
        return listaProcesos.contiene(bcp)
                && bcp.getEstado() == EstadoProceso.READY
                && !bcp.tieneInicioMarcado();
    }

    /**
     * Swap-in: reserva particion y bloque de BCP, lee el programa desde la
     * direccion de swap, CREA el BCP (mismo id), carga las instrucciones
     * en RAM y deja el proceso READY. El BCP queda en listaProcesos.
     *
     * @return el BCP creado, o null si no hay particion/BCP libre
     *         (el trabajo sigue en NEW)
     */
    private BCP admitirTrabajo(TrabajoNuevo t) {
        int tamano = t.getTamano();

        // Se lee el programa del swap primero (sin borrarlo): la estrategia
        // necesita las instrucciones para ubicarlas en la memoria.
        Object[] instruccionesArr = disco.leerBloqueSwap(t.getDirSwap(), tamano);
        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) instrucciones.add((Instruccion) obj);
        }

        // Sin BCP libre no se pide memoria (evita reservar y devolver).
        if (memoria.getBCPsLibres() == 0) return null;

        int base = particionador.cargar(t.getId(), instrucciones);
        if (base == -1) return null;

        int direccionBCP = memoria.reservarBloqueBCP();
        if (direccionBCP == -1) {
            particionador.liberar(t.getId());
            return null;
        }

        if (!t.isArchivoCopiado()) {
            copiarAsmAArchivos(t.getNombre(), instrucciones);
        }

        BCP bcp = new BCP(memoria, direccionBCP, t.getId(), t.getPrioridad());
        bcp.setTiempoLlegada(t.getTiempoLlegada());
        admitirEnParticion(bcp, instrucciones, base);

        // Limpiar el swap, el indice y la ListaDeTrabajos
        disco.liberarBloqueSwap(t.getDirSwap(), tamano);
        if (t.getNombre() != null && disco.existe(t.getNombre(), Disco.TIPO_ASM)) {
            disco.eliminarDelIndice(t.getNombre(), Disco.TIPO_ASM);
        }
        listaDeTrabajos.eliminar(t.getNombre(), t.getDirSwap());
        trabajosNuevos.remove(t);

        System.out.println("[NEW->READY] Proceso " + t.getId() + " (" + t.getNombre()
            + ") cargado en RAM base=" + base);
        return bcp;
    }

    /** Copia de los trabajos en NEW ordenada por llegada (TL, luego id). */
    private List<TrabajoNuevo> trabajosPorLlegada() {
        List<TrabajoNuevo> orden = new ArrayList<>(trabajosNuevos);
        orden.sort(Comparator.comparingInt(TrabajoNuevo::getTiempoLlegada)
                             .thenComparingInt(TrabajoNuevo::getId));
        return orden;
    }

    /**
     * Admite a los procesos NEW cuyo TL ya paso (TL < relojGlobal: el paso
     * actual cubre de relojGlobal-1 a relojGlobal). Respeta el orden de
     * llegada: si el primero no tiene particion, los demas esperan.
     *
     * Los BCP devueltos se SACAN de listaProcesos para que el resto de
     * ejecutarUnPaso decida el orden de entrada a la cola.
     */
    private List<BCP> admitirLlegados() {
        List<BCP> admitidos = new ArrayList<>();
        for (TrabajoNuevo t : trabajosPorLlegada()) {
            if (t.getTiempoLlegada() >= relojGlobal) continue;   // aun no llega

            BCP bcp = admitirTrabajo(t);
            if (bcp == null) {
                System.out.println("[RELOJ " + relojGlobal + "] Proceso " + t.getId()
                    + " ya llego pero no hay particion libre: sigue en NEW.");
                break;
            }
            listaProcesos.sacar(bcp);
            admitidos.add(bcp);
        }
        return admitidos;
    }

    /**
     * Admite los programas pendientes cuyo tiempo de llegada ya paso.
     * Si hay RAM y BCP disponible pasan a READY; si no, pasan a NEW.
     *
     * El parametro incluirTiempoActual se usa al editar el TL en la GUI.
     */
    private List<BCP> admitirPendientes(boolean incluirTiempoActual) {
        List<BCP> admitidos = new ArrayList<>();
        List<ProgramaPendiente> orden = new ArrayList<>(pendientesPorLlegar);

        orden.sort(Comparator
                .comparingInt(ProgramaPendiente::getTiempoLlegada)
                .thenComparingInt(ProgramaPendiente::getId));

        for (ProgramaPendiente pendiente : orden) {
            int tiempoLlegada = pendiente.getTiempoLlegada();

            if (incluirTiempoActual) {
                if (tiempoLlegada > relojGlobal) continue;
            } else {
                // El paso N cubre N-1 a N. TL=t ejecuta desde t+1.
                if (tiempoLlegada >= relojGlobal) continue;
            }

            int id = pendiente.getId();
            List<Instruccion> instrucciones = pendiente.getInstrucciones();

            int base = (memoria.getBCPsLibres() > 0)
                    ? particionador.cargar(id, instrucciones) : -1;

            if (base != -1) {
                BCP bcp = crearProcesoEnParticion(instrucciones, base, id);

                if (bcp != null) {
                    bcp.setTiempoLlegada(tiempoLlegada);
                    bcp.setPrioridad(pendiente.getPrioridad());

                    copiarAsmAArchivos(pendiente.getNombreArchivo(), instrucciones);

                    pendientesPorLlegar.remove(pendiente);

                    // Se saca temporalmente para que ejecutarUnPaso
                    // conserve las reglas originales de cada planificador.
                    listaProcesos.sacar(bcp);
                    admitidos.add(bcp);

                    System.out.println("[RELOJ " + relojGlobal + "] Proceso "
                            + id + " PENDIENTE -> READY.");
                    continue;
                }

                particionador.liberar(id);
            }

            // El programa ya llego, pero no hay RAM: usar NEW en swap.
            String error = guardarEnSwap(instrucciones, id,
                    pendiente.getNombreArchivo(), tiempoLlegada,
                    pendiente.getPrioridad(), false);

            if (error == null) {
                pendientesPorLlegar.remove(pendiente);

                System.out.println("[RELOJ " + relojGlobal + "] Proceso "
                        + id + " PENDIENTE -> NEW (swap).");
            } else {
                System.out.println("[RELOJ " + relojGlobal + "] Proceso "
                        + id + " sigue pendiente: " + error);
            }
        }

        return admitidos;
    }

    private TrabajoNuevo buscarTrabajoNuevo(int id) {
        for (TrabajoNuevo t : trabajosNuevos) {
            if (t.getId() == id) return t;
        }
        return null;
    }

    /** Busca un programa que aun no tiene BCP ni ocupa RAM o swap. */
    private ProgramaPendiente buscarProgramaPendiente(int id) {
        for (ProgramaPendiente pendiente : pendientesPorLlegar) {
            if (pendiente.getId() == id) return pendiente;
        }
        return null;
    }

    /* ==================== EJECUCIÓN PASO A PASO ==================== */

    public boolean ejecutarUnPaso() {
        // 1. Avanzar el reloj global
        relojGlobal++;

        // 1.5. Propagar el reloj a la estrategia (HRRN lo necesita).
        planificador.setRelojGlobal(relojGlobal);

        // 2. Recolectar las llegadas (sin agregarlas a la cola todavia).
        //    Este paso cubre el intervalo de relojGlobal-1 a relojGlobal:
        //    entra quien llego a mas tardar en relojGlobal-1. Con "<=" un
        //    proceso que llega en t competiria por el intervalo de t-1 a t,
        //    un segundo antes de llegar.
        //    Aqui se CREA el BCP de los procesos que salen de NEW.
        List<BCP> llegadosAhora = admitirLlegados();

        // Tambien se admiten los programas pendientes cuyo TL ya paso.
        // Se usa el mismo orden de incorporacion a READY.
        llegadosAhora.addAll(admitirPendientes(false));

        // 3. Si NO usa quantum: agregar llegadas directo + expulsiones SRT
        if (!usaQuantum()) {
            for (BCP bcp : llegadosAhora) {
                listaProcesos.agregar(bcp);
                System.out.println("[RELOJ " + relojGlobal + "] Proceso " + bcp.getId() + " ha llegado.");
            }

            if (planificador.esApropiativo()) {
                procesarExpulsiones();
            }
        }

        // 3.5. RR, Selfish RR, Lottery y MLFQ: rotacion por quantum CON orden
        //      especial de llegadas. En RR entran a la cola de turnos los que
        //      llegan; en Selfish RR, los que se aceptan en este segundo.
        if (usaQuantum()) {
            for (BCP bcp : llegadosAhora) {
                System.out.println("[RELOJ " + relojGlobal + "] Proceso " + bcp.getId() + " ha llegado.");
            }

            List<BCP> entrantes = llegadosAhora;
            boolean colaVaciaAntes = listaProcesos.estaVacia();

            if (esSelfishRR()) {
                PlanificadorSelfishRR srr = (PlanificadorSelfishRR) planificador.getEstrategia();
                // Los que llegan esperan en la cola de nuevos: estan en la
                // lista, pero el planificador no los elige hasta aceptarlos.
                for (BCP bcp : llegadosAhora) {
                    listaProcesos.agregar(bcp);
                }
                colaVaciaAntes = !srr.hayAceptadosEnCola(listaProcesos);
                entrantes = srr.actualizarPrioridades(listaProcesos, procesosEnCPU());
            }

            if (colaVaciaAntes) {
                // R4: cola vacia -> primero los nuevos, luego rota el de CPU
                for (BCP bcp : entrantes) {
                    encolarAlFinal(bcp);
                }

                procesarRotacionPorQuantum();

            } else {
                // R5: cola NO vacia -> primero rota el de CPU, luego llegan
                procesarRotacionPorQuantum();

                for (BCP bcp : entrantes) {
                    encolarAlFinal(bcp);
                }
            }

            // MLFQ: un proceso en una cola mas alta (por ejemplo, recien
            // llegado a Q1) expropia al que corre en una cola mas baja.
            if (esMLFQ()) {
                procesarExpulsiones();
            }
        }

        // 4. Delegar la asignación al Gestor de CPUs (que usa el Balanceador)
        gestorCPUs.balancear(listaProcesos, planificador, relojGlobal, this::puedeEjecutarEn);

        // 5. Capturar los RUNNING ANTES de ejecutar el paso.
        //    Los BLOCKED por INT 21H siguen ocupando su nucleo: se marcan en
        //    el Gantt en vivo como espera de disco.
        Map<Integer, Integer> ejecucionesEnPaso = new HashMap<>();
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null && bcp.getEstado() == EstadoProceso.RUNNING) {
                    ejecucionesEnPaso.put(bcp.getId(), nucleo.getIdNucleo());
                } else if (bcp != null && bcp.getEstado() == EstadoProceso.BLOCKED) {
                    registroGantt.registrarEspera(nucleo.getIdNucleo(), bcp.getId(), relojGlobal);
                }
            }
        }

        // 6. Ejecutar un paso de CPU en los núcleos
        List<BCP> terminadosEnEstePaso = new ArrayList<>();
        gestorCPUs.ejecutarPasoEnTodos(terminadosEnEstePaso);

        // 6.5. Fallos de pagina (paginacion virtual): el proceso perdio el
        //      segundo esperando que su pagina llegara del disco. No ejecuto
        //      instruccion: en el Gantt se marca como espera y no gasta quantum.
        java.util.Set<Integer> conFallo = new java.util.HashSet<>();
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            EjecutorCPU ejecutor = nucleo.getDespachador().getEjecutorActual();
            String fallo = (ejecutor != null) ? ejecutor.tomarFalloDePagina() : null;
            if (fallo != null) {
                BCP bcp = nucleo.getBcpActual();
                conFallo.add(bcp.getId());
                registroGantt.registrarEspera(nucleo.getIdNucleo(), bcp.getId(), relojGlobal);
                anunciarContexto("CPU " + nucleo.getIdNucleo() + ": " + fallo);
            }
        }

        // 7. Registrar ejecuciones + incrementar contador de quantum
        //    (RR, Selfish RR, Lottery y MLFQ)
        for (Map.Entry<Integer, Integer> e : ejecucionesEnPaso.entrySet()) {
            int idProceso = e.getKey();
            if (conFallo.contains(idProceso)) continue;
            registrarEjecucion(idProceso, e.getValue(), relojGlobal);

            if (usaQuantum()) {
                int c = quantumConsumidoPorProceso.getOrDefault(idProceso, 0) + 1;
                quantumConsumidoPorProceso.put(idProceso, c);
            }
        }

        // 8. Procesar los terminados
        for (BCP bcp : terminadosEnEstePaso) {
            System.out.println("[CPU] Proceso " + bcp.getId() + " TERMINÓ.");
            procesoTerminado(bcp);
        }

        // 9. Actualizar estados de BLOCKED y descontar tiempo
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null && bcp.getEstado() == EstadoProceso.BLOCKED) {

                    if (bcp.getPesoPendiente() == 0) {
                        int tardanza = calcularTardanzaDisco(bcp);
                        bcp.setPesoPendiente(tardanza);
                        anunciarContexto("CPU " + nucleo.getIdNucleo() + ": P" + bcp.getId()
                            + " pasa a BLOCKED por E/S de disco (INT 21H, " + tardanza
                            + " s); sigue ocupando el nucleo");
                    }

                    if (bcp.getPesoPendiente() > 0) {
                        bcp.setPesoPendiente(bcp.getPesoPendiente() - 1);
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId()
                            + " esperando disco... (" + bcp.getPesoPendiente() + "s)");
                    }

                    if (bcp.getPesoPendiente() == 0) {
                        bcp.setEstado(EstadoProceso.RUNNING);
                        if (esMLFQ()) {
                            ((PlanificadorMLFQ) planificador.getEstrategia()).resetearNivel(bcp);
                            System.out.println("[MLFQ] Proceso " + bcp.getId()
                                + " vuelve de I/O -> Q1.");
                        }
                        anunciarContexto("CPU " + nucleo.getIdNucleo() + ": P" + bcp.getId()
                            + " termina la E/S de disco y vuelve a RUNNING"
                            + (esMLFQ() ? " (MLFQ: vuelve a Q1)" : ""));
                    }
                }
            }
        }

        return hayProcesosActivos();
    }

    /**
     * @return true si la estrategia activa es Round Robin.
     */
    private boolean esRR() {
        return "RR".equals(planificador.getNombreEstrategia());
    }

    /**
     * @return true si la estrategia activa es Selfish Round Robin.
     */
    private boolean esSelfishRR() {
        return planificador.getEstrategia() instanceof PlanificadorSelfishRR;
    }

    /**
     * @return true si la estrategia activa es Lottery.
     *         (instanceof: no depende del texto que devuelva getNombre()).
     */
    private boolean esLottery() {
        return planificador.getEstrategia() instanceof PlanificadorLottery;
    }

    /**
     * @return true si la estrategia activa es MLFQ.
     */
    private boolean esMLFQ() {
        return planificador.getEstrategia() instanceof PlanificadorMLFQ;
    }

    /**
     * @return true si la estrategia reparte la CPU por quantum
     *         (RR, Selfish RR, Lottery o MLFQ).
     */
    private boolean usaQuantum() {
        return esRR() || esSelfishRR() || esLottery() || esMLFQ();
    }

    /**
     * Pone el proceso al final de la cola. En Selfish RR el proceso ya
     * esta en la lista (esperaba como nuevo), por eso primero se saca.
     */
    private void encolarAlFinal(BCP bcp) {
        listaProcesos.sacar(bcp);
        listaProcesos.agregar(bcp);
    }

    /**
     * @return los procesos que ocupan un nucleo (en ejecucion o esperando el disco).
     */
    private List<BCP> procesosEnCPU() {
        List<BCP> enCPU = new ArrayList<>();
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (nucleo.estaOcupado() && nucleo.getBcpActual() != null) {
                enCPU.add(nucleo.getBcpActual());
            }
        }
        return enCPU;
    }

    /**
     * Registra que un proceso ejecuto en un instante y en una CPU.
     */
    private void registrarEjecucion(int idProceso, int idCpu, int t) {
        cpuPorProceso.put(idProceso, idCpu);
        registroGantt.registrarEjecucion(idCpu, idProceso, t);

        List<IntervaloEjecucion> lista =
                intervalosPorProceso.computeIfAbsent(idProceso, k -> new ArrayList<>());

        if (!lista.isEmpty()) {
            IntervaloEjecucion ultimo = lista.get(lista.size() - 1);
            IntervaloEjecucion fusion = ultimo.fusionarSiContiguo(
                    new IntervaloEjecucion(t, t));
            if (fusion != null) {
                lista.set(lista.size() - 1, fusion);
                return;
            }
        }
        lista.add(new IntervaloEjecucion(t, t));
    }

    /**
     * SRT y Guaranteed: recorre los nucleos ocupados y pregunta al
     * planificador si el proceso en CPU debe ser expulsado por otro de la
     * cola READY.
     */
    private void procesarExpulsiones() {
        // Nucleos libres: el balanceador les dara un proceso en este mismo
        // paso, asi que un candidato que pueda ir a uno de ellos no necesita
        // sacar a nadie. Ademas cada candidato provoca a lo sumo una
        // expulsion (sin esto dos nucleos podian expulsar por el mismo).
        java.util.List<Integer> libres = new ArrayList<>();
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (!nucleo.estaOcupado()) libres.add(nucleo.getIdNucleo());
        }
        java.util.Set<Integer> reclamados = new java.util.HashSet<>();

        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (!nucleo.estaOcupado()) continue;

            BCP enCPU = nucleo.getBcpActual();
            if (enCPU == null) continue;
            if (enCPU.getEstado() != EstadoProceso.RUNNING) continue;
            if (enCPU.getTiempoRestante() <= 0) continue;

            // Solo compiten los procesos que este nucleo puede ejecutar.
            ListaProcesos elegibles = logica.cpu.Balanceador.filtrar(
                    listaProcesos, nucleo.getIdNucleo(), this::puedeEjecutarEn);
            BCP candidato = planificador.debeExpulsar(enCPU, elegibles);
            if (candidato == null) continue;
            if (!reclamados.add(candidato.getId())) continue;   // ya tiene lugar

            Integer libre = null;
            for (Integer n : libres) {
                if (puedeEjecutarEn(candidato, n)) { libre = n; break; }
            }
            if (libre != null) {
                libres.remove(libre);   // ira a ese nucleo libre
                continue;
            }

            String motivo = planificador.getEstrategia().motivoExpulsion(enCPU, candidato);
            if (motivo == null) {
                motivo = planificador.getNombreEstrategia() + " expulsa a P" + enCPU.getId()
                    + " (le quedan " + enCPU.getTiempoRestante() + " s) porque P" + candidato.getId()
                    + " tiene menos (" + candidato.getTiempoRestante() + " s)";
            }
            anunciarContexto("CPU " + nucleo.getIdNucleo() + ": " + motivo);

            // En MLFQ el expulsado conserva su cola y lo que le queda de
            // quantum: asi se reproduce la fila "FB q = 2^i" de la Tabla 9.5.
            nucleo.expulsarProceso();
            listaProcesos.agregar(enCPU);
        }
    }

    /**
     * RR, Selfish RR, Lottery y MLFQ: recorre los nucleos ocupados y, si
     * el proceso en CPU agoto su quantum, lo expulsa y lo reencola al
     * final. En MLFQ el quantum es dinamico segun el nivel del proceso y,
     * al expulsarlo, se le baja de nivel (Q1->Q2->Q3).
     */
    private void procesarRotacionPorQuantum() {
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (!nucleo.estaOcupado()) continue;

            BCP enCPU = nucleo.getBcpActual();
            if (enCPU == null) continue;
            if (enCPU.getEstado() != EstadoProceso.RUNNING) continue;

            int idProceso = enCPU.getId();
            int consumido = quantumConsumidoPorProceso.getOrDefault(idProceso, 0);

            // Quantum dinamico en MLFQ; fijo en RR/SelfishRR/Lottery
            int quantumActual = quantumRR;
            if (esMLFQ()) {
                quantumActual = ((PlanificadorMLFQ) planificador.getEstrategia())
                                    .getQuantumActual(enCPU);
            }

            if (consumido < quantumActual) continue;

            if (esMLFQ()) {
                PlanificadorMLFQ mlfq = (PlanificadorMLFQ) planificador.getEstrategia();
                int nivelAntes = mlfq.getNivel(enCPU);
                mlfq.bajarNivel(enCPU);
                int nivelDespues = mlfq.getNivel(enCPU);
                anunciarContexto("CPU " + nucleo.getIdNucleo() + ": MLFQ expulsa a P" + idProceso
                    + " (quantum " + consumido + "/" + quantumActual
                    + " agotado); baja de " + PlanificadorMLFQ.nombreNivel(nivelAntes)
                    + " a " + PlanificadorMLFQ.nombreNivel(nivelDespues));
            } else {
                anunciarContexto("CPU " + nucleo.getIdNucleo() + ": "
                    + planificador.getNombreEstrategia() + " expulsa a P" + idProceso
                    + " (quantum " + consumido + "/" + quantumActual + " agotado)");
            }

            nucleo.expulsarProceso();
            listaProcesos.agregar(enCPU);
            quantumConsumidoPorProceso.put(idProceso, 0);
        }
    }

    private int calcularTardanzaDisco(BCP bcp) {
        int ah = bcp.getAh();
        switch (ah) {
            case 0x3C: return TARDANZA_CREAR;
            case 0x3D: return TARDANZA_ABRIR;
            case 0x4D: return TARDANZA_LEER;
            case 0x40: return TARDANZA_ESCRIBIR;
            case 0x41: return TARDANZA_ELIMINAR;
            default:   return TARDANZA_DEFAULT;
        }
    }

    /**
     * Ejecuta la simulacion completa paso a paso hasta que no queden
     * procesos activos. Si detecta un posible bucle infinito, una
     * excepcion o una cancelacion, guarda el motivo en errorEjecucion.
     *
     * @param onPaso callback opcional por cada paso
     * @return la cantidad de pasos ejecutados
     */
    public int ejecutarAutomatico(Runnable onPaso) {
        int pasos = 0;
        int maxPasos = 10000;

        try {
            while (hayProcesosActivos() && pasos < maxPasos) {
                ejecutarUnPaso();
                pasos++;

                if (onPaso != null) onPaso.run();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorEjecucion = "Ejecucion interrumpida por el usuario "
                            + "despues de " + pasos + " pasos.";
                    break;
                }
            }
            // Si se alcanzo el limite de pasos, quedo trabajo pendiente.
            if (pasos >= maxPasos && hayProcesosActivos()) {
                errorEjecucion = "Se alcanzo el limite de " + maxPasos
                        + " pasos sin terminar. Posible bucle infinito o "
                        + "configuracion que no progresa.";
            }
        } catch (RuntimeException ex) {
            errorEjecucion = "Error durante la ejecucion en el paso " + (pasos + 1)
                    + ": " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage();
            System.err.println("[EJECUCION] " + errorEjecucion);
        }
        return pasos;
    }

    /* ==================== TERMINACIÓN ==================== */

    public void procesoTerminado(BCP bcp) {
        bcp.setEstado(EstadoProceso.EXIT);
        bcp.marcarFin(relojGlobal);
        bcp.setCpuAsignado(-1);

        int tiempoLlegada = bcp.getTiempoLlegada();

        // Tr = fin - llegada. relojGlobal ya es el instante de fin: el
        // proceso termino al cerrar el intervalo de relojGlobal-1 a relojGlobal.
        int turnaround = relojGlobal - tiempoLlegada;
        if (turnaround < 0) turnaround = 0;

        double trTs = (bcp.getRafaga() > 0) ? (double) turnaround / bcp.getRafaga() : 0.0;

        int idProceso = bcp.getId();
        List<IntervaloEjecucion> intervalos =
                intervalosPorProceso.getOrDefault(idProceso, new ArrayList<>());
        int cpu = cpuPorProceso.getOrDefault(idProceso, -1);

        BCPTerminado snapshot = new BCPTerminado(
            bcp.getId(), bcp.getEstado(), bcp.getTiempoInicio(), bcp.getTiempoFin(),
            bcp.getBase(), bcp.getAlcance(), bcp.getPrioridad(),
            tiempoLlegada, turnaround, trTs,
            cpu, intervalos
        );
        this.procesosTerminados.add(snapshot);

        quantumConsumidoPorProceso.put(idProceso, 0);

        // Limpiar el estado interno del planificador (MLFQ guarda nivel por proceso)
        if (esMLFQ()) {
            ((PlanificadorMLFQ) planificador.getEstrategia()).olvidar(bcp);
        }

        // La estrategia libera lo que el proceso ocupaba (bloque, particion o
        // marcos) y deja vacias sus celdas.
        int fallos = particionador.getFallosDePagina(idProceso);
        particionador.liberar(idProceso);
        memoria.liberarBloqueBCP(bcp.getDireccionBase());
        anunciarContexto("Memoria (" + particionador.getNombre() + "): se libera lo que ocupaba P"
                + idProceso + (fallos > 0 ? " (tuvo " + fallos + " fallos de pagina)" : ""));

        reactivarSiguienteProceso();
    }

    /**
     * Al liberarse una particion, admite a los procesos NEW que YA habian
     * llegado (TL < relojGlobal) pero no tenian espacio. Los que aun no
     * llegan entran por el paso 2 de ejecutarUnPaso. FCFS por llegada.
     */
    private void reactivarSiguienteProceso() {
        for (TrabajoNuevo t : trabajosPorLlegada()) {
            if (t.getTiempoLlegada() >= relojGlobal) continue;
            if (admitirTrabajo(t) == null) break;   // sin espacio: esperar
        }
    }

    /**
     * Deja el BCP en READY y lo agrega a la cola de listos. Las
     * instrucciones ya las escribio la estrategia de memoria en cargar().
     * La base es fisica en las estrategias contiguas y 0 (logica) en
     * paginacion.
     */
    private void admitirEnParticion(BCP bcp, List<Instruccion> instrucciones, int base) {
        bcp.setBase(base);
        bcp.setAlcance(instrucciones.size());
        bcp.setPc(base);
        bcp.setEstado(EstadoProceso.READY);
        bcp.setRafaga(instrucciones.size());
        bcp.setTiempoRestante(instrucciones.size());

        listaProcesos.agregar(bcp);
    }

    public boolean desbloquearProceso(int valor) {
        if (procesosBloqueadosInput.isEmpty()) return false;
        BCP bcp = procesosBloqueadosInput.remove(0);
        bcp.setDxAsInt(valor);
        bcp.setEstado(EstadoProceso.READY);
        listaProcesos.agregarAlPrincipio(bcp);
        return true;
    }

    /* ================== CONSULTAS PARA LA GUI ================== */

    public void reiniciarBalanceador() {
        gestorCPUs.reiniciarBalanceador();
    }

    public int getTotalProcesosActivos() {
        int enCPU = 0;
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) enCPU++;
        }
        return listaProcesos.getCantidad() + procesosBloqueadosInput.size() + enCPU;
    }

    public boolean hayProcesosActivos() {
        // Los programas pendientes mantienen activo el reloj simulado.
        // Todavia no tienen BCP ni ocupan RAM o swap.
        if (!pendientesPorLlegar.isEmpty()) {
            return true;
        }

        // Un proceso en NEW cuyo TL aun no pasa es trabajo pendiente.
        // (Uno que ya llego pero no cabe en RAM no cuenta por si solo:
        // se reactiva al terminar otro proceso; si no hay ninguno, no
        // progresaria nunca y la simulacion no debe quedar en bucle.)
        for (TrabajoNuevo t : trabajosNuevos) {
            if (t.getTiempoLlegada() >= relojGlobal) return true;
        }
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) {
                BCP b = n.getBcpActual();
                if (b != null) {
                    if (b.getEstado() == EstadoProceso.BLOCKED && b.getPesoPendiente() > 0) {
                        return true;
                    }
                    if (b.getEstado() == EstadoProceso.RUNNING) {
                        return true;
                    }
                }
            }
        }
        return !listaProcesos.estaVacia() || !procesosBloqueadosInput.isEmpty();
    }

    public BCP getProcesoActual() {
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) return n.getBcpActual();
        }
        if (!listaProcesos.estaVacia()) return listaProcesos.verPrimero();
        if (!procesosBloqueadosInput.isEmpty()) return procesosBloqueadosInput.get(0);
        return null;
    }

    public GestorCPUs getGestorCPUs() { return gestorCPUs; }
    public Planificador getPlanificador() { return planificador; }

    public ListaProcesos getListaProcesos() { return listaProcesos; }
    public ListaDeTrabajos getListaDeTrabajos() { return listaDeTrabajos; }
    public List<BCPTerminado> getProcesosTerminados() { return procesosTerminados; }
    public List<BCP> getProcesosBloqueadosInput() { return new ArrayList<>(procesosBloqueadosInput); }

    /** Procesos en estado NEW (sin BCP), en el orden en que se registraron. */
    public List<TrabajoNuevo> getTrabajosNuevos() { return new ArrayList<>(trabajosNuevos); }

    /** Copia de los programas pendientes de llegada, sin BCP, RAM ni swap. */
    public List<ProgramaPendiente> getProgramasPendientes() {
        return new ArrayList<>(pendientesPorLlegar);
    }

    /** Nombre del .asm de un proceso, o null si no se conoce. */
    public String getNombreArchivo(int id) { return nombresPorId.get(id); }

    /* ================== NUCLEO DE CADA PROCESO ================== */

    /**
     * @return el nucleo asignado al proceso, o NUCLEO_CUALQUIERA si puede ir
     *         a cualquier nucleo libre.
     */
    public int getNucleoAsignado(int idProceso) {
        return nucleoPorProceso.getOrDefault(idProceso, NUCLEO_CUALQUIERA);
    }

    /** @return si el proceso puede ejecutarse en el nucleo. */
    public boolean puedeEjecutarEn(BCP bcp, int idNucleo) {
        int asignado = getNucleoAsignado(bcp.getId());
        return asignado == NUCLEO_CUALQUIERA || asignado == idNucleo;
    }

    /**
     * Asigna el proceso a un nucleo (o a cualquiera con NUCLEO_CUALQUIERA).
     * Cada nucleo admite hasta PROCESOS_POR_NUCLEO procesos asignados que no
     * hayan terminado (grupos de 5 por CPU). Si el proceso esta ahora en
     * otro nucleo, termina su turno alli y despues solo vuelve al asignado.
     *
     * @return null si se asigno, o el motivo por el que no se pudo
     */
    public String asignarNucleo(int idProceso, int idNucleo) {
        if (idNucleo == NUCLEO_CUALQUIERA) {
            nucleoPorProceso.remove(idProceso);
            return null;
        }
        if (idNucleo < 0 || idNucleo >= gestorCPUs.getCantidadNucleos()) {
            return "P" + idProceso + ": la CPU " + idNucleo + " no existe (hay "
                    + gestorCPUs.getCantidadNucleos() + ").";
        }
        if (getNucleoAsignado(idProceso) == idNucleo) return null;

        int asignados = 0;
        for (Map.Entry<Integer, Integer> e : nucleoPorProceso.entrySet()) {
            if (e.getValue() == idNucleo && !haTerminado(e.getKey())) asignados++;
        }
        if (asignados >= PROCESOS_POR_NUCLEO) {
            return "P" + idProceso + ": la CPU " + idNucleo + " ya tiene "
                    + PROCESOS_POR_NUCLEO + " procesos asignados.";
        }
        nucleoPorProceso.put(idProceso, idNucleo);
        anunciarContexto("P" + idProceso + " queda asignado a la CPU " + idNucleo);
        return null;
    }

    /* ================== PARAMETROS DE MLFQ Y LOTTERY ================== */

    // Boletos de Lottery que el usuario dio a cada proceso (id -> boletos).
    // Si no esta, el proceso tiene tantos boletos como su rafaga.
    private final Map<Integer, Integer> boletosPorProceso = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Aplica los parametros de MLFQ y Lottery. Si el algoritmo activo es uno
     * de ellos, se vuelve a crear con estos parametros (se llama al armar el
     * sistema, antes de cargar procesos).
     *
     * @param quantumsMLFQ   quantum de cada cola de MLFQ (Q1 primero)
     * @param semillaLottery semilla de los sorteos; 0 = distinta en cada ejecucion
     */
    public void configurarParametrosCPU(int[] quantumsMLFQ, long semillaLottery) {
        if (esMLFQ() && quantumsMLFQ != null && quantumsMLFQ.length > 0) {
            planificador.setEstrategia(new PlanificadorMLFQ(quantumsMLFQ));
        }
        if (esLottery()) {
            PlanificadorLottery lottery = (semillaLottery != 0)
                    ? new PlanificadorLottery(semillaLottery) : new PlanificadorLottery();
            lottery.setBoletos(bcp -> getBoletos(bcp.getId(), bcp.getRafaga()));
            planificador.setEstrategia(lottery);
        }
    }

    /** @return los boletos del proceso (por defecto, su rafaga). */
    public int getBoletos(int idProceso, int rafaga) {
        return boletosPorProceso.getOrDefault(idProceso, Math.max(1, rafaga));
    }

    /** Fija los boletos de Lottery del proceso (al menos 1). */
    public void setBoletos(int idProceso, int boletos) {
        boletosPorProceso.put(idProceso, Math.max(1, boletos));
    }

    private boolean haTerminado(int idProceso) {
        for (BCPTerminado t : procesosTerminados) {
            if (t.getId() == idProceso) return true;
        }
        return false;
    }

    public Disco getDisco() { return disco; }
    public EstrategiaMemoria getEstrategiaMemoria() { return particionador; }
    public int getQuantumRR() { return quantumRR; }

    /**
     * Conecta la bitacora de cambios de contexto de la GUI. Los mensajes
     * pueden llegar desde el hilo de la ejecucion automatica: quien la
     * conecta debe pasarlos al hilo de Swing.
     */
    public void setBitacoraContexto(java.util.function.Consumer<String> callback) {
        this.bitacoraContexto = callback;
    }

    /**
     * @return el registro del diagrama de Gantt en vivo.
     */
    public RegistroGantt getRegistroGantt() {
        return registroGantt;
    }

    // Anota un cambio de contexto con el instante del reloj (la columna del
    // Gantt en que ocurre) y lo envia a la GUI y a la consola.
    private void anunciarContexto(String mensaje) {
        String linea = "[t=" + relojGlobal + "] " + mensaje;
        System.out.println(linea);
        if (bitacoraContexto != null) bitacoraContexto.accept(linea);
    }

    public void setSalidaPantalla(java.util.function.Consumer<String> callback) {
        this.salidaPantalla = callback;
        for (Nucleo n : gestorCPUs.getNucleos()) {
            n.getDespachador().setSalidaPantalla(callback);
        }
    }

    public void setSolicitudArchivo(java.util.function.Consumer<BCP> callback) {
        for (Nucleo n : gestorCPUs.getNucleos()) {
            n.getDespachador().setSolicitudArchivo(callback);
        }
    }

    /**
     * Aplica los tiempos de llegada elegidos en la GUI.
     *
     *  - Proceso NEW y TL ya alcanzado: se crea su BCP y pasa a READY.
     *  - Proceso NEW y TL futuro: solo se actualiza el TL.
     *  - Proceso READY (con BCP) y TL futuro: vuelve a NEW (se destruye su
     *    BCP y su programa pasa al swap). Solo si aun no ejecuto y hay
     *    espacio en swap; si no, conserva su TL anterior.
     *  - Proceso READY y TL no futuro: solo se actualiza el TL.
     *
     * La ventana manda una fila por proceso (tambien los que no se
     * tocaron), asi que un TL igual al actual no debe provocar cambios.
     *
     * ACTUALIZACION:
     *  - TL futuro: el programa pasa a pendientes, sin BCP, RAM ni swap.
     *  - TL actual o pasado: el programa pendiente puede ser admitido.
     */
    public void actualizarTiemposLlegada(Object[][] tiempos) {
        if (tiempos == null) return;

        for (Object[] t : tiempos) {
            int id = (Integer) t[0];
            int tiempo = (Integer) t[1];

            // Caso nuevo: el programa aun esta pendiente de llegada.
            ProgramaPendiente pendiente = buscarProgramaPendiente(id);
            if (pendiente != null) {
                pendiente.setTiempoLlegada(tiempo);

                if (tiempo <= relojGlobal) {
                    // Al editar el TL, admitir inmediatamente si es posible.
                    List<BCP> admitidos = admitirPendientes(true);
                    for (BCP admitido : admitidos) {
                        listaProcesos.agregar(admitido);
                    }
                }
                continue;
            }

            // Caso 1: el proceso esta en NEW (no tiene BCP)
            TrabajoNuevo nuevo = buscarTrabajoNuevo(id);
            if (nuevo != null) {
                int tlAnterior = nuevo.getTiempoLlegada();
                nuevo.setTiempoLlegada(tiempo);

                if (tiempo > relojGlobal) {
                    if (enviarNuevoAPendiente(nuevo)) {
                        System.out.println("[RELOJ] Proceso " + id
                            + " pasa a pendiente (llega en t=" + tiempo + ")");
                    } else {
                        nuevo.setTiempoLlegada(tlAnterior);
                    }
                } else {
                    BCP bcp = admitirTrabajo(nuevo);
                    if (bcp != null) {
                        System.out.println("[RELOJ] Proceso " + id
                            + " movido a READY (TL=" + tiempo + " <= reloj=" + relojGlobal + ")");
                    } else {
                        System.out.println("[RELOJ] Proceso " + id
                            + " sin particion libre; sigue en NEW.");
                    }
                }
                continue;
            }

            // Caso 2: el proceso tiene BCP
            BCP bcp = buscarProcesoPorId(id);
            if (bcp == null) continue;

            int tlAnterior = bcp.getTiempoLlegada();
            if (tiempo == tlAnterior) continue;   // sin cambios

            if (tiempo > relojGlobal) {
                if (!puedeVolverANuevo(bcp)) {
                    System.out.println("[RELOJ] Proceso " + id
                        + " ya empezo a ejecutar: no puede volver a NEW.");
                    continue;
                }
                bcp.setTiempoLlegada(tiempo);

                if (enviarAPendiente(bcp)) {
                    System.out.println("[RELOJ] Proceso " + id
                        + " pasa a pendiente (llega en t=" + tiempo + ")");
                } else {
                    // Sin instrucciones disponibles: se queda en RAM como estaba
                    bcp.setTiempoLlegada(tlAnterior);
                }
            } else {
                bcp.setTiempoLlegada(tiempo);
            }
        }
    }

    /**
     * Actualiza la prioridad de los procesos segun la matriz recibida
     * de la GUI. Funciona con procesos que tienen BCP y con los que
     * estan en NEW (la prioridad se guarda en el TrabajoNuevo).
     *
     * @param prioridades matriz de {id, prioridad}
     */
    public void actualizarPrioridades(Object[][] prioridades) {
        if (prioridades == null) return;

        for (Object[] fila : prioridades) {
            int id = (Integer) fila[0];
            int prioridad = (Integer) fila[1];

            // Los programas pendientes tambien conservan su prioridad.
            ProgramaPendiente pendiente = buscarProgramaPendiente(id);
            if (pendiente != null) {
                pendiente.setPrioridad(prioridad);
                continue;
            }

            TrabajoNuevo nuevo = buscarTrabajoNuevo(id);
            if (nuevo != null) {
                nuevo.setPrioridad(prioridad);
                continue;
            }

            BCP bcpEncontrado = buscarProcesoPorId(id);
            if (bcpEncontrado != null) {
                bcpEncontrado.setPrioridad(prioridad);
            }
        }
    }

    /**
     * Busca un proceso CON BCP por ID (los de NEW no tienen BCP: ver
     * buscarTrabajoNuevo).
     */
    private BCP buscarProcesoPorId(int id) {
        // 1. Nucleos
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) {
                BCP bcp = n.getBcpActual();
                if (bcp != null && bcp.getId() == id) return bcp;
            }
        }

        // 2. Lista de listos
        for (BCP bcp : listaProcesos.toList()) {
            if (bcp.getId() == id) return bcp;
        }

        // 3. Bloqueados de input
        for (BCP bcp : procesosBloqueadosInput) {
            if (bcp.getId() == id) return bcp;
        }

        return null;
    }
}
