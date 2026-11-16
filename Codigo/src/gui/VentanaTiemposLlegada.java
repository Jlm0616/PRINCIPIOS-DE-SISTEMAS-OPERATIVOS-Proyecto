package gui;

import logica.cpu.Nucleo;
import logica.TrabajoNuevo;
import modelo.ProgramaPendiente;
import modelo.BCP;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

/**
 * Ventana modal para configurar tiempos de llegada y prioridades.
 *
 * Muestra:
 *   - Procesos en CPU (Ubicacion = "CPU N").
 *   - Procesos en RAM (Ubicacion = "En RAM (Cola)").
 *   - Procesos en NEW, sin BCP y guardados en swap (Ubicacion = "Espera TL=N").
 *
 * Columnas editables: Tiempo Llegada (col 3) y Prioridad (col 4).
 *
 * PROYECTO 2:
 *  - Los procesos en NEW vienen como TrabajoNuevo (no tienen BCP): su
 *    rafaga es el tamano de su programa en swap.
 *  - El Tiempo de Llegada solo es editable para NEW o pendientes de llegada.
 *    Los procesos admitidos en RAM o CPU no pueden cambiar su llegada.
 *  - Si el nuevo TL es futuro, GestorProcesos devuelve el proceso de RAM
 *    a NEW (le destruye el BCP y lo manda al swap).
 *  - Si el nuevo TL es <= relojGlobal, GestorProcesos le crea el BCP, lo
 *    carga en RAM y lo pasa a READY al aplicar los cambios.
 */
public class VentanaTiemposLlegada extends JDialog {

    private static final int COL_UBICACION = 0;
    private static final int COL_ID        = 1;
    private static final int COL_ARCHIVO   = 2;
    private static final int COL_RAFAGA    = 3;
    private static final int COL_LLEGADA   = 4;
    private static final int COL_PRIORIDAD = 5;
    private static final int COL_NUCLEO    = 6;
    private static final int COL_BOLETOS   = 7;

    /** Texto del combo de nucleo para "cualquier nucleo libre". */
    private static final String CUALQUIERA = "Cualquiera";

    private static final String UBIC_NEW = "Espera TL=";
    private static final String UBIC_RAM = "En RAM (Cola)";
    private static final String UBIC_PENDIENTE = "Pendiente TL=";

    // Color para procesos pendientes (fondo amarillo claro)
    private static final Color COLOR_PENDIENTE = new Color(0xFF, 0xF3, 0xCD);
    // Color para celdas bloqueadas (gris claro)
    private static final Color COLOR_BLOQUEADO = new Color(0xEE, 0xEE, 0xEE);

    private JTable tabla;
    private DefaultTableModel modelo;
    private List<Nucleo> nucleos;
    private List<BCP> procesosEnCola;
    private List<TrabajoNuevo> trabajosNuevos;
    private List<ProgramaPendiente> programasPendientes;

    private boolean confirmado = false;

    // Nucleo asignado y boletos de cada proceso (los da GestorProcesos)
    private java.util.function.IntUnaryOperator nucleoDe = id -> -1;
    private java.util.function.IntBinaryOperator boletosDe = (id, rafaga) -> rafaga;
    private java.util.function.IntFunction<String> nombreArchivoDe = id -> "";

    /** Constructor legacy (sin pendientes). Mantiene compatibilidad. */
    public VentanaTiemposLlegada(Frame propietario,
                                 List<Nucleo> nucleos,
                                 List<BCP> procesosEnCola) {
        this(propietario, nucleos, procesosEnCola, new ArrayList<TrabajoNuevo>());
    }

    /** Constructor completo (con trabajos en NEW). */
    public VentanaTiemposLlegada(Frame propietario,
                                 List<Nucleo> nucleos,
                                 List<BCP> procesosEnCola,
                                 List<TrabajoNuevo> trabajosNuevos) {
        this(propietario, nucleos, procesosEnCola, trabajosNuevos,
                new ArrayList<ProgramaPendiente>(), null, null, null);
    }

    /**
     * Constructor completo: ademas de llegada y prioridad, muestra el nucleo
     * asignado a cada proceso y sus boletos de Lottery.
     *
     * @param nucleoDe  nucleo asignado de cada proceso (-1 = cualquiera)
     * @param boletosDe boletos de cada proceso, dados el id y la rafaga
     */
    public VentanaTiemposLlegada(Frame propietario,
                                 List<Nucleo> nucleos,
                                 List<BCP> procesosEnCola,
                                 List<TrabajoNuevo> trabajosNuevos,
                                 java.util.function.IntUnaryOperator nucleoDe,
                                 java.util.function.IntBinaryOperator boletosDe) {
        this(propietario, nucleos, procesosEnCola, trabajosNuevos,
                new ArrayList<ProgramaPendiente>(), nucleoDe, boletosDe, null);
    }

