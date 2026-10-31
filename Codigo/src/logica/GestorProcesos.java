package logica;

import logica.cpu.GestorCPUs;
import logica.cpu.Nucleo;
import logica.estadisticas.IntervaloEjecucion;
import logica.planificacion.PlanificadorFCFS;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 *
 * PROYECTO 2:
 *  - Todo el tiempo usa el RELOJ GLOBAL SIMULADO (int), no LocalDateTime.
 *  - procesoTerminado() marca el fin con el relojGlobal y calcula el
 *    turnaround como (relojGlobal - tiempoLlegada + 1), segun la
 *    convencion del curso.
 *  - El balanceo recibe el relojGlobal para propagarlo al Despachador.
 *  - SRT: en cada paso, si el planificador es apropiativo, revisa si
 *    algun proceso en READY debe expulsar al de la CPU.
 *  - DIAGRAMA DE GANTT: registra que proceso ejecuto en cada instante
 *    y en que CPU, para dibujar el diagrama al final.
 */
public class GestorProcesos {

    public static final int MAX_PROCESOS = 5;

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

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosInput;

    // === NUEVO: Reloj global y lista de pendientes ===
    private int relojGlobal = 0;
    private List<BCP> procesosPendientesLlegada = new ArrayList<>();

    // === NUEVO (Diagrama Gantt): ejecuciones por proceso y CPU ===
    /** Intervalos de ejecucion acumulados por id de proceso. */
    private Map<Integer, List<IntervaloEjecucion>> intervalosPorProceso = new HashMap<>();
    /** CPU en la que corrio cada proceso (el ultimo nucleo donde estuvo). */
    private Map<Integer, Integer> cpuPorProceso = new HashMap<>();

