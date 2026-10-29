package gui;

import config.ConfiguracionExterna;
import logica.GestorProcesos;
import logica.ListaProcesos;
import logica.ListaDeTrabajos;
import logica.ParticionadorDinamico;
import logica.ResultadoCarga;
import logica.BCPTerminado;
import logica.cpu.Nucleo;
import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Memoria;
import modelo.Disco;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.FileDialog;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ventana principal de la aplicación: arma la UI y orquesta todo el sistema.
public class VentanaPrincipal extends JFrame {

    private ConfiguracionExterna config;

    // Valores actuales
    private int tamanoMemoriaActual;
    private int maxProcesosActual;
    private int tamanoDiscoActual;
    private int maxArchivosActual;
    private int tamanoSwapActual;
    private int limiteKernelActual;
    private int cantidadCPUsActual;
    private String algoritmoCPUActual;

    private Memoria memoria;
    private CPU cpu;
    private ListaProcesos listaProcesos;
    private ListaDeTrabajos listaDeTrabajos;
    private ParticionadorDinamico particionador;
    private GestorProcesos gestor;
    private Disco disco;

    private PanelProcesos panelProcesos;
    private PanelMemoria panelMemoria;
    private PanelDisco panelDisco;
    private PanelPantalla panelPantalla;

    private JButton btnCargar;
    private JButton btnEjecutar;
    private JButton btnPasoAPaso;
    private JButton btnConfigurar;
    private JButton btnLimpiar;
    private JButton btnEstadisticas;

