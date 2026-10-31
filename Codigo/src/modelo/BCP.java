package modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Bloque de Control de Proceso (BCP).
 *
 * El BCP NO guarda datos en si mismo. Es una VISTA sobre las
 * POSICIONES_REQUERIDAS posiciones consecutivas de la zona kernel
 * de la Memoria, donde vive cada uno de sus campos.
 *
 * Offsets (posiciones relativas a la direccion base del BCP):
 *   0  = ID
 *   1  = Estado
 *   2  = Prioridad
 *   3  = PC
 *   4  = IR
 *   5  = AC
 *   6  = AX
 *   7  = BX
 *   8  = CX
 *   9  = DX               ← String (nombre de archivo o numero)
 *   10 = Overflow
 *   11 = BanderaIgual
 *   12 = PesoPendiente
 *   13 = Pila[0]           ← INICIO PILA (5 posiciones)
 *   14 = Pila[1]
 *   15 = Pila[2]
 *   16 = Pila[3]
 *   17 = Pila[4]           ← FIN PILA
 *   18 = Base
 *   19 = Alcance
 *   20 = TiempoInicioSimulado  ← int (reloj global al despachar). -1 = no marcado.
 *   21 = TiempoFinSimulado     ← int (reloj global al terminar).  -1 = no marcado.
 *   22 = CPU Asignado
 *   23 = ArchivoAbierto[0] ← INICIO ARCHIVOS (5 posiciones)
 *   24 = ArchivoAbierto[1]
 *   25 = ArchivoAbierto[2]
 *   26 = ArchivoAbierto[3]
 *   27 = ArchivoAbierto[4] ← FIN ARCHIVOS
 *   28 = Siguiente BCP
 *   29 = Direccion
 *   30 = TiempoLlegada     ← NUEVO (Proyecto 2)
 *   31 = Rafaga            ← NUEVO (Proyecto 2)
 *   32 = TiempoRestante    ← NUEVO (Proyecto 2)
 *   33 = Turnaround        ← NUEVO (Proyecto 2)
 *   34 = Tr/Ts             ← NUEVO (Proyecto 2)
 *
 * IMPORTANTE: DX es un String (no un int) porque el enunciado indica
 * que en INT 21H, DX guarda la cadena de texto del nombre del archivo.
 * Para compatibilidad con instrucciones aritmeticas (ADD, SUB, CMP),
 * se proveen los metodos getDxAsInt() y setDxAsInt().
 *
 * IMPORTANTE (Proyecto 2): Los tiempos de inicio/fin usan el RELOJ
 * GLOBAL SIMULADO (int), NO LocalDateTime. El centinela -1 indica
 * "aun no marcado". Esto corrige el bug de estadisticas que mostraban
 * tiempo real en lugar del tiempo simulado.
 *
 * Hay dos formas de crear un BCP:
 *   1. new BCP(memoria, direccionBase, id, prioridad) → INICIALIZA los campos.
 *   2. new BCP(memoria, direccionBase)                → VISTA sobre BCP existente.
 */
public class BCP {

    public static final int TAMANO_MAXIMO_PILA = 5;
    public static final int TAMANO_MAXIMO_ARCHIVOS = 5;
    public static final int POSICIONES_REQUERIDAS = 35;

    /** Centinela que indica que un tiempo simulado aun no fue marcado. */
    public static final int TIEMPO_NO_MARCADO = -1;

    public static final int OFF_ID             = 0;
    public static final int OFF_ESTADO         = 1;
    public static final int OFF_PRIORIDAD      = 2;
    public static final int OFF_PC             = 3;
    public static final int OFF_IR             = 4;
    public static final int OFF_AC             = 5;
    public static final int OFF_AX             = 6;
    public static final int OFF_BX             = 7;
    public static final int OFF_CX             = 8;
    public static final int OFF_DX             = 9;
    public static final int OFF_OVERFLOW       = 10;
    public static final int OFF_BANDERA_IGUAL  = 11;
    public static final int OFF_PESO_PENDIENTE = 12;

    public static final int OFF_PILA_INICIO    = 13;