    private java.util.function.Consumer<String> salidaPantalla;

    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          int cantidadCPUs,
                          String algoritmoCPU,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          ParticionadorDinamico particionador,
                          Disco disco) {
        this.memoria = memoria;
        this.listaProcesos = listaProcesos;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;

        this.planificador = new Planificador(listaProcesos);
        // Seleccion del algoritmo segun la configuracion
        if ("SPN".equals(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorSPN());
        } else if ("SRT".equals(algoritmoCPU)) {
            this.planificador.setEstrategia(new PlanificadorSRT());
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

        int base = particionador.asignarParticion(tamanoNecesario, id);
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base, id);
            if (bcp != null) {
                copiarAsmAArchivos(nombreArchivo, instrucciones);
                siguienteId++;

                // admitirEnParticion() YA encolo el BCP en listaProcesos.
                // Aqui solo lo movemos a pendientes si aun no ha llegado
                // (TL > relojGlobal). Si ya llego, se queda en la lista.
                if (bcp.getTiempoLlegada() > relojGlobal) {
                    listaProcesos.sacar(bcp);
                    procesosPendientesLlegada.add(bcp);
                }

                return ResultadoCarga.exito(bcp);
            }
            particionador.liberarParticion(base, tamanoNecesario, id);
        }

        ResultadoCarga resultadoSwap = suspenderEnSwap(instrucciones, id, nombreArchivo);
        if (resultadoSwap.getEstado() == ResultadoCarga.Estado.EN_ESPERA) {
            siguienteId++;
            return resultadoSwap;
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

    private ResultadoCarga suspenderEnSwap(List<Instruccion> instrucciones, int id, String nombreArchivo) {
        int tamanoInstrucciones = instrucciones.size();

        if (disco.getEspacioSwapLibre() < tamanoInstrucciones) {
            return ResultadoCarga.error("La memoria virtual del disco no tiene espacio.");
        }

        int dirSwap = disco.reservarBloqueSwap(tamanoInstrucciones);
        if (dirSwap == -1) {
            return ResultadoCarga.error("No hay bloque contiguo libre en el swap.");
        }

        disco.escribirBloqueSwap(dirSwap, instrucciones.toArray());

        int finSwap = dirSwap + tamanoInstrucciones - 1;
        boolean registrado = disco.registrarArchivoAsm(nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!registrado) {
            disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
            return ResultadoCarga.error("No se pudo registrar en el indice ASM.");
        }

        boolean agregado = listaDeTrabajos.agregar(nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!agregado) {
            disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
            disco.eliminarDelIndice(nombreArchivo, Disco.TIPO_ASM);
            return ResultadoCarga.error("La ListaDeTrabajos esta llena.");
        }

        return ResultadoCarga.enEspera();
    }

    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int base, int id) {
        int direccionBase = memoria.reservarBloqueBCP();
        if (direccionBase == -1) return null;

        BCP bcp = new BCP(memoria, direccionBase, id, 1);
        admitirEnParticion(bcp, instrucciones, base);
        return bcp;
    }

    /* ==================== EJECUCIÓN PASO A PASO ==================== */

    public boolean ejecutarUnPaso() {
        // 1. Avanzar el reloj global
        relojGlobal++;

        // 2. Revisar si hay procesos pendientes que ya llegaron a su tiempo
        List<BCP> llegadosAhora = new ArrayList<>();
        for (BCP bcp : procesosPendientesLlegada) {
            if (bcp.getTiempoLlegada() <= relojGlobal) {
                listaProcesos.agregar(bcp);
                llegadosAhora.add(bcp);
                System.out.println("[RELOJ " + relojGlobal + "] Proceso " + bcp.getId() + " ha llegado.");
            }
        }
        procesosPendientesLlegada.removeAll(llegadosAhora);

        // 3. EXPULSION APROPIATIVA (SRT / RR):
        //    Si el planificador es apropiativo, revisar si algun proceso
        //    en READY debe reemplazar al que esta en CPU.
        //    Se hace ANTES del balanceo para que las CPUs expulsadas
        //    queden libres y el balanceador pueda asignar los nuevos.
        if (planificador.esApropiativo()) {
            procesarExpulsiones();
        }

        // 4. Delegar la asignación al Gestor de CPUs (que usa el Balanceador)
        gestorCPUs.balancear(listaProcesos, planificador, relojGlobal);

        // 5. Capturar los RUNNING ANTES de ejecutar el paso.
        //    Asi registramos el ultimo instante de los procesos que
        //    terminan en este paso (antes de que pasen a EXIT).
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

        // 7. Registrar las ejecuciones capturadas en el paso 5 (Diagrama Gantt)
        for (Map.Entry<Integer, Integer> e : ejecucionesEnPaso.entrySet()) {
            registrarEjecucion(e.getKey(), e.getValue(), relojGlobal);
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
     * Registra que un proceso ejecuto en un instante y en una CPU.
     *
     * Acumula intervalos contiguos para eficiencia:
     *   [1..3] + [4..7]  -> [1..7]
     *
     * PROYECTO 2 - SOLID:
     *  - SRP: el GestorProcesos registra la ejecucion (concepto del
     *    sistema), no el BCP ni el Nucleo.
     *  - OCP: si se necesita registrar mas datos, se agrega al Map
     *    correspondiente sin tocar el flujo.
     *
     * @param idProceso id del proceso que ejecuto
     * @param idCpu     id del nucleo donde corrio
     * @param t         instante simulado del reloj global
     */
    private void registrarEjecucion(int idProceso, int idCpu, int t) {
        // Registrar la CPU (la ultima gana)
        cpuPorProceso.put(idProceso, idCpu);

        // Acumular el intervalo [t..t] al proceso
        List<IntervaloEjecucion> lista =
                intervalosPorProceso.computeIfAbsent(idProceso, k -> new ArrayList<>());

        // Intentar fusionar con el ultimo intervalo si es contiguo
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
     * Recorre los nucleos ocupados y, si el planificador es apropiativo,
     * pregunta si el proceso en CPU debe ser expulsado por otro de la
     * cola READY.
     *
     * Si la respuesta no es null:
     *   1. Expulsa al proceso actual del nucleo (vuelve a READY).
     *   2. Lo re-encola en la ListaProcesos.
     *
     * El nuevo proceso sera despachado en el siguiente balanceo.
     *
     * PROYECTO 2 (SRT / RR): este metodo es la clave de la apropiacion.
     * El Balanceador sigue siendo estricto, pero al liberar el nucleo
     * antes del balanceo, el nuevo proceso puede entrar.
     */
    private void procesarExpulsiones() {
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (!nucleo.estaOcupado()) continue;

            BCP enCPU = nucleo.getBcpActual();
            if (enCPU == null) continue;
            if (enCPU.getEstado() != EstadoProceso.RUNNING) continue;

            // Solo expulsar procesos que aun tienen trabajo pendiente.
            // Si tiempoRestante == 0, en realidad ya termino (raro, pero
            // lo dejamos salir por la via normal de terminacion).
            if (enCPU.getTiempoRestante() <= 0) continue;

            BCP candidato = planificador.debeExpulsar(enCPU);
            if (candidato == null) continue;

            System.out.println("[SRT] Expulsando ID " + enCPU.getId()
                + " (rest=" + enCPU.getTiempoRestante() + ")"
                + " por ID " + candidato.getId()
                + " (rest=" + candidato.getTiempoRestante() + ")");

            // 1. Expulsar al proceso actual (guarda contexto, READY)
            nucleo.expulsarProceso();

            // 2. Re-encolar el BCP expulsado (vuelve a competir)
            listaProcesos.agregar(enCPU);
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

    /**
     * Marca el proceso como terminado, calcula estadisticas usando el
     * RELOJ GLOBAL SIMULADO y libera sus recursos.
     *
     * PROYECTO 2:
     *  - El fin se marca con relojGlobal (int), no con LocalDateTime.
     *  - turnaround = relojGlobal - tiempoLlegada + 1 (convencion del curso).
     *  - Tr/Ts = turnaround / rafaga.
     *  - Se pasan los intervalos de ejecucion y la CPU al snapshot
     *    (para el diagrama de Gantt).
     */
    public void procesoTerminado(BCP bcp) {
        bcp.setEstado(EstadoProceso.EXIT);
        bcp.marcarFin(relojGlobal);
        bcp.setCpuAsignado(-1);

        int tiempoLlegada = bcp.getTiempoLlegada();

        // Turnaround segun convencion del curso: TF - TL + 1
        int turnaround = relojGlobal - tiempoLlegada + 1;
        if (turnaround < 0) turnaround = 0;

        double trTs = (bcp.getRafaga() > 0) ? (double) turnaround / bcp.getRafaga() : 0.0;

        // Recuperar intervalos y CPU del proceso
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

        particionador.liberarParticion(bcp.getBase(), bcp.getAlcance(), bcp.getId());
        memoria.liberarBloqueUsuario(bcp.getBase(), bcp.getAlcance());
        memoria.liberarBloqueBCP(bcp.getDireccionBase());

        reactivarSiguienteProceso();
    }

    private void reactivarSiguienteProceso() {
        if (listaDeTrabajos.estaVacia()) return;

        Object[] trabajo = listaDeTrabajos.verPrimero();
        if (trabajo == null) return;

        String nombreArchivo = (String) trabajo[0];
        int inicioSwap = (Integer) trabajo[1];
        int finSwap = (Integer) trabajo[2];
        int tamanoInstrucciones = (finSwap - inicioSwap) + 1;
        int id = siguienteId;

        int base = particionador.asignarParticion(tamanoInstrucciones, id);
        if (base == -1) return;

        int direccionBase = memoria.reservarBloqueBCP();
        if (direccionBase == -1) {
            particionador.liberarParticion(base, tamanoInstrucciones, id);
            return;
        }

        Object[] instruccionesArr = disco.leerBloqueSwap(inicioSwap, tamanoInstrucciones);
        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) instrucciones.add((Instruccion) obj);
        }

        int nuevoInicioArchivos = disco.reservarBloqueArchivo(tamanoInstrucciones);
        if (nuevoInicioArchivos != -1) disco.escribirBloqueArchivo(nuevoInicioArchivos, instruccionesArr);

        if (nombreArchivo != null && disco.existe(nombreArchivo, Disco.TIPO_ASM)) {
            disco.eliminarDelIndice(nombreArchivo, Disco.TIPO_ASM);
        }

        disco.liberarBloqueSwap(inicioSwap, tamanoInstrucciones);
        listaDeTrabajos.sacarPrimero();

        BCP bcpRestaurado = new BCP(memoria, direccionBase, id, 1);
        admitirEnParticion(bcpRestaurado, instrucciones, base);
    }

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
        if (!procesosPendientesLlegada.isEmpty()) return true;
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
    public Disco getDisco() { return disco; }
    public ParticionadorDinamico getParticionador() { return particionador; }

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

    public void actualizarTiemposLlegada(Object[][] tiempos) {
        for (Object[] t : tiempos) {
            int id = (Integer) t[0];
            int tiempo = (Integer) t[1];

            BCP bcpEncontrado = null;

            // 1. Buscar en núcleos
            for (Nucleo n : gestorCPUs.getNucleos()) {
                if (n.estaOcupado()) {
                    BCP bcp = n.getBcpActual();
                    if (bcp != null && bcp.getId() == id) {
                        bcpEncontrado = bcp;
                        break;
                    }
                }
            }

            // 2. Buscar en la lista de listos
            if (bcpEncontrado == null) {
                for (BCP bcp : listaProcesos.toList()) {
                    if (bcp.getId() == id) {
                        bcpEncontrado = bcp;
                        break;
                    }
                }
            }

            // 3. Actualizar y mover a pendientes si es necesario
            if (bcpEncontrado != null) {
                bcpEncontrado.setTiempoLlegada(tiempo);

                // Si el tiempo es mayor al reloj, lo sacamos de la lista de listos
                // (a menos que ya esté en CPU, en cuyo caso se queda).
                if (tiempo > relojGlobal) {
                    if (listaProcesos.contiene(bcpEncontrado)) {
                        listaProcesos.sacar(bcpEncontrado);
                        procesosPendientesLlegada.add(bcpEncontrado);
                        System.out.println("[RELOJ] Proceso " + id + " movido a pendientes (llega en t=" + tiempo + ")");
                    }
                }
            }
        }
    }
}