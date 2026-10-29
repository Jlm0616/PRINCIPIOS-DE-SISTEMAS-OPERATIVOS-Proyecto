package modelo;

/**
 * Memoria principal de la maquina virtual (Proyecto 1).
 *
 * Se divide en DOS zonas:
 *   - Zona KERNEL: posiciones [0, limiteKernelUsuario)
 *   - Zona USUARIO: posiciones [limiteKernelUsuario, tamanoMemoria)
 *
 * La zona KERNEL contiene 3 SUB-ZONAS:
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ZONA KERNEL                                         │
 *   ├──────────────────────────────────────────────────────┤
 *   │  ListaDeTrabajos                                     │
 *   │    → [nombre, inicio, fin, zona] × maxArchivos      │
 *   │    → Cada entrada ocupa 4 posiciones                 │
 *   ├──────────────────────────────────────────────────────┤
 *   │  BCPs                                                │
 *   │    → bcpsQueCaben × 30 posiciones                    │
 *   ├──────────────────────────────────────────────────────┤
 *   │  TablaMemoria                                        │
 *   │    → bcpsQueCaben × 3 posiciones (id, inicio, tam)  │
 *   └──────────────────────────────────────────────────────┘
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ZONA USUARIO                                        │
 *   │    → Instrucciones de los procesos activos           │
 *   └──────────────────────────────────────────────────────┘
 *
 * La ListaDeTrabajos va PRIMERO en el kernel y apunta a los
 * indices del disco. Cada entrada guarda la info completa del
 * trabajo: [nombre, inicio, fin, zona].
 *
 * IMPORTANTE: el numero de BCPs que caben se calcula a partir
 * del tamano del kernel. Si el kernel no alcanza para maxProcesos,
 * se usan los BCPs que quepan y los procesos sobrantes esperan
 * en la ListaDeTrabajos (disco), segun lo indica el enunciado:
 *
 *   "En el caso de que no exista espacio para almacenar un proceso
 *    en memoria principal, este debe esperar hasta que sea liberado."
 */
public class Memoria {

    /* ==================== TAMANOS FIJOS ==================== */

    /** Cantidad maxima de BCPs que pueden caber en el kernel (5 CPUs * 5). */
    public static final int MAX_BCPS = 25; // <-- CAMBIADO: Antes era 5

    /** Tamano minimo de la memoria (configurable por el usuario). */
    public static final int TAMANO_MINIMO = 256;

    /** Posiciones por entrada de la ListaDeTrabajos (nombre, inicio, fin, zona). */
    public static final int POSICIONES_POR_ENTRADA_LISTA_TRABAJOS = 4;

    /** Posiciones por bloque en la TablaMemoria (id, inicio, tamano). */
    public static final int POSICIONES_POR_BLOQUE_TABLA_MEMORIA = 3;

    /* ==================== ZONAS (calculadas) ==================== */

    private final int inicioListaTrabajos;
    private final int finListaTrabajos;
    private final int tamanoListaTrabajos;

    private final int inicioBCPs;
    private final int finBCPs;

    private final int inicioTablaMemoria;
    private final int finTablaMemoria;
    private final int tamanoTablaMemoria;

    private final int limiteKernelUsuario;

    /* ==================== DATOS ==================== */

    private final int tamanoMemoria;
    private final int maxProcesos;
    private final int maxArchivos;
    private final int bcpsQueCaben;

    private final Object[] arregloMemoria;

    /* ==================== CONSTRUCTOR ==================== */

