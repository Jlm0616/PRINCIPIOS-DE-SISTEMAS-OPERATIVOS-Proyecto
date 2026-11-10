package gui;

import config.ConfiguracionExterna;
import logica.GestorProcesos;
import logica.ListaProcesos;
import logica.ListaDeTrabajos;
import logica.memoria.EstrategiaMemoria;
import logica.memoria.FabricaMemoria;
import logica.memoria.ParametrosMemoria;
import logica.ResultadoCarga;
import logica.BCPTerminado;
import logica.estadisticas.Estadisticas;
import logica.estadisticas.LineaProceso;
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
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.FileDialog;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ventana principal de la aplicación: arma la UI y orquesta todo el sistema.
public class VentanaPrincipal extends JFrame {

    private ConfiguracionExterna config;

    // Valores actuales
    private int tamanoMemoriaActual;
    private int tamanoDiscoActual;
    private int maxArchivosActual;
    private int tamanoSwapActual;
    private int limiteKernelActual;
    private int cantidadCPUsActual;
    private String algoritmoCPUActual;
    private int quantumRRActual;
    private int srrAActual;
    private int srrBActual;
    private ParametrosMemoria parametrosMemoriaActual = ParametrosMemoria.porDefecto();

    private Memoria memoria;
    private CPU cpu;
    private ListaProcesos listaProcesos;
    private ListaDeTrabajos listaDeTrabajos;
    private EstrategiaMemoria particionador;   // estrategia de asignacion de memoria
    private GestorProcesos gestor;
    private Disco disco;

    private PanelProcesos panelProcesos;
    private PanelMemoria panelMemoria;
    private PanelDisco panelDisco;
    private PanelPantalla panelPantalla;
    private PanelPantalla panelContexto;   // bitacora de cambios de contexto
    private PanelGanttVivo panelGantt;

    // Alto inicial del Gantt en vivo: unas 6 filas. Con mas filas aparece el
    // scroll, y el divisor se puede arrastrar para darle mas espacio.
    private static final int ALTO_INICIAL_GANTT = 230;

    private JButton btnCargar;
    private JButton btnEjecutar;
    private JButton btnPasoAPaso;
    private JButton btnConfigurar;
    private JButton btnLimpiar;
    private JButton btnEstadisticas;

    // Label para el reloj global
    private JLabel lblRelojGlobal;

    private SwingWorker<Integer, Void> workerAutomatico;

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

