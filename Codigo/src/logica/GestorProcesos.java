package logica;

import logica.cpu.GestorCPUs;
import logica.cpu.Nucleo;
import logica.estadisticas.IntervaloEjecucion;
import logica.planificacion.PlanificadorFCFS;
import logica.planificacion.PlanificadorHRRN;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *  - DIAGRAMA DE GANTT: registra que proceso ejecuto en cada instante.
 *
 * ESTADO NEW (tiempo de llegada futuro):
 *  - Un proceso cuyo TL aun no llega NO tiene BCP. Su programa esta en la
 *    zona Swap del disco y se representa con un TrabajoNuevo (id, nombre,
 *    TL, prioridad, direccion de swap).
 *  - Cuando relojGlobal supera su TL y hay particion libre, se crea el BCP
 *    (con el mismo id), se cargan las instrucciones en RAM y pasa a READY.
 *  - Un proceso es un TrabajoNuevo O un BCP, nunca los dos a la vez.
 *  - Los trabajos que no cupieron en RAM al cargar usan el mismo mecanismo
 *    (TL = 0): esperan en NEW hasta que haya particion libre.
 */
public class GestorProcesos {

    public static final int MAX_PROCESOS = 5;
    public static final int QUANTUM_DEFAULT = 1;
    public static final int SRR_A_DEFAULT = 2;
    public static final int SRR_B_DEFAULT = 1;

    private static final int PRIORIDAD_DEFAULT  = 1;

    private static final int TARDANZA_CREAR     = 1;
    private static final int TARDANZA_ABRIR     = 1;
    private static final int TARDANZA_LEER      = 2;
    private static final int TARDANZA_ESCRIBIR  = 2;
    private static final int TARDANZA_ELIMINAR  = 1;
    private static final int TARDANZA_DEFAULT   = 1;

    private Memoria memoria;
    private ListaProcesos listaProcesos;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private GestorCPUs gestorCPUs;
    private ParticionadorDinamico particionador;
    private Disco disco;

    // === NUEVO: Randomizador de tiempos ===
    private RandomizadorTiempos randomizador = new RandomizadorTiempos();

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosInput;

    // === Reloj global ===
    private int relojGlobal = 0;

    // === NUEVO: procesos en estado NEW (sin BCP, programa en Swap) ===
    private final List<TrabajoNuevo> trabajosNuevos = new ArrayList<>();

    // === NUEVO: nombre del .asm de cada proceso (solo para mostrar en la GUI) ===
    private final Map<Integer, String> nombresPorId = new HashMap<>();

    // === (Diagrama Gantt): ejecuciones por proceso y CPU ===
    private Map<Integer, List<IntervaloEjecucion>> intervalosPorProceso = new HashMap<>();
    private Map<Integer, Integer> cpuPorProceso = new HashMap<>();

    // === (RR): contador de quantum por proceso ===
    private Map<Integer, Integer> quantumConsumidoPorProceso = new HashMap<>();
    private int quantumRR = QUANTUM_DEFAULT;

    private java.util.function.Consumer<String> salidaPantalla;