    /**
     * Crea una memoria con el tamano y limite kernel indicados.
     *
     * @param tamanoMemoria       cantidad total de posiciones (>= TAMANO_MINIMO)
     * @param limiteKernelUsuario primera posicion de la zona usuario
     * @param maxProcesos         cantidad maxima deseada de procesos (tope 25)
     * @param maxArchivos         cantidad maxima de archivos (para ListaDeTrabajos)
     * @throws IllegalArgumentException si la configuracion es invalida
     */
    public Memoria(int tamanoMemoria, int limiteKernelUsuario,
                   int maxProcesos, int maxArchivos) {
        if (tamanoMemoria < TAMANO_MINIMO) {
            throw new IllegalArgumentException(
                "Tamano de memoria menor a " + TAMANO_MINIMO);
        }
        if (maxProcesos < 1) {
            throw new IllegalArgumentException("maxProcesos debe ser al menos 1");
        }
        if (maxArchivos < 1) {
            throw new IllegalArgumentException("maxArchivos debe ser al menos 1");
        }
        if (limiteKernelUsuario > tamanoMemoria) {
            throw new IllegalArgumentException(
                "El limite kernel/usuario no puede superar el tamano total");
        }

        // Cuantos BCPs caben en el kernel configurado
        int bcpsPosibles = calcularBCPsQueCaben(limiteKernelUsuario, maxProcesos, maxArchivos);

        if (bcpsPosibles < 1) {
            throw new IllegalArgumentException(
                "El kernel no alcanza ni para 1 BCP.\n"
                + "  Kernel configurado: " + limiteKernelUsuario + " posiciones.\n"
                + "  Minimo requerido: " + getTamanoKernelMinimo(1, maxArchivos) + " posiciones "
                + "(ListaDeTrabajos=" + (maxArchivos * POSICIONES_POR_ENTRADA_LISTA_TRABAJOS)
                + " + 1 BCP=" + BCP.POSICIONES_REQUERIDAS
                + " + TablaMemoria=" + POSICIONES_POR_BLOQUE_TABLA_MEMORIA + ").");
        }

        this.tamanoMemoria = tamanoMemoria;
        this.limiteKernelUsuario = limiteKernelUsuario;
        this.maxProcesos = maxProcesos;
        this.maxArchivos = maxArchivos;
        this.bcpsQueCaben = Math.min(bcpsPosibles, maxProcesos);
        this.arregloMemoria = new Object[tamanoMemoria];

        // Dimensionar zonas
        int tamanoBCPs = bcpsQueCaben * BCP.POSICIONES_REQUERIDAS;
        this.tamanoTablaMemoria = bcpsQueCaben * POSICIONES_POR_BLOQUE_TABLA_MEMORIA;
        this.tamanoListaTrabajos = maxArchivos * POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;

        // Calcular limites (ListaDeTrabajos PRIMERO)
        this.inicioListaTrabajos = 0;
        this.finListaTrabajos = inicioListaTrabajos + tamanoListaTrabajos - 1;

        this.inicioBCPs = finListaTrabajos + 1;
        this.finBCPs = inicioBCPs + tamanoBCPs - 1;

        this.inicioTablaMemoria = finBCPs + 1;
        this.finTablaMemoria = inicioTablaMemoria + tamanoTablaMemoria - 1;

        // Verificar que la TablaMemoria no se salga del kernel
        if (finTablaMemoria >= limiteKernelUsuario) {
            throw new IllegalArgumentException(
                "Error interno: la TablaMemoria (hasta posicion " + finTablaMemoria
                + ") no cabe en el kernel (limite " + limiteKernelUsuario + ").");
        }
    }

    /* ==================== CALCULO DE BCPs QUE CABEN ==================== */

    /**
     * Calcula cuantos BCPs caben en un kernel de tamano dado.
     * Reserva espacio para:
     *   - ListaDeTrabajos (maxArchivos × 4)
     *   - BCPs (n × 30)
     *   - TablaMemoria (n × 3)
     *
     * @param tamanoKernel tamano del kernel configurado
     * @param maxProcesos  cantidad maxima deseada de procesos
     * @param maxArchivos  cantidad maxima de archivos
     * @return cantidad de BCPs que caben (0 si no cabe ni 1)
     */
    public static int calcularBCPsQueCaben(int tamanoKernel, int maxProcesos, int maxArchivos) {
        int tamanoListaTrabajos = maxArchivos * POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;

        for (int n = Math.min(maxProcesos, MAX_BCPS); n >= 1; n--) {
            int tamanoBCPs = n * BCP.POSICIONES_REQUERIDAS;
            int tamanoTablaMem = n * POSICIONES_POR_BLOQUE_TABLA_MEMORIA;
            int total = tamanoListaTrabajos + tamanoBCPs + tamanoTablaMem;

            if (total <= tamanoKernel) {
                return n;
            }
        }
        return 0;
    }

    /**
     * @return el tamano minimo del kernel para UNA cantidad dada de BCPs.
     */
    public static int getTamanoKernelMinimo(int maxProcesos, int maxArchivos) {
        return (maxArchivos * POSICIONES_POR_ENTRADA_LISTA_TRABAJOS)
             + (maxProcesos * BCP.POSICIONES_REQUERIDAS)
             + (maxProcesos * POSICIONES_POR_BLOQUE_TABLA_MEMORIA);
    }

    /* ==================== ACCESO GENERICO ==================== */

    // Escribe un valor en una posición de la memoria.
    public void escribir(int posicion, Object valor) {
        arregloMemoria[posicion] = valor;
    }

    // Lee el valor de una posición de la memoria.
    public Object leer(int posicion) {
        return arregloMemoria[posicion];
    }