    /** Constructor con programas pendientes de llegada, sin BCP, RAM ni swap. */
    public VentanaTiemposLlegada(Frame propietario,
                                  List<Nucleo> nucleos,
                                  List<BCP> procesosEnCola,
                                  List<TrabajoNuevo> trabajosNuevos,
                                  List<ProgramaPendiente> programasPendientes,
                                  java.util.function.IntUnaryOperator nucleoDe,
                                  java.util.function.IntBinaryOperator boletosDe) {
        this(propietario, nucleos, procesosEnCola, trabajosNuevos,
                programasPendientes, nucleoDe, boletosDe, null);
    }

    public VentanaTiemposLlegada(Frame propietario,
                                  List<Nucleo> nucleos,
                                  List<BCP> procesosEnCola,
                                  List<TrabajoNuevo> trabajosNuevos,
                                  List<ProgramaPendiente> programasPendientes,
                                  java.util.function.IntUnaryOperator nucleoDe,
                                  java.util.function.IntBinaryOperator boletosDe,
                                  java.util.function.IntFunction<String> nombreArchivoDe) {
        super(propietario, "Configurar Tiempos de Llegada, Prioridades y Nucleos", true);
        if (nucleoDe != null) this.nucleoDe = nucleoDe;
        if (boletosDe != null) this.boletosDe = boletosDe;
        if (nombreArchivoDe != null) this.nombreArchivoDe = nombreArchivoDe;
        this.nucleos = nucleos;
        this.procesosEnCola = procesosEnCola;
        this.trabajosNuevos = (trabajosNuevos != null)
                ? trabajosNuevos : new ArrayList<TrabajoNuevo>();

        this.programasPendientes = programasPendientes != null
                ? programasPendientes : new ArrayList<ProgramaPendiente>();

        construirInterfaz();
        pack();
        setSize(1040, 520);
        setResizable(true);
        setLocationRelativeTo(propietario);
    }

    /** true si el TL de una fila con esta ubicacion se puede editar. */
    private static boolean tlEditable(String ubicacion) {
        return ubicacion != null
                && (ubicacion.startsWith(UBIC_NEW) || ubicacion.startsWith(UBIC_PENDIENTE)
                 || ubicacion.equals(UBIC_RAM));
    }

    private void construirInterfaz() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBackground(Paleta.FONDO_GENERAL);
        panelPrincipal.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(panelPrincipal);

