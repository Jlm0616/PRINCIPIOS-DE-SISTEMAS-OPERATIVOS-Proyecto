package gui;

import logica.BCPTerminado;
import logica.estadisticas.Estadisticas;
import logica.estadisticas.LineaProceso;
import logica.estadisticas.RegistroGantt;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.BorderFactory;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * Ventana modal de estadisticas de procesos terminados.
 *
 * Organizada en pestañas:
 *   - Error de ejecucion (si la ejecucion termino mal, ANTES de las demas).
 *   - Resumen:  totales, promedios, Tr/Ts, nombre del algoritmo, tiempo ocioso.
 *   - Procesos: tabla con todos los procesos + detalle del seleccionado.
 *   - Gantt:    diagrama de ejecucion por CPU.
 *
 * Si el calculo de {@link Estadisticas} falla (por ejemplo, porque la
 * ejecucion termino a medias y hay datos corruptos), la ventana NO
 * crashea: muestra una pestaña de "Error" con el mensaje, de modo que
 * el usuario sabe que la ejecucion termino por ese problema.
 *
 * Si la ejecucion automatica termino por un error (bucle infinito,
 * excepcion, cancelacion), el gestor lo reporta en
 * {@code getErrorEjecucion()} y la ventana agrega una pestaña
 * "Error de ejecucion" adicional. En ese caso las 3 pestañas normales
 * SI se muestran: los procesos que terminaron tienen estadisticas
 * validas.
 *
 * PROYECTO 2 - SOLID:
 *  - La ventana NO calcula estadisticas. Delega TODO a {@link Estadisticas}.
 *  - Solo formatea y muestra (SRP).
 *  - Depende de la abstraccion Estadisticas (DIP).
 *  - Cada pestaña tiene una unica responsabilidad visual (SRP).
 */
public class VentanaEstadisticas extends JDialog {

    private JTable tabla;
    private DefaultTableModel modelo;
    private Estadisticas estadisticas;

    /** Nombre del algoritmo activo (para mostrar en la ventana). */
    private final String algoritmoCPU;

    /** Si el calculo de estadisticas fallo, aqui queda el mensaje. */
    private String errorCalculo;

    /** Si la ejecucion termino por un error, aqui queda el mensaje. */
    private final String errorEjecucion;

    // Panel de detalle
    private JLabel lblId;
    private JLabel lblEstado;
    private JLabel lblInicio;
    private JLabel lblFin;
    private JLabel lblDuracion;
    private JLabel lblPorcentaje;
    private JLabel lblTurnaround;
    private JLabel lblTrTs;
    private JLabel lblCpu;
    private JLabel lblPrioridad;
    private JLabel lblBase;
    private JLabel lblAlcance;

    /**
     * Constructor legacy (sin nombre de algoritmo). Se mantiene por
     * compatibilidad: delega al constructor nuevo con algoritmo = null.
     */
    public VentanaEstadisticas(Frame propietario, List<BCPTerminado> procesos) {
        this(propietario, procesos, null, null);
    }

    /**
     * Constructor con nombre de algoritmo, sin error de ejecucion.
     * Se mantiene por compatibilidad.
     */
    public VentanaEstadisticas(Frame propietario, List<BCPTerminado> procesos,
                               String algoritmoCPU) {
        this(propietario, procesos, algoritmoCPU, null);
    }

    /**
     * Constructor con nombre de algoritmo y error de ejecucion, sin
     * datos de Gantt: el tiempo ocioso quedara en 0.
     * Se mantiene por compatibilidad.
     */
    public VentanaEstadisticas(Frame propietario, List<BCPTerminado> procesos,
                               String algoritmoCPU, String errorEjecucion) {
        this(propietario, procesos, algoritmoCPU, errorEjecucion, null, 0);
    }