        try {
            inicializarSistema();
        } catch (ErrorAsignacionMemoria e) {
            // La estrategia de config.txt no sirve con esta memoria (por
            // ejemplo, una particion mas grande que la zona de usuario).
            JOptionPane.showMessageDialog(this,
                    e.getMessage() + "\n\nSe usa " + ParametrosMemoria.ESTRATEGIA_DEFAULT
                    + ". Puedes cambiarlo en 'Configurar'.",
                    "Asignacion de memoria", JOptionPane.WARNING_MESSAGE);
            parametrosMemoriaActual = ParametrosMemoria.porDefecto();
            inicializarSistema();
        }
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
        this.tamanoDiscoActual = config.getDisco();
        this.maxArchivosActual = config.getMaxArchivos();
        this.tamanoSwapActual = config.getMemoriaVirtual();
        this.cantidadCPUsActual = config.getCpus();
        this.algoritmoCPUActual = config.getAlgoritmoCPU();
        this.quantumRRActual = config.getQuantum();
        this.srrAActual = config.getSrrA();
        this.srrBActual = config.getSrrB();
        this.parametrosMemoriaActual = config.getParametrosMemoria();
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
     * La estrategia de memoria elegida no sirve con esta memoria (por
     * ejemplo, una particion o una pagina mas grande que la zona de usuario).
     */
    private static class ErrorAsignacionMemoria extends IllegalArgumentException {
        ErrorAsignacionMemoria(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Inicializa el sistema con la configuracion actual.
     */
    private void inicializarSistema() {
        try {
            // El limite de procesos ahora es 5 por cada CPU configurada
            int maxProcesosReales = cantidadCPUsActual * 5;

            Memoria nuevaMemoria = new Memoria(tamanoMemoriaActual, limiteKernelActual,
                                  maxProcesosReales, maxArchivosActual);

            // Estrategia de asignacion de la memoria de usuario elegida en la
            // configuracion (First Fit, Next Fit, Best Fit, particiones fijas,
            // paginacion o paginacion virtual). Se crea antes de reemplazar
            // nada: si sus parametros no sirven, el sistema anterior sigue igual.
            EstrategiaMemoria nuevaEstrategia;
            try {
                nuevaEstrategia = FabricaMemoria.crear(parametrosMemoriaActual, nuevaMemoria);
            } catch (IllegalArgumentException e) {
                throw new ErrorAsignacionMemoria("Asignacion de memoria ("
                        + parametrosMemoriaActual.describir() + "): " + e.getMessage());
            }

            memoria = nuevaMemoria;
            particionador = nuevaEstrategia;
            cpu = new CPU(limiteKernelActual);
            disco = new Disco(tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

            listaProcesos = new ListaProcesos();
            listaDeTrabajos = new ListaDeTrabajos(memoria);

            // Se pasa la cantidad de CPUs, el algoritmo, el quantum y a, b de
            // Selfish RR al GestorProcesos
            gestor = new GestorProcesos(memoria, cpu, cantidadCPUsActual, algoritmoCPUActual,
                                        listaProcesos, listaDeTrabajos,
                                        particionador, disco, quantumRRActual,
                                        srrAActual, srrBActual);

            // Reiniciar el balanceador al crear el sistema
            gestor.reiniciarBalanceador();

        } catch (ErrorAsignacionMemoria e) {
            throw e;   // lo explica quien llamo (arranque o 'Configurar')
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

    // Arma los paneles principales (encabezado y zona central).
    private void inicializarComponentes() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        mainPanel.add(crearEncabezado(), BorderLayout.NORTH);

        panelProcesos = new PanelProcesos();
        panelMemoria = new PanelMemoria();
        panelDisco = new PanelDisco();
        panelPantalla = new PanelPantalla();
        panelContexto = new PanelPantalla("Cambios de contexto");
        panelGantt = new PanelGanttVivo();

        // Pantalla y cambios de contexto con altura fija, mas baja que la
        // original para dejar lugar al Gantt sin quitarselo a Memoria y Disco.
        // El ancho lo decide el layout.
        int alturaPantalla = 160;
        panelPantalla.setPreferredSize(new Dimension(1, alturaPantalla));
        panelContexto.setPreferredSize(new Dimension(1, alturaPantalla));

        // Anchos preferidos minimos para que las 3 columnas queden iguales (1/3 c/u)
        panelProcesos.setPreferredSize(new Dimension(1, 1));
        panelMemoria.setPreferredSize(new Dimension(1, 1));
        panelDisco.setPreferredSize(new Dimension(1, 1));

        JPanel centro = new JPanel(new GridBagLayout());
        centro.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // Procesos + BCP: columna izquierda, ocupa las 2 filas (toda la altura)
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridheight = 2; gbc.gridwidth = 1;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 0, 10);
        centro.add(panelProcesos, gbc);

        // Memoria principal: fila de arriba, columna central
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.gridheight = 1; gbc.gridwidth = 1;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 10, 10);
        centro.add(panelMemoria, gbc);

        // Disco: fila de arriba, columna derecha
        gbc.gridx = 2; gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);
        centro.add(panelDisco, gbc);

        // Pantalla y cambios de contexto: fila de abajo, debajo de Memoria y
        // Disco respectivamente (altura fija)
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, 0, 10);
        centro.add(panelPantalla, gbc);

        gbc.gridx = 2; gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        centro.add(panelContexto, gbc);

