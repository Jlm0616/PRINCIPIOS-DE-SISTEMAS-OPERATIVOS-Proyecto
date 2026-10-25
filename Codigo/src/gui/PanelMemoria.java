package gui;

import modelo.BCP;
import modelo.Instruccion;
import modelo.Memoria;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel que muestra el contenido de la memoria principal.
 *
 * La fila del IR se resalta con el color de la CPU que lo está ejecutando.
 */
public class PanelMemoria extends JPanel {

    private JTable tabla;
    private DefaultTableModel modelo;
    private JButton btnToggle;
    private JProgressBar barraUso;
    private JLabel lblUso;
    private boolean mostrarSoloOcupadas = false;

    private Memoria memoriaActual;
    
    // Mapa para guardar qué CPU tiene su IR en qué posición de memoria
    // Clave: posición de memoria, Valor: ID de la CPU
    private Map<Integer, Integer> irPorCPU = new HashMap<>();

    /* Colores suaves por zona */
    private static final Color COLOR_LISTA_TRABAJOS = new Color(0xE3, 0xF2, 0xFD);  // azul muy claro
    private static final Color COLOR_BCP            = new Color(0xFF, 0xF8, 0xE1);  // amarillo muy claro
    private static final Color COLOR_TABLA_MEM      = new Color(0xF3, 0xE5, 0xF5);  // lila muy claro
    private static final Color COLOR_USUARIO        = Color.WHITE;
    private static final Color COLOR_LIBRE          = new Color(0xF0, 0xF0, 0xF0);  // gris muy claro

    // Crea el panel y construye su interfaz gráfica.
    public PanelMemoria() {
        construirInterfaz();
    }