    // Lee una instrucción de una posición, validando el tipo.
    public Instruccion leerInstruccion(int posicion) {
        Object valor = arregloMemoria[posicion];
        if (valor == null) return null;
        if (!(valor instanceof Instruccion)) {
            throw new ClassCastException(
                "La posicion " + posicion + " no contiene una Instruccion, sino "
                + valor.getClass().getSimpleName());
        }
        return (Instruccion) valor;
    }

    // Lee un BCP de una posición, validando el tipo.
    public BCP leerBCP(int posicion) {
        Object valor = arregloMemoria[posicion];
        if (valor == null) return null;
        if (!(valor instanceof BCP)) {
            throw new ClassCastException(
                "La posicion " + posicion + " no contiene un BCP, sino "
                + valor.getClass().getSimpleName());
        }
        return (BCP) valor;
    }

    /* ==================== ZONA 1: LISTA DE TRABAJOS ==================== */

    public int getInicioListaTrabajos() { return inicioListaTrabajos; }
    public int getFinListaTrabajos()    { return finListaTrabajos; }
    public int getTamanoListaTrabajos() { return tamanoListaTrabajos; }

    public int getMaxArchivos() { return maxArchivos; }

    /**
     * Escribe el valor de un campo de una entrada de la ListaDeTrabajos.
     *
     * @param entrada indice de la entrada (0 a maxArchivos-1)
     * @param campo   0=nombre, 1=inicio, 2=fin, 3=zona
     * @param valor   valor a escribir
     */
    public void escribirListaTrabajos(int entrada, int campo, Object valor) {
        if (entrada < 0 || entrada >= maxArchivos) {
            throw new IndexOutOfBoundsException("Entrada fuera de ListaDeTrabajos: " + entrada);
        }
        if (campo < 0 || campo >= POSICIONES_POR_ENTRADA_LISTA_TRABAJOS) {
            throw new IndexOutOfBoundsException("Campo invalido: " + campo);
        }
        arregloMemoria[inicioListaTrabajos + entrada * POSICIONES_POR_ENTRADA_LISTA_TRABAJOS + campo] = valor;
    }

    /**
     * Lee el valor de un campo de una entrada de la ListaDeTrabajos.
     */
    public Object leerListaTrabajos(int entrada, int campo) {
        if (entrada < 0 || entrada >= maxArchivos) {
            throw new IndexOutOfBoundsException("Entrada fuera de ListaDeTrabajos: " + entrada);
        }
        if (campo < 0 || campo >= POSICIONES_POR_ENTRADA_LISTA_TRABAJOS) {
            throw new IndexOutOfBoundsException("Campo invalido: " + campo);
        }
        return arregloMemoria[inicioListaTrabajos + entrada * POSICIONES_POR_ENTRADA_LISTA_TRABAJOS + campo];
    }

