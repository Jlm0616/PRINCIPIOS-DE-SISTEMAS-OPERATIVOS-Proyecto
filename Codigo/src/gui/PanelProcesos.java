package gui;

import modelo.BCP;
import modelo.EstadoProceso;
import logica.ListaProcesos;
import logica.ListaDeTrabajos;
import logica.cpu.Nucleo;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PanelProcesos extends JPanel {

    private JTable tablaProcesos;
    private DefaultTableModel modeloProcesos;

    private JComboBox<String> comboCPUs;
    private List<Nucleo> nucleosActuales;
    private JPanel panelDetalleBCP;
    private TitledBorder bordeBCP;
    private JButton btnTiemposLlegada;

    // Callback para avisar a VentanaPrincipal que se cambiaron los tiempos
    private Consumer<Object[][]> onTiemposLlegadaCambiados;

    private JLabel lblId;
    private JLabel lblPC;
    private JLabel lblIR;
    private JLabel lblAC;
    private JLabel lblAX;
    private JLabel lblBX;
    private JLabel lblCX;
    private JLabel lblDX;
    private JLabel lblOF;
    private JLabel lblEQ;
    private JLabel lblPila;
    private JLabel lblEstado;
    private JLabel lblBase;
    private JLabel lblAlcance;

    public PanelProcesos() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        // GridBagLayout: el espacio extra de altura se reparte entre la tabla
        // de procesos (arriba) y el detalle del BCP (abajo) segun weighty.
        setLayout(new GridBagLayout());
        setOpaque(false);

        // ===== Panel superior: Tabla + Botón de tiempos =====
        JPanel panelSuperior = new JPanel(new BorderLayout(5, 5));
        panelSuperior.setOpaque(false);

        // Tabla de procesos
        modeloProcesos = new DefaultTableModel(new Object[]{"Proceso / CPU", "Estado"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaProcesos = new JTable(modeloProcesos);
        tablaProcesos.setFont(Paleta.FUENTE_MONO);
        tablaProcesos.setRowHeight(24);
        tablaProcesos.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tablaProcesos.setShowGrid(true);
        tablaProcesos.setFillsViewportHeight(true);
        tablaProcesos.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);

        tablaProcesos.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    return c;
                }

                if (column == 1) {
                    String estado = value != null ? value.toString() : "";
                    c.setBackground(colorPorEstado(estado));
                    c.setForeground(Color.WHITE);
                    setFont(Paleta.FUENTE_LABEL_BOLD);
                } else {
                    String texto = value != null ? value.toString() : "";
                    if (texto.startsWith("CPU ")) {
                        try {
                            int idCpu = Integer.parseInt(texto.substring(4, 5));
                            c.setBackground(Paleta.getColorCPU(idCpu));
                            c.setForeground(Color.BLACK);
                            setFont(Paleta.FUENTE_MONO_BOLD);
                        } catch (Exception e) {
                            c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                            c.setForeground(Paleta.TEXTO_NORMAL);
                        }
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                        c.setForeground(Paleta.TEXTO_NORMAL);
                    }
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tablaProcesos);
        scrollTabla.setPreferredSize(new Dimension(0, 200));
        scrollTabla.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                "Procesos (RAM + Disco)",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                Paleta.FUENTE_TITULO_PANEL,
                Paleta.VERDE_OSCURO
        ));
        panelSuperior.add(scrollTabla, BorderLayout.CENTER);

        // Botón de Tiempos de Llegada
        btnTiemposLlegada = new JButton("Asignar Tiempos de Llegada");
        btnTiemposLlegada.setFont(Paleta.FUENTE_BOTON);
        btnTiemposLlegada.setBackground(Paleta.MORADO_ACENTO);
        btnTiemposLlegada.setForeground(Paleta.TEXTO_CLARO);
        btnTiemposLlegada.setFocusPainted(false);
        btnTiemposLlegada.addActionListener(e -> abrirVentanaTiempos());

        JPanel panelBotonTiempos = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        panelBotonTiempos.setOpaque(false);
        panelBotonTiempos.add(btnTiemposLlegada);
        panelSuperior.add(panelBotonTiempos, BorderLayout.SOUTH);

        // Fila 0: tabla de procesos (crece junto con el BCP)
        GridBagConstraints gbcSup = new GridBagConstraints();
        gbcSup.gridx = 0;
        gbcSup.gridy = 0;
        gbcSup.weightx = 1.0;
        gbcSup.weighty = 0.5;
        gbcSup.fill = GridBagConstraints.BOTH;
        gbcSup.insets = new Insets(0, 0, 5, 0);
        add(panelSuperior, gbcSup);

        // ===== Panel BCP actual (abajo) =====
        JPanel panelSur = new JPanel(new BorderLayout(5, 5));
        panelSur.setOpaque(false);

        JPanel panelSelector = new JPanel(new BorderLayout());
        panelSelector.setOpaque(false);
        panelSelector.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        comboCPUs = new JComboBox<>();
        comboCPUs.setFont(Paleta.FUENTE_LABEL_BOLD);
        comboCPUs.addActionListener(e -> actualizarVistaBCP());
        
        panelSelector.add(new JLabel("Ver BCP de: "), BorderLayout.WEST);
        panelSelector.add(comboCPUs, BorderLayout.CENTER);
        panelSur.add(panelSelector, BorderLayout.NORTH);

        panelDetalleBCP = crearPanelBCP();
        JScrollPane scrollBCP = new JScrollPane(panelDetalleBCP);
        
        bordeBCP = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                "Detalle del BCP",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                Paleta.FUENTE_TITULO_PANEL,
                Paleta.MORADO_ACENTO
        );
        scrollBCP.setBorder(bordeBCP);
        panelSur.add(scrollBCP, BorderLayout.CENTER);

        // Fila 1: selector + detalle del BCP
        GridBagConstraints gbcSur = new GridBagConstraints();
        gbcSur.gridx = 0;
        gbcSur.gridy = 1;
        gbcSur.weightx = 1.0;
        gbcSur.weighty = 0.5;
        gbcSur.fill = GridBagConstraints.BOTH;
        gbcSur.insets = new Insets(0, 0, 0, 0);
        add(panelSur, gbcSur);
    }

    // Abre la ventana para asignar tiempos de llegada
    private void abrirVentanaTiempos() {
        if (nucleosActuales == null || nucleosActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay CPUs inicializadas.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Crear una lista temporal con los BCPs en cola (READY) para pasársela a la ventana
        List<BCP> procesosEnCola = new ArrayList<>();
        // Nota: El PanelProcesos no tiene acceso directo a la ListaProcesos, 
        // así que la ventana usará los procesos de los núcleos y de la RAM que recibe en actualizarLista
        // Como esto se llama desde el botón, solo tiene los núcleos y lo que se guardó la última vez.
        // Sin embargo, los procesos en RAM no se guardan en el panel.
        // Solución: guardar una referencia a la lista de procesos temporal en el método actualizarLista.
        // Lo haremos con una variable de clase.
        
        // Ventana modal
        VentanaTiemposLlegada ventana = new VentanaTiemposLlegada(
                (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this),
                nucleosActuales,
                listaProcesosTemporal // <-- Nueva variable
        );

        ventana.setVisible(true);

        if (ventana.isConfirmado()) {
            Object[][] tiempos = ventana.getTiempos();
            if (onTiemposLlegadaCambiados != null) {
                onTiemposLlegadaCambiados.accept(tiempos);
            }
        }
    }

    // Variable temporal para guardar la lista de procesos en RAM
    private List<BCP> listaProcesosTemporal = new ArrayList<>();

    public void actualizarLista(ListaProcesos listaProcesos,
                                List<Nucleo> nucleos,
                                List<BCP> procesosBloqueadosInput,
                                ListaDeTrabajos listaDeTrabajos) {
        this.nucleosActuales = nucleos;
        this.listaProcesosTemporal = listaProcesos != null ? listaProcesos.toList() : new ArrayList<>();
        
        modeloProcesos.setRowCount(0);
        Set<Integer> idsAgregados = new HashSet<>();

        // 1. Procesos en las CPUs
        for (Nucleo nucleo : nucleos) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                modeloProcesos.addRow(new Object[]{
                    "CPU " + nucleo.getIdNucleo() + " -> ID " + bcp.getId(),
                    bcp.getEstado().toString()
                });
                idsAgregados.add(bcp.getId());
            } else {
                modeloProcesos.addRow(new Object[]{
                    "CPU " + nucleo.getIdNucleo(),
                    "LIBRE"
                });
            }
        }

        // 2. Lista de procesos en RAM
        if (listaProcesos != null) {
            for (BCP bcp : listaProcesos.toList()) {
                if (idsAgregados.add(bcp.getId())) {
                    modeloProcesos.addRow(new Object[]{
                        "ID " + bcp.getId() + " (En RAM)",
                        bcp.getEstado().toString()
                    });
                }
            }
        }

        // 3. Procesos bloqueados
        if (procesosBloqueadosInput != null) {
            for (BCP bcp : procesosBloqueadosInput) {
                if (idsAgregados.add(bcp.getId())) {
                    modeloProcesos.addRow(new Object[]{
                        "ID " + bcp.getId() + " (Input)",
                        bcp.getEstado().toString()
                    });
                }
            }
        }

        // 4. Lista de trabajos (swap)
        if (listaDeTrabajos != null) {
            for (Object[] trabajo : listaDeTrabajos.toList()) {
                String nombre = (trabajo[0] != null) ? trabajo[0].toString() : "(?)";
                modeloProcesos.addRow(new Object[]{
                    nombre + " (Swap)",
                    "NEW"
                });
            }
        }

        if (comboCPUs.getItemCount() != nucleos.size()) {
            comboCPUs.removeAllItems();
            for (Nucleo n : nucleos) {
                comboCPUs.addItem("CPU " + n.getIdNucleo());
            }
        }
        
        actualizarVistaBCP();
    }

    // Setter para el callback
    public void setOnTiemposLlegadaCambiados(Consumer<Object[][]> callback) {
        this.onTiemposLlegadaCambiados = callback;
    }

    private JPanel crearPanelBCP() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.weightx = 1;

        lblId     = crearLabel("ID", "-");
        lblEstado = crearLabel("Estado", "-");
        lblPC     = crearLabel("PC", "-");
        lblIR     = crearLabel("IR", "-");
        lblAC     = crearLabel("AC", "-");
        lblAX     = crearLabel("AX", "-");
        lblBX     = crearLabel("BX", "-");
        lblCX     = crearLabel("CX", "-");
        lblDX     = crearLabel("DX", "-");
        lblOF     = crearLabel("OF", "0");
        lblEQ     = crearLabel("EQ", "0");
        lblPila   = crearLabel("Pila", "[]");
        lblBase   = crearLabel("Base", "-");
        lblAlcance = crearLabel("Alcance", "-");

        int y = 0;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblId, gbc);
        gbc.gridx = 1; panel.add(lblEstado, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblPC, gbc);
        gbc.gridx = 1; panel.add(lblIR, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblAC, gbc);
        gbc.gridx = 1; panel.add(lblAX, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblBX, gbc);
        gbc.gridx = 1; panel.add(lblCX, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblDX, gbc);
        gbc.gridx = 1; panel.add(lblOF, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblEQ, gbc);
        gbc.gridx = 1; panel.add(lblBase, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblAlcance, gbc);
        gbc.gridx = 1; panel.add(lblPila, gbc);
        y++;

        return panel;
    }

    private JLabel crearLabel(String nombre, String valorInicial) {
        JLabel label = new JLabel(nombre + ": " + valorInicial);
        label.setFont(Paleta.FUENTE_MONO);
        label.setForeground(Paleta.TEXTO_NORMAL);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE0, 0xE0, 0xE0), 1),
                new EmptyBorder(3, 6, 3, 6)
        ));
        label.setOpaque(true);
        label.setBackground(new Color(0xF8, 0xF9, 0xFA));
        return label;
    }

    private Color colorPorEstado(String estado) {
        if (estado == null) return Paleta.ESTADO_EXIT;
        switch (estado.toUpperCase()) {
            case "NEW":             return Paleta.ESTADO_NEW;
            case "READY":           return Paleta.ESTADO_READY;
            case "RUNNING":         return Paleta.ESTADO_RUNNING;
            case "BLOCKED":         return Paleta.ESTADO_BLOCKED;
            case "EXIT":            return Paleta.ESTADO_EXIT;
            case "READY_SUSPEND":   return Paleta.ESTADO_SUSPENDIDO;
            case "BLOCKED_SUSPEND": return Paleta.ESTADO_SUSPENDIDO;
            default:                return Paleta.ESTADO_EXIT;
        }
    }

    private void actualizarVistaBCP() {
        if (nucleosActuales == null || comboCPUs.getSelectedIndex() < 0) return;
        
        int index = comboCPUs.getSelectedIndex();
        BCP bcp = null;
        
        if (bordeBCP != null) {
            bordeBCP.setTitleColor(Paleta.getColorCPU(index));
            bordeBCP.setBorder(BorderFactory.createLineBorder(Paleta.getColorCPU(index), 2));
            panelDetalleBCP.repaint();
        }

        if (index < nucleosActuales.size() && nucleosActuales.get(index).estaOcupado()) {
            bcp = nucleosActuales.get(index).getBcpActual();
        }
        
        if (bcp == null) {
            lblId.setText("ID: -");
            lblEstado.setText("Estado: -");
            lblPC.setText("PC: -");
            lblIR.setText("IR: -");
            lblAC.setText("AC: -");
            lblAX.setText("AX: -");
            lblBX.setText("BX: -");
            lblCX.setText("CX: -");
            lblDX.setText("DX: -");
            lblOF.setText("OF: 0");
            lblEQ.setText("EQ: 0");
            lblPila.setText("Pila: []");
            lblBase.setText("Base: -");
            lblAlcance.setText("Alcance: -");
            return;
        }

        lblId.setText("ID: " + bcp.getId());
        lblEstado.setText("Estado: " + bcp.getEstado());
        lblPC.setText("PC: " + bcp.getPc());
        lblIR.setText("IR: " + bcp.getIr());
        lblAC.setText("AC: " + bcp.getAc());
        lblAX.setText("AX: " + bcp.getAx());
        lblBX.setText("BX: " + bcp.getBx());
        lblCX.setText("CX: " + bcp.getCx());
        lblDX.setText("DX: " + bcp.getDx());
        lblOF.setText("OF: " + (bcp.getOverflow() ? "1" : "0"));
        lblEQ.setText("EQ: " + (bcp.getBanderaIgual() ? "1" : "0"));
        lblPila.setText("Pila: " + bcp.getPila().toString());
        lblBase.setText("Base: " + bcp.getBase());
        lblAlcance.setText("Alcance: " + bcp.getAlcance());
    }
}