        // Diagrama de Gantt en vivo: abajo, a todo lo ancho. El divisor deja
        // ajustar cuanto alto le toca.
        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centro, panelGantt);
        // Al agrandar la ventana, el espacio extra va a Memoria y Disco.
        division.setResizeWeight(1.0);
        // La primera vez que la division tiene tamano, el Gantt recibe su alto
        // inicial (el divisor se calcula desde arriba, no desde el Gantt).
        division.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (division.getHeight() <= 0) return;
                division.setDividerLocation(Math.max(300,
                        division.getHeight() - ALTO_INICIAL_GANTT - division.getDividerSize()));
                division.removeComponentListener(this);
            }
        });
        division.setContinuousLayout(true);
        division.setBorder(null);
        division.setOpaque(false);
        panelGantt.setMinimumSize(new Dimension(1, 120));
        centro.setMinimumSize(new Dimension(1, 300));

        mainPanel.add(division, BorderLayout.CENTER);

        configurarCallbacks();
    }

    // Conecta los callbacks del gestor con los paneles de la GUI.
    private void configurarCallbacks() {
        gestor.setSalidaPantalla(mensaje -> panelPantalla.agregarMensaje(mensaje));

        // Los cambios de contexto pueden llegar desde el hilo de la ejecucion
        // automatica: se pasan al hilo de Swing en el mismo orden.
        gestor.setBitacoraContexto(mensaje ->
                SwingUtilities.invokeLater(() -> panelContexto.agregarMensaje(mensaje)));

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

        // Callback unificado: tiempos de llegada + prioridades
        panelProcesos.setOnTiemposYPrioridadesCambiados((tiempos, prioridades) -> {
            gestor.actualizarTiemposLlegada(tiempos);
            gestor.actualizarPrioridades(prioridades);
            refrescarTodo();
        });
    }

    // Crea el encabezado con el título, el reloj global y los botones de acción.
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

        // === RELOJ GLOBAL ===
        lblRelojGlobal = new JLabel("Reloj Global: 0 s");
        lblRelojGlobal.setFont(Paleta.FUENTE_TITULO_PANEL);
        lblRelojGlobal.setForeground(Paleta.TEXTO_CLARO);
        panelTitulo.add(lblRelojGlobal, BorderLayout.EAST);

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
        int enNuevo = 0;
        int conError = 0;

        for (File archivo : archivos) {
            int resultado = cargarUnArchivo(archivo);
            switch (resultado) {
                case 1: cargadosExito++; break;
                case 0: conError++; break;
                case -1: enNuevo++; break;
            }
        }

        if (archivos.length > 1) {
            panelPantalla.agregarMensaje("");
            panelPantalla.agregarMensaje(">> ===== RESUMEN DE CARGA =====");
            panelPantalla.agregarMensaje(">> Archivos seleccionados: " + archivos.length);
            panelPantalla.agregarMensaje(">> Cargados en RAM:        " + cargadosExito);
            panelPantalla.agregarMensaje(">> En NEW (swap):          " + enNuevo);
            panelPantalla.agregarMensaje(">> Con error:              " + conError);
            panelPantalla.agregarMensaje(">> ============================");
            panelPantalla.agregarMensaje("");
        }

        refrescarTodo();
    }

    // Carga un único archivo .asm. Devuelve 1=OK (en RAM), 0=error, -1=en NEW (swap).
    private int cargarUnArchivo(File archivo) {
        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            JOptionPane.showMessageDialog(this,
                    "Se omite \"" + archivo.getName() + "\": no es un archivo .asm",
                    "Formato incorrecto", JOptionPane.WARNING_MESSAGE);
            return 0;
        }

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
                // El proceso queda en NEW (guardado en swap, sin BCP). El
                // gestor ya explico en pantalla el motivo (llega en el futuro
                // o no hay hueco en RAM), asi que no se repite aqui.
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

        // Hacemos un paso del sistema (avanza el reloj global y ejecuta)
        gestor.ejecutarUnPaso();

        // Refrescamos los paneles (incluyendo el reloj)
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

                    // Si la ejecucion termino por un error (timeout, excepcion,
                    // cancelacion), avisar al usuario.
                    String error = gestor.getErrorEjecucion();
                    if (error != null) {
                        panelPantalla.agregarMensaje(">> ERROR DE EJECUCION: " + error);
                        JOptionPane.showMessageDialog(VentanaPrincipal.this,
                                "La ejecucion termino por un error:\n\n" + error
                                + "\n\nLas estadisticas parciales siguen disponibles en el boton"
                                + " 'Estadisticas'.",
                                "Error de ejecucion", JOptionPane.ERROR_MESSAGE);
                    }

                    imprimirEstadisticasEnPantalla();
                } catch (java.util.concurrent.CancellationException ex) {
                    panelPantalla.agregarMensaje(">> Ejecucion automatica cancelada.");
                    // La cancelacion del SwingWorker NO llena errorEjecucion
                    // en el gestor: lo marcamos aqui para que la ventana de
                    // estadisticas lo sepa.
                    gestor.setErrorEjecucionExterno("Ejecucion cancelada por el usuario.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VentanaPrincipal.this,
                            "Error durante la ejecucion:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    gestor.setErrorEjecucionExterno("Error en SwingWorker: "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
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

        int viejoTamano = this.tamanoMemoriaActual;
        int viejoKernel = this.limiteKernelActual;
        int viejoDisco = this.tamanoDiscoActual;
        int viejoMaxArchivos = this.maxArchivosActual;
        int viejoSwap = this.tamanoSwapActual;
        int viejoCPUs = this.cantidadCPUsActual;
        String viejoAlgoritmo = this.algoritmoCPUActual;
        int viejoQuantum = this.quantumRRActual;
        int viejoSrrA = this.srrAActual;
        int viejoSrrB = this.srrBActual;
        ParametrosMemoria viejosParametrosMemoria = this.parametrosMemoriaActual;

        VentanaConfiguracion dialogo = new VentanaConfiguracion(
                this,
                tamanoMemoriaActual,
                limiteKernelActual,
                tamanoDiscoActual,
                maxArchivosActual,
                tamanoSwapActual,
                cantidadCPUsActual,
                algoritmoCPUActual,
                quantumRRActual,
                srrAActual,
                srrBActual,
                parametrosMemoriaActual);

        dialogo.setVisible(true);

        if (!dialogo.isConfirmado()) return;

        int nuevoTamano = dialogo.getTamanoMemoria();
        int nuevoKernel = dialogo.getLimiteKernel();
        int nuevoDisco  = dialogo.getTamanoDisco();
        int nuevoMaxArchivos = dialogo.getMaxArchivos();
        int nuevoSwap   = dialogo.getTamanoSwap();
        int nuevoCPUs   = dialogo.getCantidadCPUs();
        String nuevoAlgoritmo = dialogo.getAlgoritmoCPU();
        int nuevoQuantum = dialogo.getQuantum();
        int nuevoSrrA = dialogo.getSrrA();
        int nuevoSrrB = dialogo.getSrrB();
        ParametrosMemoria nuevosParametrosMemoria = dialogo.getParametrosMemoria();

        this.tamanoMemoriaActual = nuevoTamano;
        this.limiteKernelActual = nuevoKernel;
        this.tamanoDiscoActual = nuevoDisco;
        this.maxArchivosActual = nuevoMaxArchivos;
        this.tamanoSwapActual = nuevoSwap;
        this.cantidadCPUsActual = nuevoCPUs;
        this.algoritmoCPUActual = nuevoAlgoritmo;
        this.quantumRRActual = nuevoQuantum;
        this.srrAActual = nuevoSrrA;
        this.srrBActual = nuevoSrrB;
        this.parametrosMemoriaActual = nuevosParametrosMemoria;

        try {
            inicializarSistema();
        } catch (IllegalArgumentException e) {
            this.tamanoMemoriaActual = viejoTamano;
            this.limiteKernelActual = viejoKernel;
            this.tamanoDiscoActual = viejoDisco;
            this.maxArchivosActual = viejoMaxArchivos;
            this.tamanoSwapActual = viejoSwap;
            this.cantidadCPUsActual = viejoCPUs;
            this.algoritmoCPUActual = viejoAlgoritmo;
            this.quantumRRActual = viejoQuantum;
            this.srrAActual = viejoSrrA;
            this.srrBActual = viejoSrrB;
            this.parametrosMemoriaActual = viejosParametrosMemoria;

            JOptionPane.showMessageDialog(this,
                    "La configuracion no es valida:\n\n"
                    + e.getMessage() + "\n\n"
                    + "Se mantiene la configuracion anterior.",
                    "Error de configuracion",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        config.setMemoria(nuevoTamano);
        config.setKernel(nuevoKernel);
        config.setDisco(nuevoDisco);
        config.setMaxArchivos(nuevoMaxArchivos);
        config.setMemoriaVirtual(nuevoSwap);
        config.setCpus(nuevoCPUs);
        config.setAlgoritmoCPU(nuevoAlgoritmo);
        config.setQuantum(nuevoQuantum);
        config.setSrrA(nuevoSrrA);
        config.setSrrB(nuevoSrrB);
        config.setParametrosMemoria(nuevosParametrosMemoria);
        config.guardar();

        configurarCallbacks();
        panelPantalla.limpiar();
        panelContexto.limpiar();
        mostrarMensajeConfiguracion();
        refrescarTodo();
    }

    // Reinicia el sistema con la configuración actual.
    private void limpiar() {
        inicializarSistema();
        // Al reiniciar el sistema, el error de la ejecucion anterior ya no
        // aplica: se limpia para que la proxima ejecucion empiece en blanco.
        gestor.limpiarErrorEjecucion();
        configurarCallbacks();
        panelPantalla.limpiar();
        panelContexto.limpiar();
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
                + " | Algoritmo: " + algoritmoCPUActual + parametrosAlgoritmo());
        panelPantalla.agregarMensaje(">>   Asignacion de memoria: " + parametrosMemoriaActual.describir());
        panelPantalla.agregarMensaje(">>     " + particionador.resumen());
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

    /**
     * Texto con los parametros del algoritmo activo, para los mensajes
     * de la pantalla: quantum en RR y Lottery; quantum, a y b en Selfish RR;
     * quantums por nivel en MLFQ.
     */
    private String parametrosAlgoritmo() {
        if ("RR".equalsIgnoreCase(algoritmoCPUActual)) {
            return " (quantum=" + quantumRRActual + ")";
        }
        if ("Selfish RR".equalsIgnoreCase(algoritmoCPUActual)) {
            return " (quantum=" + quantumRRActual + ", a=" + srrAActual
                    + ", b=" + srrBActual + ")";
        }
        if ("Lottery".equalsIgnoreCase(algoritmoCPUActual)) {
            return " (quantum=" + quantumRRActual + ")";
        }
        if ("MLFQ".equalsIgnoreCase(algoritmoCPUActual)) {
            return " (Q1=1, Q2=2, Q3=4)";
        }
        return "";
    }

    /**
     * Imprime en el panel de pantalla el resumen de procesos terminados.
     *
     * PROYECTO 2 - SOLID:
     *  - Delega TODO el calculo a {@link Estadisticas}.
     *  - Solo formatea e imprime (SRP).
     *  - Columnas: Proceso, Inicio, Fin, Duracion, Turnaround, Tr/Ts.
     */
    private void imprimirEstadisticasEnPantalla() {
        List<BCPTerminado> terminados = gestor.getProcesosTerminados();
        if (terminados.isEmpty()) return;

        // Delegacion del calculo (DIP: dependemos de la abstraccion Estadisticas)
        Estadisticas stats = new Estadisticas(terminados);

        panelPantalla.agregarMensaje("");
        panelPantalla.agregarMensaje(">> ================== ESTADISTICAS ==================");
        panelPantalla.agregarMensaje(">> Algoritmo: " + algoritmoCPUActual + parametrosAlgoritmo());
        panelPantalla.agregarMensaje("");
        panelPantalla.agregarMensaje(">> Proceso | Inicio | Fin    | Duracion | Turnaround | Tr/Ts");
        panelPantalla.agregarMensaje(">> ------------------------------------------------------");

        // Iteramos la lista de DTOs ya calculados
        for (LineaProceso lp : stats.getLineas()) {
            String inicio = formatearTiempo(lp.getInicio());
            String fin    = formatearTiempo(lp.getFin());

            panelPantalla.agregarMensaje(String.format(
                    ">> ID %-4d | %-6s | %-6s | %-8s | %-10d | %.2f",
                    lp.getId(), inicio, fin, lp.getDuracion() + " s",
                    lp.getTurnaround(), lp.getTrTs()));
        }

        panelPantalla.agregarMensaje(">> ------------------------------------------------------");
        panelPantalla.agregarMensaje(">> Total de procesos:  " + stats.getCantidadProcesos());
        panelPantalla.agregarMensaje(">> Duracion total:     " + stats.getDuracionTotal() + " s");
        panelPantalla.agregarMensaje(">> Duracion promedio:  " + stats.getDuracionPromedio() + " s");
        panelPantalla.agregarMensaje(">> Proceso mas largo:  " + stats.getDuracionMasLarga() + " s");
        panelPantalla.agregarMensaje(">> Proceso mas corto:  " + stats.getDuracionMasCorta() + " s");
        panelPantalla.agregarMensaje(String.format(">> Promedio Tr/Ts:     %.2f", stats.getPromedioTrTs()));
        panelPantalla.agregarMensaje(">> ==================================================");
        panelPantalla.agregarMensaje("");
    }

    /**
     * Formatea un instante simulado como "t=X", o "  -  " si no fue marcado.
     *
     * @param t instante del reloj simulado, o {@link BCPTerminado#TIEMPO_NO_MARCADO}
     */
    private String formatearTiempo(int t) {
        if (t == BCPTerminado.TIEMPO_NO_MARCADO) return "  -  ";
        return "t=" + t;
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

        VentanaEstadisticas ventana = new VentanaEstadisticas(this, terminados,
                algoritmoCPUActual + parametrosAlgoritmo(),
                gestor.getErrorEjecucion());
        ventana.setVisible(true);
    }

    // Refresca todos los paneles de la UI con el estado actual del sistema.
    private void refrescarTodo() {
        // Actualizar reloj global
        if (lblRelojGlobal != null) {
            lblRelojGlobal.setText("Reloj Global: " + gestor.getRelojGlobal() + " s");
        }

        // 1. Armar el mapa de IRs por CPU
        Map<Integer, Integer> irsPorCPU = new HashMap<>();
        for (Nucleo nucleo : gestor.getGestorCPUs().getNucleos()) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null) {
                    // En paginacion el IR guarda la direccion logica: se
                    // traduce a la fisica para resaltar la celda correcta.
                    int fisica = particionador.traducir(bcp.getId(), bcp.getIr());
                    if (fisica >= 0) irsPorCPU.put(fisica, nucleo.getIdNucleo());
                }
            }
        }

        // 2. Actualizar los paneles.
        //    Los procesos en NEW (sin BCP) vienen de getTrabajosNuevos().
        panelProcesos.setAlgoritmoActual(algoritmoCPUActual);
        panelProcesos.actualizarLista(
                gestor.getListaProcesos(),
                gestor.getGestorCPUs().getNucleos(),
                gestor.getProcesosBloqueadosInput(),
                gestor.getTrabajosNuevos());

        panelMemoria.actualizar(memoria, irsPorCPU, particionador);
        panelDisco.actualizar(disco);

        // 3. Diagrama de Gantt en vivo (incluye los procesos que siguen corriendo)
        panelGantt.actualizar(gestor.getRegistroGantt(),
                gestor.getGestorCPUs().getCantidadNucleos(),
                gestor.getRelojGlobal(),
                gestor::getNombreArchivo);
    }
}