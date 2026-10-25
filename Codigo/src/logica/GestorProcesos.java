package logica;

import logica.cpu.GestorCPUs;
import logica.cpu.Nucleo;
import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;
import modelo.Disco;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 * 
 * En el Proyecto 2, se añade soporte para múltiples CPUs a través del GestorCPUs.
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
    private GestorCPUs gestorCPUs; // <-- NUEVO: maneja los núcleos
    private ParticionadorDinamico particionador;
    private Disco disco;

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosInput;

    private java.util.function.Consumer<String> salidaPantalla;

    // Crea el gestor y sus colaboradores (planificador, gestorCPUs, etc.).
    public GestorProcesos(Memoria memoria,
                          CPU cpu, // Ya no se usa directo, se mantiene por compatibilidad
                          int cantidadCPUs,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          ParticionadorDinamico particionador,
                          Disco disco) {
        this.memoria = memoria;
        this.listaProcesos = listaProcesos;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;
        this.planificador = new Planificador(listaProcesos);
        
        // Inicializar gestor de CPUs
        this.gestorCPUs = new GestorCPUs(cantidadCPUs, memoria, memoria.getLimiteKernelUsuario(), disco);
        
        this.siguienteId = 1;
        this.procesosTerminados = new ArrayList<>();
        this.procesosBloqueadosInput = new ArrayList<>();
        this.disco = disco;
    }

    /* ==================== CREACIÓN DE PROCESOS ==================== */

    // Carga un .asm como proceso nuevo: intenta RAM, luego swap.
    public ResultadoCarga cargarPrograma(File archivo) {
        Ensamblador ensamblador = new Ensamblador();

        if (!ensamblador.esArchivoValido(archivo)) {
            return ResultadoCarga.error(ensamblador.getErroresComoTexto());
        }

        List<Instruccion> instrucciones = ensamblador.leerArchivo(archivo);
        int tamanoNecesario = instrucciones.size();
        int id = siguienteId;
        String nombreArchivo = archivo.getName();

        // 1. Intentar entrar a RAM
        int base = particionador.asignarParticion(tamanoNecesario, id);
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base, id);
            if (bcp != null) {
                copiarAsmAArchivos(nombreArchivo, instrucciones);
                siguienteId++;
                return ResultadoCarga.exito(bcp);
            }
            particionador.liberarParticion(base, tamanoNecesario, id);
        }

        // 2. Si no cabe, va a swap
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

    // Ejecuta un paso del ciclo para TODOS los núcleos
    public boolean ejecutarUnPaso() {
        // 1. Despachar procesos a núcleos libres
        Nucleo nucleoLibre = gestorCPUs.buscarNucleoLibre();
        while (nucleoLibre != null && !listaProcesos.estaVacia()) {
            BCP siguiente = planificador.seleccionarSiguiente();
            if (siguiente == null) break;

            System.out.println("[CPU " + nucleoLibre.getIdNucleo() + "] Despachando proceso ID " + siguiente.getId());
            int dir = listaProcesos.sacarPrimeraDireccion();
            nucleoLibre.asignarProceso(dir);

            nucleoLibre = gestorCPUs.buscarNucleoLibre();
        }

        // 2. Ejecutar un paso de CPU en los núcleos
        List<BCP> terminadosEnEstePaso = new ArrayList<>();
        gestorCPUs.ejecutarPasoEnTodos(terminadosEnEstePaso);

        // 3. Procesar los terminados
        for (BCP bcp : terminadosEnEstePaso) {
            System.out.println("[CPU] Proceso " + bcp.getId() + " TERMINÓ.");
            procesoTerminado(bcp);
        }

        // 4. Actualizar estados de BLOCKED y descontar tiempo
        for (Nucleo nucleo : gestorCPUs.getNucleos()) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null && bcp.getEstado() == EstadoProceso.BLOCKED) {
                    
                    // Si acaba de bloquearse, le asignamos su tardanza (si no la tiene ya)
                    if (bcp.getPesoPendiente() == 0) {
                        int tardanza = calcularTardanzaDisco(bcp);
                        bcp.setPesoPendiente(tardanza);
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId() 
                            + " BLOCKED por IO. Tardanza: " + tardanza + "s");
                    } 
                    
                    // Descontar el segundo
                    if (bcp.getPesoPendiente() > 0) {
                        bcp.setPesoPendiente(bcp.getPesoPendiente() - 1);
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId() 
                            + " esperando disco... (" + bcp.getPesoPendiente() + "s)");
                    }
                    
                    // Si ya cumplió la tardanza, pasa a RUNNING
                    if (bcp.getPesoPendiente() == 0) {
                        bcp.setEstado(EstadoProceso.RUNNING);
                        System.out.println("[CPU " + nucleo.getIdNucleo() + "] Proceso " + bcp.getId() + " desbloqueado.");
                    }
                }
            }
        }

        return hayProcesosActivos();
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

    private boolean fueBloqueoIO(BCP bcp) {
        int ir = bcp.getIr();
        if (ir < 0) return false;
        Instruccion instr = memoria.leerInstruccion(ir);
        if (instr == null) return false;
        if (!"INT".equals(instr.getOpcode())) return false;
        return instr.getCodigoInterrupcion(0) == 0x21;
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
        bcp.marcarFin();
        bcp.setCpuAsignado(-1);

        int tiempoLlegada = bcp.getTiempoLlegada();
        long duracionLong = bcp.getDuracionSegundos();
        int duracionSegundos = (duracionLong >= 0) ? (int) duracionLong : 0;
        int turnaround = duracionSegundos - tiempoLlegada;
        double trTs = (bcp.getRafaga() > 0) ? (double) turnaround / bcp.getRafaga() : 0.0;

        BCPTerminado snapshot = new BCPTerminado(
            bcp.getId(), bcp.getEstado(), bcp.getTiempoInicio(), bcp.getTiempoFin(),
            bcp.getBase(), bcp.getAlcance(), bcp.getPrioridad(),
            tiempoLlegada, turnaround, trTs
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

    public int getTotalProcesosActivos() {
        int enCPU = 0;
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) enCPU++;
        }
        return listaProcesos.getCantidad() + procesosBloqueadosInput.size() + enCPU;
    }

    public boolean hayProcesosActivos() {
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) {
                BCP b = n.getBcpActual();
                if (b != null) {
                    // Si está BLOCKED y le queda peso, el sistema sigue activo (esperando I/O)
                    if (b.getEstado() == EstadoProceso.BLOCKED && b.getPesoPendiente() > 0) {
                        return true; 
                    }
                    // Si está RUNNING, obviamente sigue activo
                    if (b.getEstado() == EstadoProceso.RUNNING) {
                        return true;
                    }
                }
            }
        }
        return !listaProcesos.estaVacia() || !procesosBloqueadosInput.isEmpty();
    }

    // Devuelve el BCP de la primera CPU ocupada (para compatibilidad con la GUI antigua)
    public BCP getProcesoActual() {
        for (Nucleo n : gestorCPUs.getNucleos()) {
            if (n.estaOcupado()) return n.getBcpActual();
        }
        if (!listaProcesos.estaVacia()) return listaProcesos.verPrimero();
        if (!procesosBloqueadosInput.isEmpty()) return procesosBloqueadosInput.get(0);
        return null;
    }

    public GestorCPUs getGestorCPUs() { return gestorCPUs; }
    
    public ListaProcesos getListaProcesos() { return listaProcesos; }
    public ListaDeTrabajos getListaDeTrabajos() { return listaDeTrabajos; }
    public List<BCPTerminado> getProcesosTerminados() { return procesosTerminados; }
    public List<BCP> getProcesosBloqueadosInput() { return new ArrayList<>(procesosBloqueadosInput); }
    public Disco getDisco() { return disco; }
    public ParticionadorDinamico getParticionador() { return particionador; }

    /* ================== CALLBACKS DE INTERRUPCIONES ================== */

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
}