    private SwingWorker<Integer, Void> workerAutomatico;

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    // Crea la ventana principal, carga config y arma todo el sistema.
    public VentanaPrincipal() {
        super("Proyecto 2 de SO");

        config = new ConfiguracionExterna();
        config.cargar();
        aplicarConfiguracion(config);

        aplicarLookAndFeel();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (workerAutomatico != null && !workerAutomatico.isDone()) {
                    workerAutomatico.cancel(true);
                }
            }
        });

        inicializarSistema();
        inicializarComponentes();
        refrescarTodo();

        if (config.huboCorrecciones()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Se encontraron valores invalidos en config.txt\n"
                        + "y se corrigieron automaticamente:\n\n"
                        + config.getMensajesCorrecciones()
                        + "\nEl archivo config.txt fue actualizado.",
                        "Configuracion corregida",
                        JOptionPane.WARNING_MESSAGE);
            });
        }
    }

    // Copia los valores de la configuración externa a los campos actuales.
    private void aplicarConfiguracion(ConfiguracionExterna config) {
        this.tamanoMemoriaActual = config.getMemoria();
        this.limiteKernelActual = config.getKernel();
        this.maxProcesosActual = config.getMaxProcesos();
        this.tamanoDiscoActual = config.getDisco();
        this.maxArchivosActual = config.getMaxArchivos();
        this.tamanoSwapActual = config.getMemoriaVirtual();
        this.cantidadCPUsActual = config.getCpus();
        this.algoritmoCPUActual = config.getAlgoritmoCPU();
    }

    // Aplica el LookAndFeel Nimbus si está disponible.
    private void aplicarLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // usar default
        }
    }

    /**
     * Inicializa el sistema con la configuracion actual.
     */
    private void inicializarSistema() {
        try {
            // El limite de procesos ahora es 5 por cada CPU configurada
            int maxProcesosReales = cantidadCPUsActual * 5;
            
            memoria = new Memoria(tamanoMemoriaActual, limiteKernelActual,
                                  maxProcesosReales, maxArchivosActual);
            cpu = new CPU(limiteKernelActual);
            disco = new Disco(tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

            listaProcesos = new ListaProcesos();
            listaDeTrabajos = new ListaDeTrabajos(memoria);

            particionador = new ParticionadorDinamico(
                    memoria,
                    memoria.getLimiteKernelUsuario(),
                    memoria.getEspacioUsuarioDisponible());

            // Se pasa la cantidad de CPUs y el algoritmo al GestorProcesos
            gestor = new GestorProcesos(memoria, cpu, cantidadCPUsActual, algoritmoCPUActual,
                                        listaProcesos, listaDeTrabajos,
                                        particionador, disco);
                                        
            // Reiniciar el balanceador al crear el sistema (por si acaso)
            gestor.reiniciarBalanceador();
            
        } catch (IllegalArgumentException e) {
            int kernelMinimoParaUno = Memoria.getTamanoKernelMinimo(1, maxArchivosActual);

            JOptionPane.showMessageDialog(this,
                    "No se puede inicializar el sistema:\n\n"
                    + e.getMessage() + "\n\n"
                    + "El kernel debe ser al menos " + kernelMinimoParaUno
                    + " posiciones para 1 proceso + " + maxArchivosActual + " archivos.\n"
                    + "Con memoria=" + tamanoMemoriaActual
                    + " y kernel=" + limiteKernelActual
                    + " no alcanza.\n\n"
                    + "Edita config.txt o usa 'Configurar' para ajustar.",
                    "Error de configuracion",
                    JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    // Arma los paneles principales (encabezado, centro, pantalla).
    private void inicializarComponentes() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        mainPanel.add(crearEncabezado(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 3, 10, 10));
        centro.setOpaque(false);

        panelProcesos = new PanelProcesos();
        panelMemoria = new PanelMemoria();
        panelDisco = new PanelDisco();

        centro.add(panelProcesos);
        centro.add(panelMemoria);
        centro.add(panelDisco);
        mainPanel.add(centro, BorderLayout.CENTER);

        panelPantalla = new PanelPantalla();
        mainPanel.add(panelPantalla, BorderLayout.SOUTH);

        configurarCallbacks();
    }

    // Conecta los callbacks del gestor con los paneles de la GUI.
    private void configurarCallbacks() {
        gestor.setSalidaPantalla(mensaje -> panelPantalla.agregarMensaje(mensaje));

        gestor.setSolicitudArchivo(bcp -> {
            int ah = bcp.getAh();
            String operacion;
            switch (ah) {
                case 0x3C: operacion = "crear"; break;
                case 0x3D: operacion = "abrir"; break;
                case 0x4D: operacion = "leer"; break;
                case 0x40: operacion = "escribir"; break;
                case 0x41: operacion = "eliminar"; break;
                default:   operacion = "desconocida"; break;
            }
            panelPantalla.agregarMensaje(">> [DISCO] Proceso " + bcp.getId()
                    + " -> " + operacion + " \"" + bcp.getDx() + "\"");
        });
    }

    // Crea el encabezado con el título y los botones de acción.
    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);

        JPanel panelTitulo = new JPanel(new BorderLayout());
        panelTitulo.setBackground(Paleta.FONDO_TITULO);
        panelTitulo.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel lblTitulo = new JLabel("Proyecto 2 de SO");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.TEXTO_CLARO);
        panelTitulo.add(lblTitulo, BorderLayout.WEST);

        encabezado.add(panelTitulo, BorderLayout.NORTH);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panelBotones.setBackground(Paleta.FONDO_PANEL);
        panelBotones.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, Paleta.VERDE_OSCURO));

        btnCargar       = crearBoton("Cargar archivos", Paleta.VERDE_PRINCIPAL);
        btnEjecutar     = crearBoton("Ejecutar", Paleta.VERDE_PRINCIPAL);
        btnPasoAPaso    = crearBoton("Paso a paso", Paleta.MORADO_ACENTO);
        btnConfigurar   = crearBoton("Configurar", Paleta.AMARILLO_ADVERTENCIA);
        btnLimpiar      = crearBoton("Limpiar", Paleta.ROJO_PELIGRO);
        btnEstadisticas = crearBoton("Estadisticas", Paleta.AMARILLO_ADVERTENCIA);

        panelBotones.add(btnCargar);
        panelBotones.add(btnEjecutar);
        panelBotones.add(btnPasoAPaso);
        panelBotones.add(btnConfigurar);
        panelBotones.add(btnLimpiar);
        panelBotones.add(btnEstadisticas);

        btnCargar.addActionListener(e -> cargarArchivo());
        btnEjecutar.addActionListener(e -> ejecutarAutomatico());
        btnPasoAPaso.addActionListener(e -> ejecutarUnPaso());
        btnConfigurar.addActionListener(e -> abrirConfiguracion());
        btnLimpiar.addActionListener(e -> limpiar());
        btnEstadisticas.addActionListener(e -> mostrarEstadisticas());

        encabezado.add(panelBotones, BorderLayout.SOUTH);
        return encabezado;
    }

    // Crea un botón con el estilo uniforme de la aplicación.
    private JButton crearBoton(String texto, Color color) {
        JButton boton = new JButton(texto);
        boton.setPreferredSize(new Dimension(150, 36));
        boton.setFont(Paleta.FUENTE_BOTON);
        boton.setBackground(color);
        boton.setForeground(Paleta.TEXTO_CLARO);
        boton.setFocusPainted(false);
        boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        boton.setUI(new javax.swing.plaf.metal.MetalButtonUI());
        boton.setOpaque(true);
        boton.setBorderPainted(false);
        return boton;
    }

    // Abre un diálogo para cargar uno o varios archivos .asm.
    private void cargarArchivo() {
        FileDialog fileDialog = new FileDialog(this, "Seleccionar archivos ASM", FileDialog.LOAD);
        fileDialog.setMultipleMode(true);
        fileDialog.setSize(900, 650);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        fileDialog.setLocation((screen.width - 900) / 2, (screen.height - 650) / 2);
        fileDialog.setFile("*.asm");
        fileDialog.setDirectory(System.getProperty("user.home"));
        fileDialog.setVisible(true);

        File[] archivos = fileDialog.getFiles();

        if (archivos == null || archivos.length == 0) return;

        int cargadosExito = 0;
        int enEspera = 0;
        int conError = 0;

        for (File archivo : archivos) {
            int resultado = cargarUnArchivo(archivo);
            switch (resultado) {
                case 1: cargadosExito++; break;
                case 0: conError++; break;
                case -1: enEspera++; break;
            }
        }

        if (archivos.length > 1) {
            panelPantalla.agregarMensaje("");
            panelPantalla.agregarMensaje(">> ===== RESUMEN DE CARGA =====");
            panelPantalla.agregarMensaje(">> Archivos seleccionados: " + archivos.length);
            panelPantalla.agregarMensaje(">> Cargados en RAM:        " + cargadosExito);
            panelPantalla.agregarMensaje(">> En espera (swap):       " + enEspera);
            panelPantalla.agregarMensaje(">> Con error:              " + conError);
            panelPantalla.agregarMensaje(">> ============================");
            panelPantalla.agregarMensaje("");
        }

        refrescarTodo();
    }

    // Carga un único archivo .asm. Devuelve 1=OK, 0=error, -1=en espera.
    private int cargarUnArchivo(File archivo) {
        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            JOptionPane.showMessageDialog(this,
                    "Se omite \"" + archivo.getName() + "\": no es un archivo .asm",
                    "Formato incorrecto", JOptionPane.WARNING_MESSAGE);
            return 0;
        }

        // El GestorProcesos ya registra el .asm en el indice ASM internamente.
        ResultadoCarga resultado = gestor.cargarPrograma(archivo);

        switch (resultado.getEstado()) {
            case EXITO:
                panelPantalla.agregarMensaje(">> Cargado: " + archivo.getName()
                        + " (ID=" + resultado.getBcp().getId() + ")");
                return 1;

            case ERROR:
                JOptionPane.showMessageDialog(this,
                        "Error al cargar \"" + archivo.getName() + "\":\n"
                        + resultado.getMensajeError(),
                        "Archivo invalido", JOptionPane.ERROR_MESSAGE);
                return 0;

            case EN_ESPERA:
                panelPantalla.agregarMensaje(">> " + archivo.getName()
                        + " en espera (no hay hueco suficiente)");
                return -1;
        }

        return 0;
    }

    // Ejecuta un único paso del gestor y refresca la UI.
    private void ejecutarUnPaso() {
        if (!gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos para ejecutar.\nCarga un archivo .asm primero.");
            return;
        }
        
        // Hacemos un paso del sistema
        gestor.ejecutarUnPaso();
        
        // Si el proceso quedó bloqueado esperando disco, 
        // hacemos pasos "fantasma" para consumir su tiempo de espera.
        boolean hayBloqueados = true;
        while (hayBloqueados) {
            hayBloqueados = false;
            for (Nucleo n : gestor.getGestorCPUs().getNucleos()) {
                if (n.estaOcupado()) {
                    BCP b = n.getBcpActual();
                    if (b != null && b.getEstado() == EstadoProceso.BLOCKED) {
                        hayBloqueados = true;
                        break;
                    }
                }
            }
            if (hayBloqueados) {
                gestor.ejecutarUnPaso(); // Avanza 1 segundo el tiempo del disco
            }
        }

        refrescarTodo();

        if (!gestor.hayProcesosActivos()) {
            imprimirEstadisticasEnPantalla();
        }
    }

    // Ejecuta el ciclo completo en un SwingWorker (sin bloquear la UI).
    private void ejecutarAutomatico() {
        if (!gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos para ejecutar.\nCarga un archivo .asm primero.");
            return;
        }

        deshabilitarBotones();

        workerAutomatico = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return gestor.ejecutarAutomatico(() -> {
                    publish((Void) null);
                });
            }

            @Override
            protected void process(List<Void> chunks) {
                refrescarTodo();
            }

            @Override
            protected void done() {
                try {
                    int pasos = get();
                    panelPantalla.agregarMensaje(">> Ejecucion automatica completada ("
                            + pasos + " pasos)");

                    imprimirEstadisticasEnPantalla();
                } catch (java.util.concurrent.CancellationException ex) {
                    panelPantalla.agregarMensaje(">> Ejecucion automatica cancelada.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VentanaPrincipal.this,
                            "Error durante la ejecucion:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    habilitarBotones();
                    refrescarTodo();
                }
            }
        };
        workerAutomatico.execute();
    }

    // Deshabilita los botones mientras corre la ejecución automática.
    private void deshabilitarBotones() {
        btnCargar.setEnabled(false);
        btnEjecutar.setEnabled(false);
        btnPasoAPaso.setEnabled(false);
        btnLimpiar.setEnabled(false);
        btnConfigurar.setEnabled(false);
    }

    // Vuelve a habilitar los botones tras la ejecución automática.
    private void habilitarBotones() {
        btnCargar.setEnabled(true);
        btnEjecutar.setEnabled(true);
        btnPasoAPaso.setEnabled(true);
        btnLimpiar.setEnabled(true);
        btnConfigurar.setEnabled(true);
    }

    // Abre la ventana de configuración y aplica los cambios si son válidos.
    private void abrirConfiguracion() {
        if (gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No se puede configurar mientras hay procesos activos.\n"
                    + "Presiona 'Limpiar' primero.",
                    "Sistema en uso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Guardar config actual para poder revertir si falla
        int viejoTamano = this.tamanoMemoriaActual;
        int viejoKernel = this.limiteKernelActual;
        int viejoDisco = this.tamanoDiscoActual;
        int viejoMaxArchivos = this.maxArchivosActual;
        int viejoSwap = this.tamanoSwapActual;
        int viejoMaxProcesos = this.maxProcesosActual;
        int viejoCPUs = this.cantidadCPUsActual;
        String viejoAlgoritmo = this.algoritmoCPUActual;

        VentanaConfiguracion dialogo = new VentanaConfiguracion(
                this,
                tamanoMemoriaActual,
                limiteKernelActual,
                tamanoDiscoActual,
                maxArchivosActual,
                tamanoSwapActual,
                maxProcesosActual,
                cantidadCPUsActual,
                algoritmoCPUActual);

        dialogo.setVisible(true);

        if (!dialogo.isConfirmado()) return;

        int nuevoTamano = dialogo.getTamanoMemoria();
        int nuevoKernel = dialogo.getLimiteKernel();
        int nuevoDisco  = dialogo.getTamanoDisco();
        int nuevoMaxArchivos = dialogo.getMaxArchivos();
        int nuevoSwap   = dialogo.getTamanoSwap();
        int nuevoCPUs   = dialogo.getCantidadCPUs();
        String nuevoAlgoritmo = dialogo.getAlgoritmoCPU();

        // Aplicar temporalmente
        this.tamanoMemoriaActual = nuevoTamano;
        this.limiteKernelActual = nuevoKernel;
        this.tamanoDiscoActual = nuevoDisco;
        this.maxArchivosActual = nuevoMaxArchivos;
        this.tamanoSwapActual = nuevoSwap;
        this.cantidadCPUsActual = nuevoCPUs;
        this.algoritmoCPUActual = nuevoAlgoritmo;

        // Intentar inicializar (valida la config)
        try {
            inicializarSistema();
        } catch (IllegalArgumentException e) {
            // Revertir a la config anterior
            this.tamanoMemoriaActual = viejoTamano;
            this.limiteKernelActual = viejoKernel;
            this.tamanoDiscoActual = viejoDisco;
            this.maxArchivosActual = viejoMaxArchivos;
            this.tamanoSwapActual = viejoSwap;
            this.maxProcesosActual = viejoMaxProcesos;
            this.cantidadCPUsActual = viejoCPUs;
            this.algoritmoCPUActual = viejoAlgoritmo;

            // NO guardar en config.txt
            JOptionPane.showMessageDialog(this,
                    "La configuracion no es valida:\n\n"
                    + e.getMessage() + "\n\n"
                    + "Se mantiene la configuracion anterior.",
                    "Error de configuracion",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Solo si inicializo bien: guardar en config.txt
        config.setMemoria(nuevoTamano);
        config.setKernel(nuevoKernel);
        config.setDisco(nuevoDisco);
        config.setMaxArchivos(nuevoMaxArchivos);
        config.setMemoriaVirtual(nuevoSwap);
        config.setCpus(nuevoCPUs);
        config.setAlgoritmoCPU(nuevoAlgoritmo);
        config.guardar();

        configurarCallbacks();
        panelPantalla.limpiar();
        mostrarMensajeConfiguracion();
        refrescarTodo();
    }

    // Reinicia el sistema con la configuración actual.
    private void limpiar() {
        inicializarSistema();
        configurarCallbacks();
        panelPantalla.limpiar();
        mostrarMensajeConfiguracion();
        refrescarTodo();
    }

    // Muestra en pantalla un resumen de la configuración aplicada.
    private void mostrarMensajeConfiguracion() {
        int procesosReales = cantidadCPUsActual * 5;

        panelPantalla.agregarMensaje(">> Configuracion aplicada:");
        panelPantalla.agregarMensaje(">>   Memoria: " + tamanoMemoriaActual + " posiciones");
        panelPantalla.agregarMensaje(">>   Kernel: 0-" + (limiteKernelActual - 1)
                + ", Usuario: " + limiteKernelActual + "-" + (tamanoMemoriaActual - 1));
        panelPantalla.agregarMensaje(">>   BCPs disponibles en kernel: "
                + memoria.getBcpsQueCaben()
                + " (maximo permitido: " + procesosReales + " = " + cantidadCPUsActual + " CPU x 5)");
        panelPantalla.agregarMensaje(">>   ListaDeTrabajos en RAM: "
                + memoria.getTamanoListaTrabajos() + " posiciones ("
                + maxArchivosActual + " entradas)");
        panelPantalla.agregarMensaje(">>   CPUs: " + cantidadCPUsActual
                + " | Algoritmo: " + algoritmoCPUActual);
        panelPantalla.agregarMensaje(">>   Disco: " + tamanoDiscoActual
                + " posiciones (maxArchivos=" + maxArchivosActual
                + ", swap=" + tamanoSwapActual + ")");
        panelPantalla.agregarMensaje(">>     Indice ASM: " + disco.getInicioIndiceAsm()
                + "-" + (disco.getInicioIndiceProceso() - 1));
        panelPantalla.agregarMensaje(">>     Indice PROCESO: " + disco.getInicioIndiceProceso()
                + "-" + (disco.getInicioSwap() - 1));
        panelPantalla.agregarMensaje(">>     Swap: " + disco.getInicioSwap()
                + "-" + (disco.getInicioArchivos() - 1));
        panelPantalla.agregarMensaje(">>     Archivos: " + disco.getInicioArchivos()
                + "-" + (tamanoDiscoActual - 1));
    }

    // Imprime en el panel de pantalla el resumen de procesos terminados.
    private void imprimirEstadisticasEnPantalla() {
        List<BCPTerminado> terminados = gestor.getProcesosTerminados();
        if (terminados.isEmpty()) return;

        panelPantalla.agregarMensaje("");
        panelPantalla.agregarMensaje(">> ========== ESTADISTICAS ==========");
        panelPantalla.agregarMensaje(">> Proceso | Inicio | Fin    | Duracion");
        panelPantalla.agregarMensaje(">> ----------------------------------");

        long duracionTotal = 0;
        for (BCPTerminado pt : terminados) {
            long dur = pt.getDuracionSegundos();
            if (dur > 0) duracionTotal += dur;

            String inicio = formatearHora(pt.getTiempoInicio());
            String fin    = formatearHora(pt.getTiempoFin());

            panelPantalla.agregarMensaje(String.format(
                    ">> ID %-4d | %s  | %s | %d s",
                    pt.getId(), inicio, fin, dur));
        }

        panelPantalla.agregarMensaje(">> ----------------------------------");
        panelPantalla.agregarMensaje(">> Total de procesos: " + terminados.size());
        panelPantalla.agregarMensaje(">> Duracion total:    " + duracionTotal + " s");
        panelPantalla.agregarMensaje(">> ==================================");
        panelPantalla.agregarMensaje("");
    }

    // Formatea un LocalDateTime como HH:mm, o "  -  " si es null.
    private String formatearHora(LocalDateTime t) {
        if (t == null) return "  -  ";
        return t.format(FORMATO_HORA);
    }

    // Abre la ventana modal de estadísticas de procesos terminados.
    private void mostrarEstadisticas() {
        List<BCPTerminado> terminados = gestor.getProcesosTerminados();
        if (terminados.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos terminados todavia.",
                    "Estadisticas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        VentanaEstadisticas ventana = new VentanaEstadisticas(this, terminados);
        ventana.setVisible(true);
    }

    // Refresca todos los paneles de la UI con el estado actual del sistema.
    private void refrescarTodo() {
        // 1. Armar el mapa de IRs por CPU
        Map<Integer, Integer> irsPorCPU = new HashMap<>();
        for (Nucleo nucleo : gestor.getGestorCPUs().getNucleos()) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null) {
                    irsPorCPU.put(bcp.getIr(), nucleo.getIdNucleo());
                }
            }
        }

        // 2. Actualizar los paneles
        panelProcesos.actualizarLista(
                gestor.getListaProcesos(),
                gestor.getGestorCPUs().getNucleos(),
                gestor.getProcesosBloqueadosInput(),
                gestor.getListaDeTrabajos());

        panelMemoria.actualizar(memoria, irsPorCPU);
        panelDisco.actualizar(disco);
    }
}