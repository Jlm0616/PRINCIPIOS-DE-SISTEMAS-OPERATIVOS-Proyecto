package config;

import modelo.BCP;
import modelo.Memoria;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuracion externa del sistema.
 *
 * Parametros CONFIGURABLES:
 *   - memoria, kernel, disco, max_archivos, memoria_virtual, cpus, algoritmo_cpu.
 *
 * Parametros FIJOS:
 *   - max_procesos = 5 por CPU.
 */
public class ConfiguracionExterna {

    public static final String ARCHIVO = "config.txt";
    public static final double PORCENTAJE_MINIMO_KERNEL = 0.30;

    public static final int MEMORIA_DEFAULT = 1000;
    public static final int KERNEL_DEFAULT = (int) Math.ceil(1000 * PORCENTAJE_MINIMO_KERNEL);
    public static final int DISCO_DEFAULT = 512;
    public static final int MAX_ARCHIVOS_DEFAULT = 10;
    public static final int MEMORIA_VIRTUAL_DEFAULT = 64;
    public static final int CPUS_DEFAULT = 1;
    public static final String ALGORITMO_CPU_DEFAULT = "FCFS";

    public static final int MAX_PROCESOS = 5;

    // Valores mínimos absolutos (si el usuario pone menos, se corrige)
    public static final int MEMORIA_MINIMO = 1000;
    public static final int MEMORIA_MAXIMO = 65536;
    public static final int POSICIONES_POR_INSTRUCCION = 1;
    
    public static final int DISCO_MINIMO = 512;
    public static final int DISCO_MAXIMO = 65536;
    
    public static final int MAX_ARCHIVOS_MINIMO = 1;
    public static final int MAX_ARCHIVOS_MAXIMO = 100;
    
    public static final int MEMORIA_VIRTUAL_MINIMO = 64;
    public static final int MEMORIA_VIRTUAL_MAXIMO = 65536;
    public static final int ESPACIO_ARCHIVOS_MINIMO = 64;
    
    public static final int CPUS_MINIMO = 1;
    public static final int CPUS_MAXIMO = 5;

    private int memoria         = MEMORIA_DEFAULT;
    private int kernel          = KERNEL_DEFAULT;
    private int disco           = DISCO_DEFAULT;
    private int maxArchivos     = MAX_ARCHIVOS_DEFAULT;
    private int memoriaVirtual  = MEMORIA_VIRTUAL_DEFAULT;
    private int cpus            = CPUS_DEFAULT;
    private String algoritmoCPU = ALGORITMO_CPU_DEFAULT;

    private boolean huboCorrecciones = false;
    private final StringBuilder mensajesCorrecciones = new StringBuilder();

    /* ==================== CARGA ==================== */

    public void cargar() {
        File archivo = new File(ARCHIVO);

        if (!archivo.exists()) {
            System.out.println("[CONFIG] " + ARCHIVO
                    + " no existe. Creando con valores por defecto...");
            guardar();
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(archivo)) {
            props.load(in);

            memoria        = parsear(props, "memoria",        memoria);
            kernel         = parsear(props, "kernel",         kernel);
            disco          = parsear(props, "disco",          disco);
            maxArchivos    = parsear(props, "max_archivos",   maxArchivos);
            memoriaVirtual = parsear(props, "memoria_virtual", memoriaVirtual);
            cpus           = parsear(props, "cpus",           cpus);
            
            // Leer el algoritmo (si no está, usa FCFS)
            algoritmoCPU = props.getProperty("algoritmo_cpu", ALGORITMO_CPU_DEFAULT).toUpperCase();

            validarYCorregir();

            System.out.println("[CONFIG] Configuracion cargada desde " + ARCHIVO);
            System.out.println("[CONFIG]   Memoria: " + memoria + ", Kernel: " + kernel);
            System.out.println("[CONFIG]   Disco: " + disco
                    + ", MaxArchivos: " + maxArchivos + ", Swap: " + memoriaVirtual);
            System.out.println("[CONFIG]   CPUs: " + cpus);
            System.out.println("[CONFIG]   Algoritmo CPU: " + algoritmoCPU);
            System.out.println("[CONFIG]   MaxProcesos: " + MAX_PROCESOS
                    + " (FIJO por CPU)");

        } catch (IOException e) {
            System.err.println("[CONFIG] Error leyendo " + ARCHIVO
                    + ": " + e.getMessage());
            System.err.println("[CONFIG] Usando valores por defecto.");
        }
    }

    /* ==================== VALIDACION ==================== */