    public static final int OFF_BASE           = 18;
    public static final int OFF_ALCANCE        = 19;
    public static final int OFF_TIEMPO_INICIO  = 20;
    public static final int OFF_TIEMPO_FIN     = 21;
    public static final int OFF_CPU_ASIGNADO   = 22;

    public static final int OFF_ARCHIVOS_INICIO = 23;

    public static final int OFF_SIGUIENTE_BCP  = 28;
    public static final int OFF_DIRECCION      = 29;

    // === NUEVOS OFFSETS (Proyecto 2) ===
    public static final int OFF_TIEMPO_LLEGADA  = 30;
    public static final int OFF_RAFAGA          = 31;
    public static final int OFF_TIEMPO_RESTANTE = 32;
    public static final int OFF_TURNAROUND      = 33;
    public static final int OFF_TR_TS           = 34;

    private final Memoria memoria;
    private final int direccionBase;

    /**
     * Crea un BCP NUEVO que ocupa POSICIONES_REQUERIDAS posiciones
     * consecutivas del kernel a partir de direccionBase.
     * INICIALIZA todos los campos con valores por defecto.
     */
    public BCP(Memoria memoria, int direccionBase, int id, int prioridad) {
        this.memoria = memoria;
        this.direccionBase = direccionBase;

        setId(id);
        setEstado(EstadoProceso.NEW);
        setPrioridad(prioridad);
        setPc(0);
        setIr(0);
        setAc(0);
        setAx(0);
        setBx(0);
        setCx(0);
        setDx("0");   // ← String
        setOverflow(false);
        setBanderaIgual(false);
        setPesoPendiente(0);

        // Limpiar las 5 posiciones de la pila
        for (int i = 0; i < TAMANO_MAXIMO_PILA; i++) {
            escribir(OFF_PILA_INICIO + i, null);
        }

        setBase(0);
        setAlcance(0);
        setTiempoInicio(TIEMPO_NO_MARCADO);
        setTiempoFin(TIEMPO_NO_MARCADO);
        setCpuAsignado(-1);

        // Limpiar las 5 posiciones de archivos abiertos
        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            escribir(OFF_ARCHIVOS_INICIO + i, null);
        }

        setSiguienteBCP(-1);
        setDireccion(direccionBase);