    /**
     * Crea la ventana modal con la lista de procesos terminados, el
     * nombre del algoritmo activo, el error de ejecucion (si hubo) y
     * los datos del Gantt en vivo para calcular el tiempo ocioso.
     *
     * @param propietario    frame padre
     * @param procesos       lista de snapshots de procesos terminados
     * @param algoritmoCPU   nombre del algoritmo (p. ej. "MLFQ"); puede ser null
     * @param errorEjecucion mensaje si la ejecucion termino por un error; puede ser null
     * @param registro       registro del Gantt en vivo; puede ser null
     * @param cantidadNucleos cantidad de nucleos configurados (>= 0)
     */
    public VentanaEstadisticas(Frame propietario, List<BCPTerminado> procesos,
                               String algoritmoCPU, String errorEjecucion,
                               RegistroGantt registro, int cantidadNucleos) {
        super(propietario, "Estadisticas de Procesos", true);

        this.algoritmoCPU = (algoritmoCPU != null && !algoritmoCPU.isEmpty())
                ? algoritmoCPU : "(desconocido)";
        this.errorEjecucion = (errorEjecucion != null && !errorEjecucion.isEmpty())
                ? errorEjecucion : null;

        // Delegacion del calculo (DIP). Si falla, NO se rompe la ventana:
        // se guarda el mensaje y se muestra una pestaña de Error.
        try {
            this.estadisticas = new Estadisticas(procesos, registro, cantidadNucleos);
        } catch (RuntimeException ex) {
            this.estadisticas = null;
            this.errorCalculo = "La ejecucion termino con un error al calcular"
                    + " las estadisticas:\n\n" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage();
        }

        construirInterfaz();
        pack();
        setSize(1200, 850);
        setResizable(true);
        setLocationRelativeTo(propietario);
    }

    // Construye la interfaz: titulo, pestañas, boton cerrar.
    private void construirInterfaz() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(mainPanel);