    private void validarYCorregir() {
        huboCorrecciones = false;
        mensajesCorrecciones.setLength(0);

        // ==== MEMORIA BASE (Mínimo absoluto) ====
        if (memoria < MEMORIA_MINIMO) {
            agregarCorreccion("memoria=" + memoria
                    + " invalido (minimo absoluto " + MEMORIA_MINIMO + "). Usando "
                    + MEMORIA_DEFAULT + ".");
            memoria = MEMORIA_DEFAULT;
        } else if (memoria > MEMORIA_MAXIMO) {
            agregarCorreccion("memoria=" + memoria
                    + " invalido (maximo " + MEMORIA_MAXIMO + "). Usando "
                    + MEMORIA_DEFAULT + ".");
            memoria = MEMORIA_DEFAULT;
        }

        // ==== CPUS ====
        if (cpus < CPUS_MINIMO) {
            agregarCorreccion("cpus=" + cpus
                    + " invalido (minimo " + CPUS_MINIMO + "). Usando " + CPUS_DEFAULT + ".");
            cpus = CPUS_DEFAULT;
        } else if (cpus > CPUS_MAXIMO) {
            agregarCorreccion("cpus=" + cpus
                    + " invalido (maximo " + CPUS_MAXIMO + "). Usando " + CPUS_MAXIMO + ".");
            cpus = CPUS_MAXIMO;
        }

        // ==== VALIDACION CRUZADA: Memoria vs CPUs ====
        // Calcula cuanta memoria se necesita para N CPUs (5 BCPs por CPU).
        int memoriaMinimaParaCpus = calcularMemoriaMinimaParaCPUs(cpus);
        
        if (memoria < memoriaMinimaParaCpus) {
            agregarCorreccion("memoria=" + memoria
                    + " no alcanza para " + cpus + " CPUs. Minimo requerido: "
                    + memoriaMinimaParaCpus + ". Usando " + memoriaMinimaParaCpus + ".");
            memoria = memoriaMinimaParaCpus;
        }

        // ==== VALIDACION CRUZADA: Disco vs CPUs ====
        int discoMinimoParaCpus = calcularDiscoMinimoParaCPUs(cpus, maxArchivos, memoriaVirtual);
        
        if (disco < discoMinimoParaCpus) {
            agregarCorreccion("disco=" + disco
                    + " no alcanza para " + cpus + " CPUs. Minimo requerido: "
                    + discoMinimoParaCpus + ". Usando " + discoMinimoParaCpus + ".");
            disco = discoMinimoParaCpus;
        }

        // ==== KERNEL ====
        int kernelMinimoPorcentaje = (int) Math.ceil(memoria * PORCENTAJE_MINIMO_KERNEL);

        if (kernel >= memoria) {
            agregarCorreccion("kernel=" + kernel
                    + " invalido (debe ser < memoria=" + memoria + "). Usando "
                    + kernelMinimoPorcentaje + " (30%).");
            kernel = kernelMinimoPorcentaje;
        } else if (kernel < kernelMinimoPorcentaje) {
            agregarCorreccion("kernel=" + kernel
                    + " invalido (minimo " + ((int)(PORCENTAJE_MINIMO_KERNEL*100))
                    + "% de memoria=" + memoria + " -> " + kernelMinimoPorcentaje
                    + "). Usando " + kernelMinimoPorcentaje + ".");
            kernel = kernelMinimoPorcentaje;
        } else if (memoria - kernel < POSICIONES_POR_INSTRUCCION) {
            agregarCorreccion("memoria-kernel=" + (memoria - kernel)
                    + " invalido (minimo " + POSICIONES_POR_INSTRUCCION
                    + "). Ajustando kernel a " + (memoria - POSICIONES_POR_INSTRUCCION) + ".");
            kernel = memoria - POSICIONES_POR_INSTRUCCION;
        }

        // ==== DISCO BASE ====
        if (disco < DISCO_MINIMO) {
            agregarCorreccion("disco=" + disco
                    + " invalido (minimo " + DISCO_MINIMO + "). Usando " + DISCO_DEFAULT + ".");
            disco = DISCO_DEFAULT;
        } else if (disco > DISCO_MAXIMO) {
            agregarCorreccion("disco=" + disco
                    + " invalido (maximo " + DISCO_MAXIMO + "). Usando " + DISCO_DEFAULT + ".");
            disco = DISCO_DEFAULT;
        }

        // ==== MAX_ARCHIVOS ====
        if (maxArchivos < MAX_ARCHIVOS_MINIMO) {
            agregarCorreccion("max_archivos=" + maxArchivos
                    + " invalido (minimo " + MAX_ARCHIVOS_MINIMO + "). Usando "
                    + MAX_ARCHIVOS_DEFAULT + ".");
            maxArchivos = MAX_ARCHIVOS_DEFAULT;
        } else if (maxArchivos > MAX_ARCHIVOS_MAXIMO) {
            agregarCorreccion("max_archivos=" + maxArchivos
                    + " invalido (maximo " + MAX_ARCHIVOS_MAXIMO + "). Usando "
                    + MAX_ARCHIVOS_DEFAULT + ".");
            maxArchivos = MAX_ARCHIVOS_DEFAULT;
        }

        // ==== MEMORIA_VIRTUAL ====
        if (memoriaVirtual < MEMORIA_VIRTUAL_MINIMO) {
            agregarCorreccion("memoria_virtual=" + memoriaVirtual
                    + " invalido (minimo " + MEMORIA_VIRTUAL_MINIMO + "). Usando "
                    + MEMORIA_VIRTUAL_DEFAULT + ".");
            memoriaVirtual = MEMORIA_VIRTUAL_DEFAULT;
        } else if (memoriaVirtual > MEMORIA_VIRTUAL_MAXIMO) {
            agregarCorreccion("memoria_virtual=" + memoriaVirtual
                    + " invalido (maximo " + MEMORIA_VIRTUAL_MAXIMO + "). Usando "
                    + MEMORIA_VIRTUAL_DEFAULT + ".");
            memoriaVirtual = MEMORIA_VIRTUAL_DEFAULT;
        }

        // ==== ALGORITMO CPU ====
        if (!algoritmoCPU.equals("FCFS") && !algoritmoCPU.equals("SPN")) {
            agregarCorreccion("algoritmo_cpu=" + algoritmoCPU
                    + " invalido. Usando " + ALGORITMO_CPU_DEFAULT + ".");
            algoritmoCPU = ALGORITMO_CPU_DEFAULT;
        }

        // ==== VALIDACION CRUZADA: kernel vs maxArchivos ====
        int kernelMinimoParaUno = Memoria.getTamanoKernelMinimo(1, maxArchivos);
        if (kernel < kernelMinimoParaUno) {
            int espacioParaLista = kernel
                    - BCP.POSICIONES_REQUERIDAS
                    - Memoria.POSICIONES_POR_BLOQUE_TABLA_MEMORIA;
            int maxArchivosPosibles = espacioParaLista
                    / Memoria.POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;

            if (maxArchivosPosibles < MAX_ARCHIVOS_MINIMO) {
                int kernelMinimoReal = Memoria.getTamanoKernelMinimo(1, MAX_ARCHIVOS_MINIMO);
                agregarCorreccion("kernel=" + kernel
                        + " no alcanza ni para 1 BCP con max_archivos=" + maxArchivos
                        + ". Ajustando kernel a " + kernelMinimoReal + ".");
                kernel = kernelMinimoReal;
            } else {
                agregarCorreccion("kernel=" + kernel
                        + " no alcanza para max_archivos=" + maxArchivos
                        + " + 1 BCP. Reduciendo max_archivos a " + maxArchivosPosibles + ".");
                maxArchivos = maxArchivosPosibles;
            }
        }

        // ==== VALIDACION CRUZADA: espacio del disco ====
        int espacioIndiceAsm = maxArchivos * 4;
        int espacioIndiceProceso = maxArchivos * 4;
        int espacioIndice = espacioIndiceAsm + espacioIndiceProceso;
        int espacioOcupado = espacioIndice + memoriaVirtual;
        int espacioArchivos = disco - espacioOcupado;

        if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
            agregarCorreccion("El disco no tiene espacio suficiente: "
                    + "indice (" + maxArchivos + "x8=" + espacioIndice + ")"
                    + " + swap (" + memoriaVirtual + ")"
                    + " = " + espacioOcupado
                    + ", deja " + espacioArchivos
                    + " para archivos (minimo " + ESPACIO_ARCHIVOS_MINIMO + ")."
                    + " Ajustando memoria_virtual a " + MEMORIA_VIRTUAL_DEFAULT + ".");
            memoriaVirtual = MEMORIA_VIRTUAL_DEFAULT;

            espacioOcupado = espacioIndice + memoriaVirtual;
            espacioArchivos = disco - espacioOcupado;
            if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
                agregarCorreccion("Aun sin espacio. Ajustando max_archivos a "
                        + MAX_ARCHIVOS_DEFAULT + ".");
                maxArchivos = MAX_ARCHIVOS_DEFAULT;
            }
        }

        if (huboCorrecciones) {
            System.err.println("[CONFIG] Se corrigieron valores invalidos:");
            System.err.println(mensajesCorrecciones.toString());
            System.err.println("[CONFIG] Guardando " + ARCHIVO + "...");
            guardar();
        }
    }

    /* ==================== FÓRMULAS DINÁMICAS ==================== */

    /**
     * Calcula la memoria mínima necesaria para soportar N CPUs al 100%.
     * Cada CPU necesita espacio en el kernel para 5 BCPs y 5 bloques en la TablaMemoria.
     * Además, la ListaDeTrabajos ocupa espacio fijo.
     */
    public static int calcularMemoriaMinimaParaCPUs(int cpus) {
        // Tamaño base del kernel para 10 archivos
        int tamanoListaTrabajos = MAX_ARCHIVOS_DEFAULT * Memoria.POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;
        
        // Cada CPU necesita 5 BCPs + 5 bloques en TablaMemoria
        int bcpsPorCpu = 5;
        int espacioPorCpu = bcpsPorCpu * (BCP.POSICIONES_REQUERIDAS + Memoria.POSICIONES_POR_BLOQUE_TABLA_MEMORIA);
        
        int kernelMinimoReal = tamanoListaTrabajos + (cpus * espacioPorCpu);
        
        // Como el kernel debe ser al menos el 30% de la memoria
        int memoriaMinima = (int) Math.ceil(kernelMinimoReal / PORCENTAJE_MINIMO_KERNEL);
        
        // Redondear a un número más amigable (múltiplo de 100)
        return ((memoriaMinima + 99) / 100) * 100;
    }

    /**
     * Calcula el disco mínimo necesario para N CPUs.
     * Básicamente, el disco debe tener espacio para los índices, el swap,
     * y los archivos de al menos los procesos de todas las CPUs.
     */
    public static int calcularDiscoMinimoParaCPUs(int cpus, int maxArchivos, int memoriaVirtual) {
        int espacioIndices = maxArchivos * 4 * 2; // Índice ASM + Índice PROCESO
        int espacioOcupado = espacioIndices + memoriaVirtual;
        
        // Necesitamos al menos 64 posiciones para archivos
        int discoMinimo = espacioOcupado + ESPACIO_ARCHIVOS_MINIMO;
        
        return Math.max(discoMinimo, DISCO_MINIMO);
    }

    private void agregarCorreccion(String mensaje) {
        huboCorrecciones = true;
        mensajesCorrecciones.append("  - ").append(mensaje).append("\n");
    }

    /* ==================== GUARDADO ==================== */

    public void guardar() {
        Properties props = new Properties();
        props.setProperty("memoria",         String.valueOf(memoria));
        props.setProperty("kernel",          String.valueOf(kernel));
        props.setProperty("disco",           String.valueOf(disco));
        props.setProperty("max_archivos",    String.valueOf(maxArchivos));
        props.setProperty("memoria_virtual", String.valueOf(memoriaVirtual));
        props.setProperty("cpus",            String.valueOf(cpus));
        props.setProperty("algoritmo_cpu",   algoritmoCPU);

        try (FileOutputStream out = new FileOutputStream(ARCHIVO)) {
            props.store(out, "Configuracion del Sistema - Proyecto 2 SO");
            System.out.println("[CONFIG] Configuracion guardada en " + ARCHIVO);
        } catch (IOException e) {
            System.err.println("[CONFIG] Error guardando " + ARCHIVO
                    + ": " + e.getMessage());
        }
    }

    /* ==================== HELPERS ==================== */

    private int parsear(Properties props, String clave, int defaultValor) {
        String valor = props.getProperty(clave);
        if (valor == null) return defaultValor;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Valor invalido para '" + clave
                    + "': " + valor + ". Usando default " + defaultValor);
            return defaultValor;
        }
    }

    /* ==================== GETTERS ==================== */

    public int getMemoria()        { return memoria; }
    public int getKernel()         { return kernel; }
    public int getDisco()          { return disco; }
    public int getMaxArchivos()    { return maxArchivos; }
    public int getMemoriaVirtual() { return memoriaVirtual; }
    public int getCpus()           { return cpus; }
    public String getAlgoritmoCPU(){ return algoritmoCPU; }
    public int getMaxProcesos()    { return MAX_PROCESOS; }

    public boolean huboCorrecciones() { return huboCorrecciones; }
    public String getMensajesCorrecciones() { return mensajesCorrecciones.toString(); }

    /* ==================== SETTERS ==================== */

    public void setMemoria(int v)        { this.memoria = v; }
    public void setKernel(int v)         { this.kernel = v; }
    public void setDisco(int v)          { this.disco = v; }
    public void setMaxArchivos(int v)    { this.maxArchivos = v; }
    public void setMemoriaVirtual(int v) { this.memoriaVirtual = v; }
    public void setCpus(int v)           { this.cpus = v; }
    public void setAlgoritmoCPU(String v){ this.algoritmoCPU = v; }

    @Override
    public String toString() {
        return "ConfiguracionExterna{"
                + "memoria=" + memoria
                + ", kernel=" + kernel
                + ", disco=" + disco
                + ", maxArchivos=" + maxArchivos
                + ", memoriaVirtual=" + memoriaVirtual
                + ", cpus=" + cpus
                + ", algoritmoCPU='" + algoritmoCPU + '\''
                + ", maxProcesos=" + MAX_PROCESOS
                + "}";
    }
}