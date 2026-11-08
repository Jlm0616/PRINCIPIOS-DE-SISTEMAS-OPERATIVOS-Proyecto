package gui;

import logica.cpu.Nucleo;
import logica.TrabajoNuevo;
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
 *  - El Tiempo de Llegada es editable si el proceso esta en NEW o en la
 *    cola de RAM (sin haber ejecutado). Los que estan en CPU ya no se
 *    pueden cambiar.
 *  - Si el nuevo TL es futuro, GestorProcesos devuelve el proceso de RAM
 *    a NEW (le destruye el BCP y lo manda al swap).
 *  - Si el nuevo TL es <= relojGlobal, GestorProcesos le crea el BCP, lo
 *    carga en RAM y lo pasa a READY al aplicar los cambios.
 */
public class VentanaTiemposLlegada extends JDialog {

    private static final int COL_UBICACION = 0;
    private static final int COL_ID        = 1;
    private static final int COL_RAFAGA    = 2;
    private static final int COL_LLEGADA   = 3;
    private static final int COL_PRIORIDAD = 4;

    private static final String UBIC_NEW = "Espera TL=";
    private static final String UBIC_RAM = "En RAM (Cola)";

    // Color para procesos pendientes (fondo amarillo claro)
    private static final Color COLOR_PENDIENTE = new Color(0xFF, 0xF3, 0xCD);
    // Color para celdas bloqueadas (gris claro)
    private static final Color COLOR_BLOQUEADO = new Color(0xEE, 0xEE, 0xEE);

    private JTable tabla;
    private DefaultTableModel modelo;
    private List<Nucleo> nucleos;
    private List<BCP> procesosEnCola;
    private List<TrabajoNuevo> trabajosNuevos;

    private boolean confirmado = false;

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
        super(propietario, "Configurar Tiempos de Llegada y Prioridades", true);
        this.nucleos = nucleos;
        this.procesosEnCola = procesosEnCola;
        this.trabajosNuevos = (trabajosNuevos != null)
                ? trabajosNuevos : new ArrayList<TrabajoNuevo>();

        construirInterfaz();
        pack();
        setSize(750, 520);
        setResizable(true);
        setLocationRelativeTo(propietario);
    }

    /** true si el TL de una fila con esta ubicacion se puede editar. */
    private static boolean tlEditable(String ubicacion) {
        return ubicacion != null
                && (ubicacion.startsWith(UBIC_NEW) || ubicacion.equals(UBIC_RAM));
    }

    private void construirInterfaz() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBackground(Paleta.FONDO_GENERAL);
        panelPrincipal.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(panelPrincipal);

        JLabel lblTitulo = new JLabel("Asignar Tiempos de Llegada y Prioridades");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.VERDE_OSCURO);
        panelPrincipal.add(lblTitulo, BorderLayout.NORTH);

        // Columnas: Ubicación | ID | Ráfaga | Tiempo Llegada | Prioridad
        modelo = new DefaultTableModel(
                new Object[]{"Ubicación", "ID", "Ráfaga", "Tiempo Llegada", "Prioridad"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // La prioridad siempre se puede editar
                if (column == COL_PRIORIDAD) return true;

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
                        bcp.getRafaga(),
                        bcp.getTiempoLlegada(),
                        bcp.getPrioridad()
                    });
                }
            }
        }

        // 2. Procesos en RAM (READY)
        for (BCP bcp : procesosEnCola) {
            modelo.addRow(new Object[]{
                UBIC_RAM,
                bcp.getId(),
                bcp.getRafaga(),
                bcp.getTiempoLlegada(),
                bcp.getPrioridad()
            });
        }

        // 3. Procesos en NEW (desde TrabajoNuevo, sin BCP)
        for (TrabajoNuevo t : trabajosNuevos) {
            modelo.addRow(new Object[]{
                UBIC_NEW + t.getTiempoLlegada(),
                t.getId(),
                t.getTamano(),
                t.getTiempoLlegada(),
                t.getPrioridad()
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
                        } else if (ubicacion.startsWith(UBIC_NEW)) {
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

        JScrollPane scroll = new JScrollPane(tabla);
        panelPrincipal.add(scroll, BorderLayout.CENTER);

        // Botones
        JButton btnAceptar = new JButton("Aplicar");
        JButton btnCancelar = new JButton("Cancelar");

        btnAceptar.addActionListener(e -> confirmar());
        btnCancelar.addActionListener(e -> dispose());

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setOpaque(false);
        panelBotones.add(btnCancelar);
        panelBotones.add(btnAceptar);

        panelPrincipal.add(panelBotones, BorderLayout.SOUTH);
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
            }
        } catch (NumberFormatException e) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Los tiempos de llegada y las prioridades deben ser números enteros.",
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
        Object[][] tiempos = new Object[modelo.getRowCount()][2];
        for (int i = 0; i < modelo.getRowCount(); i++) {
            int id = Integer.parseInt(modelo.getValueAt(i, COL_ID).toString());
            int tiempo = Integer.parseInt(modelo.getValueAt(i, COL_LLEGADA).toString().trim());
            tiempos[i][0] = id;
            tiempos[i][1] = tiempo;
        }
        return tiempos;
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