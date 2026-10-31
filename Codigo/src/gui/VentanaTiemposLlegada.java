package gui;

import logica.cpu.Nucleo;
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
import java.util.List;

public class VentanaTiemposLlegada extends JDialog {

    private JTable tabla;
    private DefaultTableModel modelo;
    private List<Nucleo> nucleos;
    private List<BCP> procesosEnCola;
    
    private boolean confirmado = false;

    public VentanaTiemposLlegada(Frame propietario, List<Nucleo> nucleos, List<BCP> procesosEnCola) {
        super(propietario, "Configurar Tiempos de Llegada", true);
        this.nucleos = nucleos;
        this.procesosEnCola = procesosEnCola;

        construirInterfaz();
        pack();
        setSize(600, 450);
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    private void construirInterfaz() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBackground(Paleta.FONDO_GENERAL);
        panelPrincipal.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(panelPrincipal);

        JLabel lblTitulo = new JLabel("Asignar Tiempos de Llegada (segundos)");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.VERDE_OSCURO);
        panelPrincipal.add(lblTitulo, BorderLayout.NORTH);

        // Columnas: Ubicación | ID | Ráfaga | Tiempo Llegada
        modelo = new DefaultTableModel(new Object[]{"Ubicación", "ID", "Ráfaga", "Tiempo Llegada"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Solo Tiempo Llegada es editable
            }
        };

        // 1. Cargar procesos que están en las CPUs
        for (Nucleo nucleo : nucleos) {
            if (nucleo.estaOcupado()) {
                BCP bcp = nucleo.getBcpActual();
                if (bcp != null) {
                    modelo.addRow(new Object[]{
                        "CPU " + nucleo.getIdNucleo(), 
                        bcp.getId(), 
                        bcp.getRafaga(), 
                        bcp.getTiempoLlegada()
                    });
                }
            }
        }

        // 2. Cargar procesos en la cola de listos (READY)
        for (BCP bcp : procesosEnCola) {
            modelo.addRow(new Object[]{
                "En RAM (Cola)", 
                bcp.getId(), 
                bcp.getRafaga(), 
                bcp.getTiempoLlegada()
            });
        }

        tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(25);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);
        
        // Renderizador para pintar la columna "Ubicación" con el color de la CPU
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    if (column == 0) { // Columna Ubicación
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
                            }
                        } else {
                            c.setBackground(Color.WHITE);
                            c.setForeground(Paleta.TEXTO_NORMAL);
                            setFont(Paleta.FUENTE_MONO);
                        }
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
        JButton btnAceptar = new JButton("Aplicar Tiempos");
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
        // Validar que todos los tiempos sean números
        try {
            for (int i = 0; i < modelo.getRowCount(); i++) {
                Integer.parseInt(modelo.getValueAt(i, 3).toString().trim());
            }
        } catch (NumberFormatException e) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Todos los tiempos de llegada deben ser números enteros.",
                    "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        confirmado = true;
        dispose();
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    // Devuelve un arreglo con los tiempos ingresados [ID, TiempoLlegada]
    public Object[][] getTiempos() {
        Object[][] tiempos = new Object[modelo.getRowCount()][2];
        for (int i = 0; i < modelo.getRowCount(); i++) {
            int id = Integer.parseInt(modelo.getValueAt(i, 1).toString()); // Columna 1 ahora es ID
            int tiempo = Integer.parseInt(modelo.getValueAt(i, 3).toString().trim()); // Columna 3 es tiempo
            tiempos[i][0] = id;
            tiempos[i][1] = tiempo;
        }
        return tiempos;
    }
}