        // === NUEVOS CAMPOS (Proyecto 2) ===
        setTiempoLlegada(0);
        setRafaga(0);
        setTiempoRestante(0);
        setTurnaround(-1);
        setTrTs(-1.0);
    }

    /**
     * Crea una VISTA sobre un BCP que YA EXISTE en memoria.
     * NO inicializa ningun campo: solo apunta a la direccion dada.
     */
    public BCP(Memoria memoria, int direccionBase) {
        this.memoria = memoria;
        this.direccionBase = direccionBase;
    }

    /* ============ ACCESO A MEMORIA ============ */

    // Lee el campo ubicado en el offset indicado.
    private Object leer(int offset) {
        return memoria.leer(direccionBase + offset);
    }

    // Escribe un valor en el campo ubicado en el offset indicado.
    private void escribir(int offset, Object valor) {
        memoria.escribir(direccionBase + offset, valor);
    }

    /* ============ EXTRACCIÓN / RESTAURACIÓN ============ */

    // Devuelve los 35 valores del BCP como arreglo.
    public Object[] extraerValores() {
        Object[] valores = new Object[POSICIONES_REQUERIDAS];
        for (int i = 0; i < POSICIONES_REQUERIDAS; i++) {
            valores[i] = leer(i);
        }
        return valores;
    }

    // Escribe los 35 valores del arreglo en el BCP.
    public void restaurarValores(Object[] valores) {
        if (valores == null || valores.length != POSICIONES_REQUERIDAS) {
            throw new IllegalArgumentException(
                "El arreglo debe tener exactamente " + POSICIONES_REQUERIDAS + " valores");
        }
        for (int i = 0; i < POSICIONES_REQUERIDAS; i++) {
            escribir(i, valores[i]);
        }
    }

    // Construye un arreglo con los valores por defecto de un BCP nuevo.
    public static Object[] valoresIniciales(int id, int prioridad, int alcance) {
        Object[] valores = new Object[POSICIONES_REQUERIDAS];
        valores[OFF_ID]             = id;
        valores[OFF_ESTADO]         = EstadoProceso.READY_SUSPEND;
        valores[OFF_PRIORIDAD]      = prioridad;
        valores[OFF_PC]             = 0;
        valores[OFF_IR]             = 0;
        valores[OFF_AC]             = 0;
        valores[OFF_AX]             = 0;
        valores[OFF_BX]             = 0;
        valores[OFF_CX]             = 0;
        valores[OFF_DX]             = "0";   // ← String
        valores[OFF_OVERFLOW]       = false;
        valores[OFF_BANDERA_IGUAL]  = false;
        valores[OFF_PESO_PENDIENTE] = 0;

        for (int i = 0; i < TAMANO_MAXIMO_PILA; i++) {
            valores[OFF_PILA_INICIO + i] = null;
        }

        valores[OFF_BASE]           = 0;
        valores[OFF_ALCANCE]        = alcance;
        valores[OFF_TIEMPO_INICIO]  = TIEMPO_NO_MARCADO;   // ← int
        valores[OFF_TIEMPO_FIN]     = TIEMPO_NO_MARCADO;   // ← int
        valores[OFF_CPU_ASIGNADO]   = -1;

        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            valores[OFF_ARCHIVOS_INICIO + i] = null;
        }

        valores[OFF_SIGUIENTE_BCP]  = -1;
        valores[OFF_DIRECCION]      = -1;

        // === NUEVOS CAMPOS (Proyecto 2) ===
        valores[OFF_TIEMPO_LLEGADA]  = 0;
        valores[OFF_RAFAGA]          = alcance;
        valores[OFF_TIEMPO_RESTANTE] = alcance;
        valores[OFF_TURNAROUND]      = -1;
        valores[OFF_TR_TS]           = -1.0;

        return valores;
    }

    /* ============ SINCRONIZACIÓN CON CPU ============ */

    // Copia el estado de los registros de la CPU al BCP.
    public void actualizarDesdeCPU(CPU cpu) {
        setPc(cpu.getPC());
        setIr(cpu.getIR());
        setAc(cpu.getAC());
        setAx(cpu.getAX());
        setBx(cpu.getBX());
        setCx(cpu.getCX());
        setDx(cpu.getDX());   // ← String
        setOverflow(cpu.getOverflow());
        setBanderaIgual(cpu.getBanderaIgual());
    }

    // Carga el estado del BCP en los registros de la CPU.
    public void actualizarHaciaCPU(CPU cpu) {
        cpu.setPC(getPc());
        cpu.setIR(getIr());
        cpu.setAC(getAc());
        cpu.setAX(getAx());
        cpu.setBX(getBx());
        cpu.setCX(getCx());
        cpu.setDX(getDx());   // ← String
        cpu.setOverflow(getOverflow());
        cpu.setBanderaIgual(getBanderaIgual());
    }

    /* ============ PILA (5 posiciones) ============ */

    // Apila un valor en la primera posicion libre.
    public void apilar(int valor) {
        for (int i = 0; i < TAMANO_MAXIMO_PILA; i++) {
            int off = OFF_PILA_INICIO + i;
            if (leer(off) == null) {
                escribir(off, valor);
                return;
            }
        }
        throw new IllegalStateException(
            "Desbordamiento de pila en el proceso " + getId()
            + " (maximo " + TAMANO_MAXIMO_PILA + " elementos)");
    }

    // Desapila y devuelve el valor de la cima.
    public int desapilar() {
        for (int i = TAMANO_MAXIMO_PILA - 1; i >= 0; i--) {
            int off = OFF_PILA_INICIO + i;
            Object v = leer(off);
            if (v != null) {
                escribir(off, null);
                return (Integer) v;
            }
        }
        throw new IllegalStateException(
            "Subdesbordamiento de pila en el proceso " + getId() + " (pila vacia)");
    }

    // Devuelve la pila del proceso como Stack.
    public Stack<Integer> getPila() {
        Stack<Integer> pila = new Stack<>();
        for (int i = 0; i < TAMANO_MAXIMO_PILA; i++) {
            Object v = leer(OFF_PILA_INICIO + i);
            if (v != null) pila.push((Integer) v);
        }
        return pila;
    }

    // Reemplaza la pila del proceso con la indicada.
    public void setPila(Stack<Integer> pila) {
        if (pila == null) pila = new Stack<>();
        if (pila.size() > TAMANO_MAXIMO_PILA) {
            throw new IllegalArgumentException(
                "La pila no puede tener mas de " + TAMANO_MAXIMO_PILA + " elementos");
        }
        for (int i = 0; i < TAMANO_MAXIMO_PILA; i++) {
            escribir(OFF_PILA_INICIO + i, null);
        }
        int i = 0;
        for (Integer v : pila) {
            escribir(OFF_PILA_INICIO + i, v);
            i++;
        }
    }

    /* ============ ARCHIVOS ABIERTOS (5 posiciones) ============ */

    /**
     * Abre un archivo: lo agrega a la primera posicion libre.
     *
     * @return true si se abrio, false si ya estaba o si la lista esta llena
     */
    public boolean abrirArchivo(String nombre) {
        if (nombre == null || nombre.isEmpty()) return false;

        // Verificar si ya esta abierto
        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            Object v = leer(OFF_ARCHIVOS_INICIO + i);
            if (v != null && v.equals(nombre)) {
                return false;   // ya abierto
            }
        }

        // Buscar primer hueco
        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            int off = OFF_ARCHIVOS_INICIO + i;
            if (leer(off) == null) {
                escribir(off, nombre);
                return true;
            }
        }
        return false;   // lista llena
    }

    /**
     * Cierra un archivo: lo quita de la lista.
     *
     * @return true si se cerro, false si no estaba
     */
    public boolean cerrarArchivo(String nombre) {
        if (nombre == null) return false;

        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            int off = OFF_ARCHIVOS_INICIO + i;
            Object v = leer(off);
            if (v != null && v.equals(nombre)) {
                escribir(off, null);
                return true;
            }
        }
        return false;
    }

    /**
     * @return la lista de archivos abiertos (solo los no-null).
     */
    public List<String> getArchivosAbiertos() {
        List<String> lista = new ArrayList<>();
        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            Object v = leer(OFF_ARCHIVOS_INICIO + i);
            if (v != null) lista.add((String) v);
        }
        return lista;
    }

    /**
     * Reemplaza la lista completa de archivos abiertos.
     */
    public void setArchivosAbiertos(List<String> archivos) {
        for (int i = 0; i < TAMANO_MAXIMO_ARCHIVOS; i++) {
            escribir(OFF_ARCHIVOS_INICIO + i, null);
        }
        if (archivos == null) return;
        if (archivos.size() > TAMANO_MAXIMO_ARCHIVOS) {
            throw new IllegalArgumentException(
                "No pueden haber mas de " + TAMANO_MAXIMO_ARCHIVOS + " archivos abiertos");
        }
        int i = 0;
        for (String a : archivos) {
            escribir(OFF_ARCHIVOS_INICIO + i, a);
            i++;
        }
    }

    /* ============ TIEMPOS (reloj global simulado) ============ */

    /**
     * Marca el instante de inicio si aun no estaba marcado.
     * Se debe llamar cuando el proceso pasa a RUNNING (despacho).
     *
     * @param relojGlobal valor actual del reloj simulado del sistema
     */
    public void marcarInicio(int relojGlobal) {
        if (getTiempoInicio() == TIEMPO_NO_MARCADO) {
            setTiempoInicio(relojGlobal);
        }
    }

    /**
     * Marca el instante de fin del proceso.
     * Se debe llamar cuando el proceso pasa a EXIT.
     *
     * @param relojGlobal valor actual del reloj simulado del sistema
     */
    public void marcarFin(int relojGlobal) {
        setTiempoFin(relojGlobal);
    }

    /**
     * Devuelve la duracion simulada en unidades de reloj (segundos
     * simulados), o -1 si falta inicio o fin.
     */
    public int getDuracionSimulada() {
        int inicio = getTiempoInicio();
        int fin = getTiempoFin();
        if (inicio == TIEMPO_NO_MARCADO || fin == TIEMPO_NO_MARCADO) return -1;
        return fin - inicio;
    }

    /**
     * @return true si el proceso ya tiene marcado su tiempo de inicio.
     */
    public boolean tieneInicioMarcado() {
        return getTiempoInicio() != TIEMPO_NO_MARCADO;
    }

    /* ============ AH / AL ============ */

    // Devuelve el byte alto de AX.
    public int getAh() { return (getAx() >> 8) & 0xFF; }

    // Asigna el byte alto de AX, conservando AL.
    public void setAh(int ah) {
        int al = getAl();
        setAx(((ah & 0xFF) << 8) | al);
    }

    // Devuelve el byte bajo de AX.
    public int getAl() { return getAx() & 0xFF; }

    // Asigna el byte bajo de AX, conservando AH.
    public void setAl(int al) {
        int ah = getAh();
        setAx((ah << 8) | (al & 0xFF));
    }

    /* ============ GETTERS ============ */

    public Memoria getMemoria() { return memoria; }
    public int getDireccionBase() { return direccionBase; }

    public int getId() { return (Integer) leer(OFF_ID); }
    public EstadoProceso getEstado() { return (EstadoProceso) leer(OFF_ESTADO); }
    public int getPrioridad() { return (Integer) leer(OFF_PRIORIDAD); }
    public int getPc() { return (Integer) leer(OFF_PC); }
    public int getIr() { return (Integer) leer(OFF_IR); }
    public int getAc() { return (Integer) leer(OFF_AC); }
    public int getAx() { return (Integer) leer(OFF_AX); }
    public int getBx() { return (Integer) leer(OFF_BX); }
    public int getCx() { return (Integer) leer(OFF_CX); }

    /**
     * Devuelve DX como String.
     * Puede contener un nombre de archivo o un numero en texto.
     */
    public String getDx() {
        Object v = leer(OFF_DX);
        return (v != null) ? v.toString() : "0";
    }

    /**
     * Devuelve DX como entero, parseando el string.
     * Si DX no es numerico, devuelve 0.
     * Se usa para compatibilidad con instrucciones aritmeticas.
     */
    public int getDxAsInt() {
        try {
            return Integer.parseInt(getDx());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean getOverflow() {
        Boolean v = (Boolean) leer(OFF_OVERFLOW);
        return v != null && v;
    }

    public boolean getBanderaIgual() {
        Boolean v = (Boolean) leer(OFF_BANDERA_IGUAL);
        return v != null && v;
    }

    public int getPesoPendiente() { return (Integer) leer(OFF_PESO_PENDIENTE); }

    public int getBase() { return (Integer) leer(OFF_BASE); }
    public int getAlcance() { return (Integer) leer(OFF_ALCANCE); }

    /**
     * Devuelve el reloj simulado en el que el proceso comenzo a ejecutarse.
     * @return el instante simulado, o {@link #TIEMPO_NO_MARCADO} si aun no fue despachado.
     */
    public int getTiempoInicio() {
        Object v = leer(OFF_TIEMPO_INICIO);
        return (v != null) ? (Integer) v : TIEMPO_NO_MARCADO;
    }

    /**
     * Devuelve el reloj simulado en el que el proceso termino.
     * @return el instante simulado, o {@link #TIEMPO_NO_MARCADO} si aun no ha terminado.
     */
    public int getTiempoFin() {
        Object v = leer(OFF_TIEMPO_FIN);
        return (v != null) ? (Integer) v : TIEMPO_NO_MARCADO;
    }

    public int getCpuAsignado() { return (Integer) leer(OFF_CPU_ASIGNADO); }

    public int getSiguienteBCP() { return (Integer) leer(OFF_SIGUIENTE_BCP); }
    public int getDireccion() { return (Integer) leer(OFF_DIRECCION); }

    // === NUEVOS CAMPOS (Proyecto 2) ===

    // Devuelve el tiempo de llegada del proceso (asignado por el usuario).
    public int getTiempoLlegada() {
        Object v = leer(OFF_TIEMPO_LLEGADA);
        return (v != null) ? (Integer) v : 0;
    }

    // Asigna el tiempo de llegada del proceso.
    public void setTiempoLlegada(int tiempoLlegada) {
        escribir(OFF_TIEMPO_LLEGADA, tiempoLlegada);
    }

    // Devuelve la ráfaga del proceso (cantidad de instrucciones).
    public int getRafaga() {
        Object v = leer(OFF_RAFAGA);
        return (v != null) ? (Integer) v : 0;
    }

    // Asigna la ráfaga del proceso.
    public void setRafaga(int rafaga) {
        escribir(OFF_RAFAGA, rafaga);
    }

    // Devuelve el tiempo restante (para SRT).
    public int getTiempoRestante() {
        Object v = leer(OFF_TIEMPO_RESTANTE);
        return (v != null) ? (Integer) v : 0;
    }

    // Asigna el tiempo restante.
    public void setTiempoRestante(int tiempoRestante) {
        escribir(OFF_TIEMPO_RESTANTE, tiempoRestante);
    }

    // Devuelve el turnaround (tiempo final - tiempo de llegada).
    public int getTurnaround() {
        Object v = leer(OFF_TURNAROUND);
        return (v != null) ? (Integer) v : -1;
    }

    // Asigna el turnaround.
    public void setTurnaround(int turnaround) {
        escribir(OFF_TURNAROUND, turnaround);
    }

    // Devuelve Tr/Ts (turnaround / service time).
    public double getTrTs() {
        Object v = leer(OFF_TR_TS);
        return (v != null) ? (Double) v : -1.0;
    }

    // Asigna Tr/Ts.
    public void setTrTs(double trTs) {
        escribir(OFF_TR_TS, trTs);
    }

    /* ============ SETTERS ============ */

    public void setId(int id) { escribir(OFF_ID, id); }
    public void setEstado(EstadoProceso estado) { escribir(OFF_ESTADO, estado); }
    public void setPrioridad(int prioridad) { escribir(OFF_PRIORIDAD, prioridad); }
    public void setPc(int pc) { escribir(OFF_PC, pc); }
    public void setIr(int ir) { escribir(OFF_IR, ir); }
    public void setAc(int ac) { escribir(OFF_AC, ac); }
    public void setAx(int ax) { escribir(OFF_AX, ax); }
    public void setBx(int bx) { escribir(OFF_BX, bx); }
    public void setCx(int cx) { escribir(OFF_CX, cx); }

    /**
     * Asigna DX como String.
     * @param dx valor a asignar (si es null, se asigna "0")
     */
    public void setDx(String dx) {
        escribir(OFF_DX, (dx != null) ? dx : "0");
    }

    /**
     * Asigna DX como entero, convirtiendolo a String.
     * Se usa para compatibilidad con instrucciones aritmeticas.
     */
    public void setDxAsInt(int dx) {
        escribir(OFF_DX, String.valueOf(dx));
    }

    public void setOverflow(boolean overflow) { escribir(OFF_OVERFLOW, overflow); }
    public void setBanderaIgual(boolean banderaIgual) { escribir(OFF_BANDERA_IGUAL, banderaIgual); }
    public void setPesoPendiente(int pesoPendiente) { escribir(OFF_PESO_PENDIENTE, pesoPendiente); }
    public void setBase(int base) { escribir(OFF_BASE, base); }
    public void setAlcance(int alcance) { escribir(OFF_ALCANCE, alcance); }

    /**
     * Asigna el instante simulado de inicio.
     * @param tiempoInicio reloj global, o {@link #TIEMPO_NO_MARCADO}.
     */
    public void setTiempoInicio(int tiempoInicio) {
        escribir(OFF_TIEMPO_INICIO, tiempoInicio);
    }

    /**
     * Asigna el instante simulado de fin.
     * @param tiempoFin reloj global, o {@link #TIEMPO_NO_MARCADO}.
     */
    public void setTiempoFin(int tiempoFin) {
        escribir(OFF_TIEMPO_FIN, tiempoFin);
    }

    public void setCpuAsignado(int cpuAsignado) { escribir(OFF_CPU_ASIGNADO, cpuAsignado); }
    public void setSiguienteBCP(int siguienteBCP) { escribir(OFF_SIGUIENTE_BCP, siguienteBCP); }
    public void setDireccion(int direccion) { escribir(OFF_DIRECCION, direccion); }

    /* ============ toString ============ */

    @Override
    public String toString() {
        return "BCP[id=" + getId() + ", estado=" + getEstado()
                + ", base=" + getBase() + ", alcance=" + getAlcance() + "]";
    }
}