        JLabel lblTitulo = new JLabel("Asignar Tiempos de Llegada, Prioridades y Nucleos");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.VERDE_OSCURO);
        panelPrincipal.add(lblTitulo, BorderLayout.NORTH);

        // Columnas: Ubicación | ID | Ráfaga | Tiempo Llegada | Prioridad | Núcleo | Boletos
        modelo = new DefaultTableModel(
                new Object[]{"Ubicación", "ID", "Archivo ASM", "Ráfaga", "Tiempo Llegada", "Prioridad",
                             "Núcleo", "Boletos (Lottery)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Prioridad, nucleo y boletos siempre se pueden editar
                if (column == COL_PRIORIDAD || column == COL_NUCLEO
                        || column == COL_BOLETOS) return true;

                // El TL se edita en procesos NEW y en los de la cola de RAM
                if (column == COL_LLEGADA) {
                    return tlEditable((String) getValueAt(row, COL_UBICACION));
                }

                return false;
            }
        };

        // 1. Procesos en CPU
        for (Nucleo nucleo : nucleos) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null) {
                    modelo.addRow(new Object[]{
                        "CPU " + nucleo.getIdNucleo(),
                        bcp.getId(),
                        nombreArchivoDe.apply(bcp.getId()),
                        bcp.getRafaga(),
                        bcp.getTiempoLlegada(),
                        bcp.getPrioridad(),
                        textoNucleo(bcp.getId()),
                        boletosDe.applyAsInt(bcp.getId(), bcp.getRafaga())
                    });
                }
            }
        }

        // 2. Procesos en RAM (READY)
        for (BCP bcp : procesosEnCola) {
            modelo.addRow(new Object[]{
                UBIC_RAM,
                bcp.getId(),
                nombreArchivoDe.apply(bcp.getId()),
                bcp.getRafaga(),
                bcp.getTiempoLlegada(),
                bcp.getPrioridad(),
                textoNucleo(bcp.getId()),
                boletosDe.applyAsInt(bcp.getId(), bcp.getRafaga())
            });
        }

        // 3. Procesos en NEW (desde TrabajoNuevo, sin BCP)
        for (TrabajoNuevo t : trabajosNuevos) {
            modelo.addRow(new Object[]{
                UBIC_NEW + t.getTiempoLlegada(),
                t.getId(),
                t.getNombre(),
                t.getTamano(),
                t.getTiempoLlegada(),
                t.getPrioridad(),
                textoNucleo(t.getId()),
                boletosDe.applyAsInt(t.getId(), t.getTamano())
            });
        }

        // 4. Programas pendientes: aun no tienen BCP ni ocupan RAM o swap.
        for (ProgramaPendiente pendiente : programasPendientes) {
            modelo.addRow(new Object[]{
                UBIC_PENDIENTE + pendiente.getTiempoLlegada(),
                pendiente.getId(),
                pendiente.getNombreArchivo(),
                pendiente.getInstrucciones().size(),
                pendiente.getTiempoLlegada(),
                pendiente.getPrioridad(),
                textoNucleo(pendiente.getId()),
                boletosDe.applyAsInt(pendiente.getId(), pendiente.getInstrucciones().size())
            });
        }

        tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(25);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);

        // Renderizador de la columna "Ubicación" y celdas bloqueadas
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    if (column == COL_UBICACION) {
                        String ubicacion = value != null ? value.toString() : "";
                        if (ubicacion.startsWith("CPU ")) {
                            try {
                                int idCpu = Integer.parseInt(ubicacion.substring(4));
                                c.setBackground(Paleta.getColorCPU(idCpu));
                                c.setForeground(Color.BLACK);
                                setFont(Paleta.FUENTE_MONO_BOLD);
                            } catch (Exception e) {
                                c.setBackground(Color.WHITE);
                                c.setForeground(Paleta.TEXTO_NORMAL);
                                setFont(Paleta.FUENTE_MONO);
                            }
                        } else if ((ubicacion.startsWith(UBIC_NEW) || ubicacion.startsWith(UBIC_PENDIENTE))) {
                            c.setBackground(COLOR_PENDIENTE);
                            c.setForeground(Paleta.TEXTO_NORMAL);
                            setFont(Paleta.FUENTE_MONO);
                        } else {
                            c.setBackground(Color.WHITE);
                            c.setForeground(Paleta.TEXTO_NORMAL);
                            setFont(Paleta.FUENTE_MONO);
                        }
                    } else if (column == COL_LLEGADA) {
                        // Si NO es editable, pintarlo de gris
                        String ubicacion = (String) table.getValueAt(row, COL_UBICACION);
                        if (ubicacion != null && !tlEditable(ubicacion)) {
                            c.setBackground(COLOR_BLOQUEADO);
                            c.setForeground(Color.GRAY);
                        } else {
                            c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                            c.setForeground(Paleta.TEXTO_NORMAL);
                        }
                        setFont(Paleta.FUENTE_MONO);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                        c.setForeground(Paleta.TEXTO_NORMAL);
                        setFont(Paleta.FUENTE_MONO);
                    }
                }
                return c;
            }
        });

        // El nucleo se elige de una lista: cualquiera, o una CPU concreta.
        javax.swing.JComboBox<String> comboNucleo = new javax.swing.JComboBox<>();
        comboNucleo.addItem(CUALQUIERA);
        for (Nucleo n : nucleos) comboNucleo.addItem("CPU " + n.getIdNucleo());
        tabla.getColumnModel().getColumn(COL_NUCLEO)
                .setCellEditor(new javax.swing.DefaultCellEditor(comboNucleo));

        JScrollPane scroll = new JScrollPane(tabla);
        panelPrincipal.add(scroll, BorderLayout.CENTER);

        JLabel lblAyuda = new JLabel("<html>Núcleo: Cualquiera = el proceso va a la primera CPU libre;"
                + " una CPU concreta = solo esa CPU lo ejecuta (hasta 5 procesos por CPU)."
                + " Boletos: solo se usan con Lottery.</html>");
        lblAyuda.setFont(Paleta.FUENTE_LABEL);
        lblAyuda.setForeground(Paleta.TEXTO_NORMAL);

        // Botones
        JButton btnAceptar = new JButton("Aplicar");
        JButton btnCancelar = new JButton("Cancelar");

        btnAceptar.addActionListener(e -> confirmar());
        btnCancelar.addActionListener(e -> dispose());

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setOpaque(false);
        panelBotones.add(btnCancelar);
        panelBotones.add(btnAceptar);

        JPanel sur = new JPanel(new BorderLayout(0, 8));
        sur.setOpaque(false);
        sur.add(lblAyuda, BorderLayout.NORTH);
        sur.add(panelBotones, BorderLayout.SOUTH);
        panelPrincipal.add(sur, BorderLayout.SOUTH);
    }

    private void confirmar() {
        // Cerrar una edicion de celda que siga abierta (si el usuario no
        // presiono Enter, el modelo todavia tiene el valor anterior)
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }

        try {
            for (int i = 0; i < modelo.getRowCount(); i++) {
                Integer.parseInt(modelo.getValueAt(i, COL_LLEGADA).toString().trim());
                Integer.parseInt(modelo.getValueAt(i, COL_PRIORIDAD).toString().trim());
                if (Integer.parseInt(modelo.getValueAt(i, COL_BOLETOS).toString().trim()) < 1) {
                    throw new NumberFormatException();
                }
            }
        } catch (NumberFormatException e) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Los tiempos de llegada y las prioridades deben ser números enteros,"
                    + " y los boletos enteros mayores o iguales a 1.",
                    "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        confirmado = true;
        dispose();
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    /**
     * Devuelve un arreglo con los tiempos de llegada: [ID, TiempoLlegada].
     */
    public Object[][] getTiempos() {
        // Solo enviar cambios de procesos cuya llegada aun se puede editar.
        // Asi el gestor no reescribe tiempos de procesos ya admitidos.
        List<Object[]> editables = new ArrayList<>();
        for (int i = 0; i < modelo.getRowCount(); i++) {
            String ubicacion = modelo.getValueAt(i, COL_UBICACION).toString();
            if (!tlEditable(ubicacion)) continue;
            int id = Integer.parseInt(modelo.getValueAt(i, COL_ID).toString());
            int tiempo = Integer.parseInt(modelo.getValueAt(i, COL_LLEGADA).toString().trim());
            editables.add(new Object[]{id, tiempo});
        }
        return editables.toArray(new Object[0][2]);
    }

    /**
     * Devuelve el nucleo de cada proceso: [ID, nucleo], con -1 = cualquiera.
     */
    public Object[][] getNucleos() {
        Object[][] res = new Object[modelo.getRowCount()][2];
        for (int i = 0; i < modelo.getRowCount(); i++) {
            res[i][0] = Integer.parseInt(modelo.getValueAt(i, COL_ID).toString());
            String texto = modelo.getValueAt(i, COL_NUCLEO).toString();
            res[i][1] = texto.startsWith("CPU ") ? Integer.parseInt(texto.substring(4).trim()) : -1;
        }
        return res;
    }

    /**
     * Devuelve los boletos de Lottery de cada proceso: [ID, boletos].
     */
    public Object[][] getBoletos() {
        Object[][] res = new Object[modelo.getRowCount()][2];
        for (int i = 0; i < modelo.getRowCount(); i++) {
            res[i][0] = Integer.parseInt(modelo.getValueAt(i, COL_ID).toString());
            res[i][1] = Integer.parseInt(modelo.getValueAt(i, COL_BOLETOS).toString().trim());
        }
        return res;
    }

    // Texto del combo de nucleo para el proceso.
    private String textoNucleo(int id) {
        int n = nucleoDe.applyAsInt(id);
        return n < 0 ? CUALQUIERA : "CPU " + n;
    }

    /**
     * Devuelve un arreglo con las prioridades: [ID, Prioridad].
     */
    public Object[][] getPrioridades() {
        Object[][] prioridades = new Object[modelo.getRowCount()][2];
        for (int i = 0; i < modelo.getRowCount(); i++) {
            int id = Integer.parseInt(modelo.getValueAt(i, COL_ID).toString());
            int prioridad = Integer.parseInt(modelo.getValueAt(i, COL_PRIORIDAD).toString().trim());
            prioridades[i][0] = id;
            prioridades[i][1] = prioridad;
        }
        return prioridades;
    }
}