    /**
     * Busca la primera entrada libre en la ListaDeTrabajos.
     * Una entrada se considera libre si su campo 0 (nombre) es null.
     *
     * @return el indice de la entrada libre, o -1 si esta llena
     */
    public int buscarListaTrabajosLibre() {
        for (int i = 0; i < maxArchivos; i++) {
            Object nombre = leerListaTrabajos(i, 0);
            if (nombre == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Cuenta cuantas entradas no vacias hay en la ListaDeTrabajos.
     */
    public int contarListaTrabajos() {
        int contador = 0;
        for (int i = 0; i < maxArchivos; i++) {
            if (leerListaTrabajos(i, 0) != null) {
                contador++;
            }
        }
        return contador;
    }

    /* ==================== ZONA 2: BCPs ==================== */

    public int getInicioBCPs() { return inicioBCPs; }
    public int getFinBCPs()    { return finBCPs; }
    public int getMaxProcesos() { return maxProcesos; }
    public int getBcpsQueCaben() { return bcpsQueCaben; }

    // Busca un bloque contiguo libre para un BCP, o -1 si no hay.
    public int reservarBloqueBCP() {
        for (int inicio = inicioBCPs;
             inicio + BCP.POSICIONES_REQUERIDAS - 1 <= finBCPs;
             inicio += BCP.POSICIONES_REQUERIDAS) {
            boolean libre = true;
            for (int i = 0; i < BCP.POSICIONES_REQUERIDAS; i++) {
                if (arregloMemoria[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) return inicio;
        }
        return -1;
    }

    // Libera el bloque de un BCP (marca sus posiciones como null).
    public void liberarBloqueBCP(int direccionBase) {
        for (int i = 0; i < BCP.POSICIONES_REQUERIDAS; i++) {
            arregloMemoria[direccionBase + i] = null;
        }
    }

    /* ==================== ZONA 3: TABLA DE MEMORIA ==================== */

    public int getInicioTablaMemoria() { return inicioTablaMemoria; }
    public int getFinTablaMemoria()    { return finTablaMemoria; }
    public int getTamanoTablaMemoria() { return tamanoTablaMemoria; }

    // Escribe un valor en la TablaMemoria por índice lógico.
    public void escribirTablaMemoria(int i, Object valor) {
        if (i < 0 || i >= tamanoTablaMemoria) {
            throw new IndexOutOfBoundsException("Indice fuera de TablaMemoria: " + i);
        }
        arregloMemoria[inicioTablaMemoria + i] = valor;
    }

    // Lee un valor de la TablaMemoria por índice lógico.
    public Object leerTablaMemoria(int i) {
        if (i < 0 || i >= tamanoTablaMemoria) {
            throw new IndexOutOfBoundsException("Indice fuera de TablaMemoria: " + i);
        }
        return arregloMemoria[inicioTablaMemoria + i];
    }

    /* ==================== ZONA USUARIO ==================== */

    // Busca un bloque contiguo libre en la zona usuario, o -1 si no hay.
    public int reservarBloqueUsuario(int tamano) {
        if (tamano <= 0) {
            throw new IllegalArgumentException("El tamano del bloque debe ser > 0");
        }
        for (int inicio = limiteKernelUsuario; inicio <= tamanoMemoria - tamano; inicio++) {
            boolean libre = true;
            for (int i = 0; i < tamano; i++) {
                if (arregloMemoria[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) return inicio;
        }
        return -1;
    }

    // Libera un bloque de la zona usuario (marca sus posiciones como null).
    public void liberarBloqueUsuario(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            arregloMemoria[direccionBase + i] = null;
        }
    }

    /* ==================== CONSULTAS ==================== */

    // Indica si una posición pertenece a la zona kernel.
    public boolean esZonaKernel(int posicion) {
        return posicion < limiteKernelUsuario;
    }

    // Indica si un programa de N posiciones cabe en la zona usuario.
    public boolean cabeProgramaDeUsuario(int cantidadPosiciones) {
        return cantidadPosiciones <= getEspacioUsuarioDisponible();
    }

    public int getTamanoMemoria() { return tamanoMemoria; }
    public int getLimiteKernelUsuario() { return limiteKernelUsuario; }
    public Object[] getArregloMemoria() { return arregloMemoria; }

    // Devuelve cuántas posiciones tiene la zona usuario.
    public int getEspacioUsuarioDisponible() {
        return tamanoMemoria - limiteKernelUsuario;
    }

    // Devuelve cuántas posiciones tiene la zona kernel.
    public int getTamanoKernel() {
        return limiteKernelUsuario;
    }

    /** @return cuantos procesos mas pueden entrar a RAM ahora mismo. */
    public int getBCPsLibres() {
        int libres = 0;
        for (int inicio = inicioBCPs;
             inicio + BCP.POSICIONES_REQUERIDAS - 1 <= finBCPs;
             inicio += BCP.POSICIONES_REQUERIDAS) {
            boolean vacio = true;
            for (int i = 0; i < BCP.POSICIONES_REQUERIDAS; i++) {
                if (arregloMemoria[inicio + i] != null) {
                    vacio = false;
                    break;
                }
            }
            if (vacio) libres++;
        }
        return libres;
    }
    
    /**
    * Calcula cuantos maxArchivos caben con un kernel dado.
    *
    * @param tamanoKernel tamano del kernel
    * @return cantidad maxima de archivos que caben (>= 1)
    */
   public static int calcularMaxArchivosQueCaben(int tamanoKernel) {
       int espacioParaLista = tamanoKernel
               - BCP.POSICIONES_REQUERIDAS
               - POSICIONES_POR_BLOQUE_TABLA_MEMORIA;

       if (espacioParaLista < POSICIONES_POR_ENTRADA_LISTA_TRABAJOS) {
           return 1;   // minimo 1 archivo
       }

       return espacioParaLista / POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;
   }

    @Override
    public String toString() {
        return "Memoria[" + tamanoMemoria + " pos, kernel=0-" + (limiteKernelUsuario - 1)
                + ", usuario=" + limiteKernelUsuario + "-" + (tamanoMemoria - 1)
                + ", BCPs=" + bcpsQueCaben + "/" + maxProcesos
                + ", ListaTrab=" + maxArchivos + "]";
    }
}