        // ==== Titulo (con nombre del algoritmo) ====
        JLabel lblTitulo = new JLabel("Estadisticas de Procesos \u2014 Algoritmo: " + algoritmoCPU);
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.VERDE_OSCURO);
        mainPanel.add(lblTitulo, BorderLayout.NORTH);

        // ==== Pestañas ====
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Paleta.FUENTE_LABEL_BOLD);

        if (errorCalculo != null) {
            // Si fallo el calculo de Estadisticas, mostramos SOLO la
            // pestaña de Error de calculo: no hay datos que mostrar.
            tabs.addTab("Error", crearPestanaErrorCalculo());
        } else {
            // Si la ejecucion termino por un error, agregamos una pestaña
            // de Error de ejecucion ANTES de las pestañas normales.
            if (errorEjecucion != null) {
                tabs.addTab("Error de ejecucion", crearPestanaErrorEjecucion());
            }
            tabs.addTab("Resumen", crearPestanaResumen());
            tabs.addTab("Procesos", crearPestanaProcesos());
            tabs.addTab("Diagrama Gantt", crearPestanaGantt());
        }

        mainPanel.add(tabs, BorderLayout.CENTER);

        // ==== Boton cerrar ====
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.setFont(Paleta.FUENTE_BOTON);
        btnCerrar.setBackground(Paleta.VERDE_PRINCIPAL);
        btnCerrar.setForeground(Paleta.TEXTO_CLARO);
        btnCerrar.setFocusPainted(false);
        btnCerrar.addActionListener(e -> dispose());

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setOpaque(false);
        panelBotones.add(btnCerrar);
        mainPanel.add(panelBotones, BorderLayout.SOUTH);
    }

    /* ==================== PESTAÑA: ERROR DE CALCULO ==================== */

    // Crea la pestaña que se muestra si el calculo de estadisticas fallo.
    private JPanel crearPestanaErrorCalculo() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Paleta.FONDO_GENERAL);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel lblError = new JLabel("<html><body style='width: 800px'>"
                + "<h2 style='color:#B00020'>No se pudieron calcular las estadisticas</h2>"
                + "<p>Detalle tecnico:</p>"
                + "<pre style='background:#F8F9FA; padding:10px; border:1px solid #E0E0E0'>"
                + escaparHtml(errorCalculo)
                + "</pre>"
                + "<p>Revisa la consola para mas informacion.</p>"
                + "</body></html>");
        lblError.setFont(Paleta.FUENTE_MONO);
        lblError.setForeground(Paleta.TEXTO_NORMAL);

        JScrollPane scroll = new JScrollPane(lblError);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /* ==================== PESTAÑA: ERROR DE EJECUCION ==================== */

    // Crea la pestaña que se muestra si la ejecucion termino por un error
    // (bucle infinito, timeout, excepcion o cancelacion).
    private JPanel crearPestanaErrorEjecucion() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Paleta.FONDO_GENERAL);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel lblError = new JLabel("<html><body style='width: 800px'>"
                + "<h2 style='color:#B00020'>La ejecucion termino por un error</h2>"
                + "<p>Las estadisticas que se muestran en las otras pestañas"
                + " corresponden solo a los procesos que SI terminaron."
                + " Los demas se perdieron por el error.</p>"
                + "<p>Detalle del error:</p>"
                + "<pre style='background:#F8F9FA; padding:10px; border:1px solid #E0E0E0'>"
                + escaparHtml(errorEjecucion)
                + "</pre>"
                + "<p>Revisa la consola para mas informacion. "
                + "Si el problema persiste, revisa la configuracion del sistema "
                + "o los archivos .asm cargados.</p>"
                + "</body></html>");
        lblError.setFont(Paleta.FUENTE_MONO);
        lblError.setForeground(Paleta.TEXTO_NORMAL);

        JScrollPane scroll = new JScrollPane(lblError);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // Escapa caracteres especiales de HTML para que un mensaje de error
    // con <, >, & no rompa el JLabel.
    private String escaparHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    /* ==================== PESTAÑA: RESUMEN ==================== */

    // Crea la pestaña del resumen.
    private JPanel crearPestanaResumen() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Paleta.FONDO_GENERAL);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        panel.add(crearPanelResumen(), BorderLayout.NORTH);

        // Texto de ayuda
        JLabel lblAyuda = new JLabel("<html><body style='width: 700px'>"
                + "<h3>Resumen de la ejecución</h3>"
                + "<p>Esta pestaña muestra los totales y promedios de la simulación.</p>"
                + "<p>Cambia a <b>Procesos</b> para ver el detalle por proceso, "
                + "o a <b>Diagrama Gantt</b> para ver la ejecución en el tiempo por CPU.</p>"
                + "</body></html>");
        lblAyuda.setFont(Paleta.FUENTE_MONO);
        lblAyuda.setForeground(Paleta.TEXTO_NORMAL);
        panel.add(lblAyuda, BorderLayout.CENTER);

        return panel;
    }

    // Crea el panel de resumen con totales y promedios (de Estadisticas).
    private JPanel crearPanelResumen() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Resumen",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(15, 20, 15, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 15, 6, 15);
        gbc.weightx = 1;

        // ==== Fila 0: algoritmo + total de procesos ====
        JLabel l0 = crearLabelResumen("Algoritmo:", algoritmoCPU);
        JLabel l1 = crearLabelResumen("Total de procesos:",
                String.valueOf(estadisticas.getCantidadProcesos()));

        gbc.gridx = 0; gbc.gridy = 0; panel.add(l0, gbc);
        gbc.gridx = 1; panel.add(l1, gbc);

        // ==== Fila 1: duraciones ====
        JLabel l2 = crearLabelResumen("Duracion total:",
                estadisticas.getDuracionTotal() + " s");
        JLabel l3 = crearLabelResumen("Duracion promedio:",
                estadisticas.getDuracionPromedio() + " s");

        gbc.gridx = 0; gbc.gridy = 1; panel.add(l2, gbc);
        gbc.gridx = 1; panel.add(l3, gbc);

        // ==== Fila 2: proceso mas largo / corto ====
        JLabel l4 = crearLabelResumen("Proceso mas largo:",
                estadisticas.getDuracionMasLarga() + " s");
        JLabel l5 = crearLabelResumen("Proceso mas corto:",
                estadisticas.getDuracionMasCorta() + " s");

        gbc.gridx = 0; gbc.gridy = 2; panel.add(l4, gbc);
        gbc.gridx = 1; panel.add(l5, gbc);

        // ==== Fila 3: tiempo ocioso + promedio Tr/Ts ====
        JLabel l6 = crearLabelResumen("Tiempo ocioso:",
                estadisticas.getTiempoOcioso() + " s");
        JLabel l7 = crearLabelResumen("Promedio Tr/Ts:",
                String.format("%.2f", estadisticas.getPromedioTrTs()));

        gbc.gridx = 0; gbc.gridy = 3; panel.add(l6, gbc);
        gbc.gridx = 1; panel.add(l7, gbc);

        return panel;
    }

    // Crea una etiqueta del panel de resumen.
    private JLabel crearLabelResumen(String titulo, String valor) {
        JLabel label = new JLabel(titulo + " " + valor);
        label.setFont(Paleta.FUENTE_MONO);
        label.setForeground(Paleta.TEXTO_NORMAL);
        return label;
    }

    /* ==================== PESTAÑA: PROCESOS ==================== */

    // Crea la pestaña con la tabla + detalle.
    private JPanel crearPestanaProcesos() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Paleta.FONDO_GENERAL);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // IMPORTANTE: crear el detalle PRIMERO para que los labels existan
        // cuando la tabla dispare mostrarDetalle().
        JPanel panelDetalle = crearPanelDetalle();

        // Tabla en el centro
        panel.add(crearPanelTabla(), BorderLayout.CENTER);

        // Detalle abajo
        panel.add(panelDetalle, BorderLayout.SOUTH);

        return panel;
    }

    // Crea el panel de la tabla con todos los procesos terminados.
    private JPanel crearPanelTabla() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                "Procesos terminados",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                Paleta.FUENTE_TITULO_PANEL,
                Paleta.MORADO_ACENTO
        ));

        modelo = new DefaultTableModel(
                new Object[]{"ID", "CPU", "Inicio", "Fin", "Duracion",
                             "Turnaround", "Tr/Ts", "% del total", "Prioridad"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        List<LineaProceso> lineas = estadisticas.getLineas();
        int duracionTotal = estadisticas.getDuracionTotal();

        for (LineaProceso lp : lineas) {
            int dur = lp.getDuracion();
            double porcentaje = (duracionTotal > 0 && dur > 0)
                    ? (dur * 100.0 / duracionTotal) : 0;
            String cpuStr = (lp.getCpuAsignado() >= 0)
                    ? "CPU " + lp.getCpuAsignado() : "-";
            modelo.addRow(new Object[]{
                "ID " + lp.getId(),
                cpuStr,
                formatearTiempo(lp.getInicio()),
                formatearTiempo(lp.getFin()),
                dur + " s",
                String.valueOf(lp.getTurnaround()),
                String.format("%.2f", lp.getTrTs()),
                String.format("%.1f%%", porcentaje),
                String.valueOf(lp.getPrioridad())
            });
        }

        tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(28);
        tabla.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tabla.setShowGrid(true);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                    c.setForeground(Paleta.TEXTO_NORMAL);
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int fila = tabla.getSelectedRow();
                mostrarDetalle(fila);
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        panel.add(scroll, BorderLayout.CENTER);

        // Seleccionar primera fila DESPUES de que todos los labels existan
        if (!lineas.isEmpty()) {
            tabla.setRowSelectionInterval(0, 0);
        } else {
            mostrarDetalle(-1);
        }

        return panel;
    }

    // Crea el panel de detalle del proceso seleccionado.
    private JPanel crearPanelDetalle() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Detalle del proceso seleccionado",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(10, 15, 10, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.weightx = 1;

        lblId         = crearLabelDetalle("ID", "-");
        lblEstado     = crearLabelDetalle("Estado", "-");
        lblCpu        = crearLabelDetalle("CPU", "-");
        lblInicio     = crearLabelDetalle("Inicio", "-");
        lblFin        = crearLabelDetalle("Fin", "-");
        lblDuracion   = crearLabelDetalle("Duracion", "-");
        lblPorcentaje = crearLabelDetalle("% del total", "-");
        lblTurnaround = crearLabelDetalle("Turnaround", "-");
        lblTrTs       = crearLabelDetalle("Tr/Ts", "-");
        lblPrioridad  = crearLabelDetalle("Prioridad", "-");
        lblBase       = crearLabelDetalle("Base", "-");
        lblAlcance    = crearLabelDetalle("Alcance", "-");

        int y = 0;
        gbc.gridx = 0; gbc.gridy = y; panel.add(lblId, gbc);
        gbc.gridx = 1; panel.add(lblEstado, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblCpu, gbc);
        gbc.gridx = 1; panel.add(lblPrioridad, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblInicio, gbc);
        gbc.gridx = 1; panel.add(lblFin, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblDuracion, gbc);
        gbc.gridx = 1; panel.add(lblPorcentaje, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblTurnaround, gbc);
        gbc.gridx = 1; panel.add(lblTrTs, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblBase, gbc);
        gbc.gridx = 1; panel.add(lblAlcance, gbc);

        return panel;
    }

    // Crea una etiqueta del panel de detalle.
    private JLabel crearLabelDetalle(String nombre, String valorInicial) {
        JLabel label = new JLabel(nombre + ": " + valorInicial);
        label.setFont(Paleta.FUENTE_MONO);
        label.setForeground(Paleta.TEXTO_NORMAL);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE0, 0xE0, 0xE0), 1),
                new EmptyBorder(5, 8, 5, 8)
        ));
        label.setOpaque(true);
        label.setBackground(new Color(0xF8, 0xF9, 0xFA));
        return label;
    }

    // Muestra en el panel de detalle el proceso de la fila indicada.
    private void mostrarDetalle(int fila) {
        if (lblId == null) return;   // seguridad

        List<LineaProceso> lineas = estadisticas.getLineas();
        if (fila < 0 || fila >= lineas.size()) {
            lblId.setText("ID: -");
            lblEstado.setText("Estado: -");
            lblCpu.setText("CPU: -");
            lblInicio.setText("Inicio: -");
            lblFin.setText("Fin: -");
            lblDuracion.setText("Duracion: -");
            lblPorcentaje.setText("% del total: -");
            lblTurnaround.setText("Turnaround: -");
            lblTrTs.setText("Tr/Ts: -");
            lblPrioridad.setText("Prioridad: -");
            lblBase.setText("Base: -");
            lblAlcance.setText("Alcance: -");
            return;
        }

        LineaProceso lp = lineas.get(fila);
        int dur = lp.getDuracion();
        int duracionTotal = estadisticas.getDuracionTotal();
        double porcentaje = (duracionTotal > 0 && dur > 0)
                ? (dur * 100.0 / duracionTotal) : 0;
        String cpuStr = (lp.getCpuAsignado() >= 0)
                ? String.valueOf(lp.getCpuAsignado()) : "-";

        lblId.setText("ID: " + lp.getId());
        lblEstado.setText("Estado: EXIT");
        lblCpu.setText("CPU: " + cpuStr);
        lblInicio.setText("Inicio: " + formatearTiempo(lp.getInicio()));
        lblFin.setText("Fin: " + formatearTiempo(lp.getFin()));
        lblDuracion.setText("Duracion: " + dur + " s (" + formatearDuracion(dur) + ")");
        lblPorcentaje.setText(String.format("%% del total: %.1f%%", porcentaje));
        lblTurnaround.setText("Turnaround: " + lp.getTurnaround());
        lblTrTs.setText(String.format("Tr/Ts: %.2f", lp.getTrTs()));
        lblPrioridad.setText("Prioridad: " + lp.getPrioridad());
        lblBase.setText("Base: " + lp.getBase());
        lblAlcance.setText("Alcance: " + lp.getAlcance() + " instrucciones");
    }

    /* ==================== PESTAÑA: GANTT ==================== */

    // Crea la pestaña del diagrama de Gantt.
    private JPanel crearPestanaGantt() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Paleta.FONDO_GENERAL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        PanelDiagramaGantt gantt = new PanelDiagramaGantt(estadisticas);
        panel.add(gantt, BorderLayout.CENTER);

        return panel;
    }

    /* ==================== HELPERS ==================== */

    /**
     * Formatea un instante simulado como "t=X", o "-" si no fue marcado.
     *
     * @param t instante del reloj simulado, o {@link BCPTerminado#TIEMPO_NO_MARCADO}
     */
    private String formatearTiempo(int t) {
        if (t == BCPTerminado.TIEMPO_NO_MARCADO) return "-";
        return "t=" + t;
    }

    // Formatea una duracion simulada en segundos como texto legible.
    private String formatearDuracion(int segundos) {
        if (segundos < 0) return "-";
        int min = segundos / 60;
        int seg = segundos % 60;
        if (min > 0) {
            return min + " min " + seg + " s";
        }
        return seg + " s";
    }
}