    // Construye la interfaz del panel (barra de uso + tabla + leyenda).
    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Memoria principal",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(5, 5, 5, 5)
        ));

        // === Arriba: barra de uso ===
        JPanel panelUso = new JPanel(new BorderLayout(5, 2));
        panelUso.setOpaque(false);

        barraUso = new JProgressBar(0, 100);
        barraUso.setStringPainted(true);
        barraUso.setForeground(Paleta.VERDE_PRINCIPAL);
        barraUso.setBackground(new Color(0xE0, 0xE0, 0xE0));
        barraUso.setFont(Paleta.FUENTE_LABEL_BOLD);
        barraUso.setPreferredSize(new Dimension(0, 22));
        barraUso.setValue(0);
        barraUso.setString("0%");

        lblUso = new JLabel("0 / 0 posiciones usadas");
        lblUso.setFont(Paleta.FUENTE_LABEL);
        lblUso.setForeground(Paleta.TEXTO_NORMAL);

        panelUso.add(barraUso, BorderLayout.CENTER);
        panelUso.add(lblUso, BorderLayout.SOUTH);

        add(panelUso, BorderLayout.NORTH);

        // === Centro: tabla ===
        modelo = new DefaultTableModel(new Object[]{"Pos", "Zona", "Valor"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(22);
        tabla.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tabla.setShowGrid(true);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);

        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(100);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(350);

        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (isSelected) return c;

                Object posObj = table.getValueAt(row, 0);
                Object zonaObj = table.getValueAt(row, 1);
                String zona = (zonaObj != null) ? zonaObj.toString() : "";
                int pos = (posObj instanceof Integer) ? (Integer) posObj : -1;

                // ¿Esta posición es el IR de alguna CPU?
                if (irPorCPU.containsKey(pos)) {
                    int idCPU = irPorCPU.get(pos);
                    c.setBackground(Paleta.getColorCPU(idCPU));
                    c.setForeground(Color.BLACK);
                    setFont(Paleta.FUENTE_MONO_BOLD);
                    return c;
                }

                c.setBackground(colorDeZona(zona));
                c.setForeground(Paleta.TEXTO_NORMAL);
                setFont(Paleta.FUENTE_MONO);
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        add(scroll, BorderLayout.CENTER);

        // === Abajo: boton toggle ===
        btnToggle = new JButton("Mostrar solo ocupadas");
        btnToggle.setFont(Paleta.FUENTE_BOTON);
        btnToggle.setBackground(Paleta.VERDE_PRINCIPAL);
        btnToggle.setForeground(Paleta.TEXTO_CLARO);
        btnToggle.setFocusPainted(false);
        btnToggle.addActionListener(e -> {
            mostrarSoloOcupadas = !mostrarSoloOcupadas;
            btnToggle.setText(mostrarSoloOcupadas
                    ? "Mostrar todas" : "Mostrar solo ocupadas");
            refrescar();
        });

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelBoton.setOpaque(false);
        panelBoton.add(btnToggle);

        add(panelBoton, BorderLayout.SOUTH);
    }

    // Devuelve el color de fondo correspondiente a la zona.
    private Color colorDeZona(String zona) {
        if (zona == null) return COLOR_USUARIO;
        switch (zona) {
            case "ListaTrab": return COLOR_LISTA_TRABAJOS;
            case "BCP":       return COLOR_BCP;
            case "TablaMem":  return COLOR_TABLA_MEM;
            case "Usuario":   return COLOR_USUARIO;
            case "Libre":     return COLOR_LIBRE;
            default:          return COLOR_USUARIO;
        }
    }

    // Actualiza el panel con la memoria y los IRs de las CPUs.
    public void actualizar(Memoria memoria, Map<Integer, Integer> irsPorCPU) {
        this.memoriaActual = memoria;
        this.irPorCPU = irsPorCPU;
        refrescar();
        actualizarBarra();
    }

    // Refresca la tabla de la memoria (respetando el filtro activo).
    private void refrescar() {
        if (memoriaActual == null) {
            modelo.setRowCount(0);
            return;
        }

        modelo.setRowCount(0);

        int tamano = memoriaActual.getTamanoMemoria();
        for (int i = 0; i < tamano; i++) {
            Object contenido = memoriaActual.leer(i);
            if (mostrarSoloOcupadas && contenido == null) {
                continue;
            }
            String zona = calcularZona(i);
            String valor = formatearContenido(i, contenido);
            modelo.addRow(new Object[]{i, zona, valor});
        }
    }

    // Determina a qué zona pertenece una posición de memoria.
    private String calcularZona(int pos) {
        if (memoriaActual == null) return "";

        if (pos >= memoriaActual.getInicioListaTrabajos()
                && pos <= memoriaActual.getFinListaTrabajos()) {
            return "ListaTrab";
        }
        if (pos >= memoriaActual.getInicioBCPs()
                && pos <= memoriaActual.getFinBCPs()) {
            return "BCP";
        }
        if (pos >= memoriaActual.getInicioTablaMemoria()
                && pos <= memoriaActual.getFinTablaMemoria()) {
            return "TablaMem";
        }
        if (pos >= memoriaActual.getLimiteKernelUsuario()
                && pos < memoriaActual.getTamanoMemoria()) {
            return "Usuario";
        }
        if (pos < memoriaActual.getLimiteKernelUsuario()) {
            return "Libre";
        }
        return "?";
    }

    // Actualiza la barra de uso de memoria.
    private void actualizarBarra() {
        if (memoriaActual == null) {
            barraUso.setValue(0);
            barraUso.setString("0%");
            lblUso.setText("0 / 0 posiciones usadas");
            return;
        }

        int total = memoriaActual.getTamanoMemoria();
        int usadas = 0;
        for (int i = 0; i < total; i++) {
            if (memoriaActual.leer(i) != null) {
                usadas++;
            }
        }

        int porcentaje = (total > 0) ? (usadas * 100) / total : 0;
        barraUso.setValue(porcentaje);
        barraUso.setString(porcentaje + "%");
        lblUso.setText(usadas + " / " + total + " posiciones usadas");
    }

    /**
     * Formatea el contenido de una posición de memoria.
     */
    private String formatearContenido(int pos, Object contenido) {
        if (contenido == null) {
            return "(vacio)";
        }
        if (contenido instanceof Instruccion) {
            return contenido.toString();
        }

        // Caso 1: ¿Es parte de la ListaDeTrabajos?
        if (memoriaActual != null
                && pos >= memoriaActual.getInicioListaTrabajos()
                && pos <= memoriaActual.getFinListaTrabajos()) {
            return formatearListaTrabajo(pos, contenido);
        }

        // Caso 2: ¿Es parte de un BCP?
        BCP bcp = encontrarBCPPorPosicion(pos);
        if (bcp != null) {
            int offset = pos - bcp.getDireccionBase();
            if (offset == 0) {
                return "=== BCP id=" + bcp.getId() + " ===";
            }
            if (offset > 0 && offset < BCP.POSICIONES_REQUERIDAS) {
                return nombreCampoBCP(offset) + ": " + valorCampoBCP(bcp, offset);
            }
        }

        // Caso 3: ¿Es parte de la TablaMemoria?
        if (memoriaActual != null
                && pos >= memoriaActual.getInicioTablaMemoria()
                && pos <= memoriaActual.getFinTablaMemoria()) {
            return formatearTablaMemoria(pos, contenido);
        }

        return contenido.toString();
    }

    /**
     * Formatea una posición de la ListaDeTrabajos.
     */
    private String formatearListaTrabajo(int pos, Object contenido) {
        int inicio = memoriaActual.getInicioListaTrabajos();
        int offset = pos - inicio;

        int posicionesPorEntrada = Memoria.POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;
        int numEntrada = offset / posicionesPorEntrada;
        int posEnEntrada = offset % posicionesPorEntrada;

        switch (posEnEntrada) {
            case 0: return "Trabajo " + numEntrada + " -> " + contenido;
            case 1: return "  inicio: " + contenido;
            case 2: return "  fin: " + contenido;
            case 3: return "  zona: " + contenido;
            default: return contenido.toString();
        }
    }

    /**
     * Formatea una posición de la TablaMemoria.
     */
    private String formatearTablaMemoria(int pos, Object contenido) {
        int inicio = memoriaActual.getInicioTablaMemoria();
        int offset = pos - inicio;

        int posEnBloque = offset % 3;

        switch (posEnBloque) {
            case 0: return "ID Proceso: " + contenido;
            case 1: return "Inicio: " + contenido;
            case 2: return "Tamano: " + contenido;
            default: return contenido.toString();
        }
    }

    /**
     * Busca el BCP al que pertenece una posición de memoria.
     */
    private BCP encontrarBCPPorPosicion(int pos) {
        if (memoriaActual == null) return null;

        int inicioBCPs = memoriaActual.getInicioBCPs();
        int finBCPs = memoriaActual.getFinBCPs();

        if (pos < inicioBCPs || pos > finBCPs) return null;

        for (int base = inicioBCPs;
             base + BCP.POSICIONES_REQUERIDAS - 1 <= finBCPs;
             base += BCP.POSICIONES_REQUERIDAS) {

            if (pos >= base && pos < base + BCP.POSICIONES_REQUERIDAS) {
                Object idObj = memoriaActual.leer(base + BCP.OFF_ID);
                if (idObj instanceof Integer) {
                    return new BCP(memoriaActual, base);
                }
                return null;
            }
        }
        return null;
    }

    // Devuelve el nombre legible del campo de un BCP según su offset.
    private String nombreCampoBCP(int offset) {
        switch (offset) {
            case 0:  return "ID";
            case 1:  return "Estado";
            case 2:  return "Prioridad";
            case 3:  return "PC";
            case 4:  return "IR";
            case 5:  return "AC";
            case 6:  return "AX";
            case 7:  return "BX";
            case 8:  return "CX";
            case 9:  return "DX";
            case 10: return "Overflow";
            case 11: return "BanderaIgual";
            case 12: return "PesoPendiente";
            case 13: return "Pila[0]";
            case 14: return "Pila[1]";
            case 15: return "Pila[2]";
            case 16: return "Pila[3]";
            case 17: return "Pila[4]";
            case 18: return "Base";
            case 19: return "Alcance";
            case 20: return "TiempoInicio";
            case 21: return "TiempoFin";
            case 22: return "CPUAsignado";
            case 23: return "ArchivoAbierto[0]";
            case 24: return "ArchivoAbierto[1]";
            case 25: return "ArchivoAbierto[2]";
            case 26: return "ArchivoAbierto[3]";
            case 27: return "ArchivoAbierto[4]";
            case 28: return "SiguienteBCP";
            case 29: return "Direccion";
            case 30: return "TiempoLlegada";
            case 31: return "Rafaga";
            case 32: return "TiempoRestante";
            case 33: return "Turnaround";
            case 34: return "Tr/Ts";
            default: return "Campo" + offset;
        }
    }

    // Devuelve el valor de un campo de un BCP como texto.
    private String valorCampoBCP(BCP bcp, int offset) {
        Object v = bcp.getMemoria().leer(bcp.getDireccionBase() + offset);
        if (v == null) return "(vacio)";
        return v.toString();
    }
}