    /**
     * Constructor legacy (sin quantum). Usa QUANTUM_DEFAULT.
     */
    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          int cantidadCPUs,
                          String algoritmoCPU,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          ParticionadorDinamico particionador,
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
                          ParticionadorDinamico particionador,
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
                          ParticionadorDinamico particionador,
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
        } else {
            this.planificador.setEstrategia(new PlanificadorFCFS());
        }

        this.gestorCPUs = new GestorCPUs(cantidadCPUs, memoria, memoria.getLimiteKernelUsuario(), disco);

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

        // TL aleatorio en [relojGlobal, relojGlobal + 6]
        int tiempoLlegada = randomizador.generarTiempoLlegada(relojGlobal);

        // CASO 1: llega en el futuro -> NEW. Va directo al swap: no se le
        // reserva particion ni BCP, porque todavia no ha entrado al sistema.
        if (tiempoLlegada > relojGlobal) {
            String error = guardarEnSwap(instrucciones, id, nombreArchivo,
                    tiempoLlegada, PRIORIDAD_DEFAULT, false);
            if (error != null) {
                return ResultadoCarga.error(
                    "El proceso llega en t=" + tiempoLlegada + " y debe esperar en "
                    + "memoria virtual (swap), pero no se pudo guardar:\n" + error);
            }
            nombresPorId.put(id, nombreArchivo);
            siguienteId++;
            anunciar(">> ID " + id + " (" + nombreArchivo + ") en NEW: llega en t="
                    + tiempoLlegada + ", esperando en swap.");
            return ResultadoCarga.enEspera();
        }

        // CASO 2: llega ya (TL = reloj) -> intentar cargarlo en RAM
        int base = particionador.asignarParticion(tamanoNecesario, id);
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base, id);
            if (bcp != null) {
                bcp.setTiempoLlegada(tiempoLlegada);
                copiarAsmAArchivos(nombreArchivo, instrucciones);
                nombresPorId.put(id, nombreArchivo);
                siguienteId++;
                return ResultadoCarga.exito(bcp);
            }
            particionador.liberarParticion(base, tamanoNecesario, id);
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
        int base = bcp.getBase();
        int tamano = bcp.getAlcance();
        String nombre = nombresPorId.getOrDefault(id, "proceso" + id + ".asm");

        List<Instruccion> instrucciones = new ArrayList<>();
        for (int i = 0; i < tamano; i++) {
            Object o = memoria.leer(base + i);
            if (o instanceof Instruccion) instrucciones.add((Instruccion) o);
        }

        String error = guardarEnSwap(instrucciones, id, nombre,
                bcp.getTiempoLlegada(), bcp.getPrioridad(), true);
        if (error != null) {
            System.out.println("[NEW] No se pudo mandar ID " + id + " a swap: " + error
                + " Sigue en RAM.");
            return false;
        }

        listaProcesos.sacar(bcp);
        particionador.liberarParticion(base, tamano, id);
        memoria.liberarBloqueUsuario(base, tamano);
        memoria.liberarBloqueBCP(bcp.getDireccionBase());

        System.out.println("[NEW] Proceso " + id + " vuelve a NEW (sin BCP, en swap).");
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

        int base = particionador.asignarParticion(tamano, t.getId());
        if (base == -1) return null;

        int direccionBCP = memoria.reservarBloqueBCP();
        if (direccionBCP == -1) {
            particionador.liberarParticion(base, tamano, t.getId());
            return null;
        }

        Object[] instruccionesArr = disco.leerBloqueSwap(t.getDirSwap(), tamano);
        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) instrucciones.add((Instruccion) obj);
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

    private TrabajoNuevo buscarTrabajoNuevo(int id) {
        for (TrabajoNuevo t : trabajosNuevos) {
            if (t.getId() == id) return t;
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

        // 3.5. RR y Selfish RR: rotacion por quantum CON orden especial de llegadas.
        //      En RR entran a la cola de turnos los que llegan; en Selfish RR,
        //      los que se aceptan en este segundo.
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
        }

        // 4. Delegar la asignación al Gestor de CPUs (que usa el Balanceador)
        gestorCPUs.balancear(listaProcesos, planificador, relojGlobal);

        // 5. Capturar los RUNNING ANTES de ejecutar el paso.
        Map<Integer, Integer> ejecucionesEnPaso = new HashMap<>();
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null && bcp.getEstado() == EstadoProceso.RUNNING) {
                    ejecucionesEnPaso.put(bcp.getId(), nucleo.getIdNucleo());
                }
            }
        }

        // 6. Ejecutar un paso de CPU en los núcleos
        List<BCP> terminadosEnEstePaso = new ArrayList<>();
        gestorCPUs.ejecutarPasoEnTodos(terminadosEnEstePaso);

        // 7. Registrar ejecuciones + incrementar contador de quantum (RR y Selfish RR)
        for (Map.Entry<Integer, Integer> e : ejecucionesEnPaso.entrySet()) {
            int idProceso = e.getKey();
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
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId()
                            + " BLOCKED por IO. Tardanza: " + tardanza + "s");
                    }

                    if (bcp.getPesoPendiente() > 0) {
                        bcp.setPesoPendiente(bcp.getPesoPendiente() - 1);
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId()
                            + " esperando disco... (" + bcp.getPesoPendiente() + "s)");
                    }

                    if (bcp.getPesoPendiente() == 0) {
                        bcp.setEstado(EstadoProceso.RUNNING);
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId() + " desbloqueado.");
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
     * @return true si la estrategia reparte la CPU por quantum (RR o Selfish RR).
     */
    private boolean usaQuantum() {
        return esRR() || esSelfishRR();
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
     * SRT: recorre los nucleos ocupados y pregunta al planificador si
     * el proceso en CPU debe ser expulsado por otro de la cola READY.
     */
    private void procesarExpulsiones() {
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (!nucleo.estaOcupado()) continue;

            BCP enCPU = nucleo.getBcpActual();
            if (enCPU == null) continue;
            if (enCPU.getEstado() != EstadoProceso.RUNNING) continue;
            if (enCPU.getTiempoRestante() <= 0) continue;

            BCP candidato = planificador.debeExpulsar(enCPU);
            if (candidato == null) continue;

            System.out.println("[SRT] Expulsando ID " + enCPU.getId()
                + " (rest=" + enCPU.getTiempoRestante() + ")"
                + " por ID " + candidato.getId()
                + " (rest=" + candidato.getTiempoRestante() + ")");

            nucleo.expulsarProceso();
            listaProcesos.agregar(enCPU);
        }
    }

    /**
     * RR: recorre los nucleos ocupados y, si el proceso en CPU agoto
     * su quantum, lo expulsa y lo reencola al final.
     */
    private void procesarRotacionPorQuantum() {
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (!nucleo.estaOcupado()) continue;

            BCP enCPU = nucleo.getBcpActual();
            if (enCPU == null) continue;
            if (enCPU.getEstado() != EstadoProceso.RUNNING) continue;

            int idProceso = enCPU.getId();
            int consumido = quantumConsumidoPorProceso.getOrDefault(idProceso, 0);

            if (consumido < quantumRR) continue;

            System.out.println("[RR] Expulsando ID " + idProceso
                + " (quantum " + consumido + "/" + quantumRR + " agotado)");

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

    public int ejecutarAutomatico(Runnable onPaso) {
        int pasos = 0;
        int maxPasos = 10000;

        while (hayProcesosActivos() && pasos < maxPasos) {
            ejecutarUnPaso();
            pasos++;

            if (onPaso != null) onPaso.run();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
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

        particionador.liberarParticion(bcp.getBase(), bcp.getAlcance(), bcp.getId());
        memoria.liberarBloqueUsuario(bcp.getBase(), bcp.getAlcance());
        memoria.liberarBloqueBCP(bcp.getDireccionBase());

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
     * Carga las instrucciones en la particion, deja el BCP en READY y lo
     * agrega a la cola de listos.
     */
    private void admitirEnParticion(BCP bcp, List<Instruccion> instrucciones, int base) {
        bcp.setBase(base);
        bcp.setAlcance(instrucciones.size());
        bcp.setPc(base);
        bcp.setEstado(EstadoProceso.READY);
        bcp.setRafaga(instrucciones.size());
        bcp.setTiempoRestante(instrucciones.size());

        int pos = base;
        for (Instruccion instr : instrucciones) {
            memoria.escribir(pos++, instr);
        }

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

    /** Nombre del .asm de un proceso, o null si no se conoce. */
    public String getNombreArchivo(int id) { return nombresPorId.get(id); }

    public Disco getDisco() { return disco; }
    public ParticionadorDinamico getParticionador() { return particionador; }
    public int getQuantumRR() { return quantumRR; }

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
     *  - Proceso READY (con BCP) y TL futuro: vuelve a NEW (se destruye su
     *    BCP y su programa pasa al swap). Solo si aun no ejecuto.
     *  - Proceso NEW y TL ya alcanzado: se crea su BCP y pasa a READY.
     *  - Proceso NEW y TL futuro: solo se actualiza el TL.
     */
    public void actualizarTiemposLlegada(Object[][] tiempos) {
        for (Object[] t : tiempos) {
            int id = (Integer) t[0];
            int tiempo = (Integer) t[1];

            // Caso 1: el proceso esta en NEW (no tiene BCP)
            TrabajoNuevo nuevo = buscarTrabajoNuevo(id);
            if (nuevo != null) {
                nuevo.setTiempoLlegada(tiempo);
                if (tiempo <= relojGlobal) {
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

            bcp.setTiempoLlegada(tiempo);

            if (tiempo > relojGlobal) {
                if (puedeVolverANuevo(bcp)) {
                    enviarANuevo(bcp);
                    System.out.println("[RELOJ] Proceso " + id
                        + " pasa a NEW (llega en t=" + tiempo + ")");
                } else {
                    System.out.println("[RELOJ] Proceso " + id
                        + " ya empezo a ejecutar: no puede volver a NEW.");
                }
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