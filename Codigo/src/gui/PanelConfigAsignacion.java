package gui;

import logica.memoria.ParametrosMemoria;
import logica.memoria.Paginacion;
import logica.memoria.PaginacionVirtual;
import logica.memoria.ParticionesFijas;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * Pestaña de configuracion de la ESTRATEGIA DE ASIGNACION de la memoria
 * de usuario y sus parametros (requisito 8 del enunciado).
 *
 * Los campos de parametros solo se muestran para la estrategia que los
 * usa:
 *   - Fija igual:          tamano de particion
 *   - Fija distinta:       lista de tamanos ("8,16,32,64")
 *   - Paginacion:          tamano de pagina
 *   - Paginacion virtual:  tamano de pagina, marcos por proceso, reemplazo
 *   - First / Next / Best Fit no tienen parametros.
 */
public class PanelConfigAsignacion extends JPanel {

    private final JComboBox<String> comboEstrategia;
    private final JComboBox<String> comboReemplazo;
    private final JTextField txtParticion;
    private final JTextField txtParticiones;
    private final JTextField txtPagina;
    private final JTextField txtMarcos;

    private final JLabel lblParticion;
    private final JLabel lblParticiones;
    private final JLabel lblPagina;
    private final JLabel lblMarcos;
    private final JLabel lblReemplazo;
    private final JLabel lblDescripcion;

    // Crea el panel con los valores actuales.
    public PanelConfigAsignacion(ParametrosMemoria actual) {
        if (actual == null) actual = ParametrosMemoria.porDefecto();

        setLayout(new GridBagLayout());
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Asignacion de la memoria de usuario",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(15, 20, 15, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        comboEstrategia = new JComboBox<>(ParametrosMemoria.ESTRATEGIAS);
        comboEstrategia.setSelectedItem(actual.getEstrategia());
        comboEstrategia.setFont(Paleta.FUENTE_MONO);
        fila(gbc, 0, crearLabelCampo("Estrategia de asignacion"), comboEstrategia);

        lblParticion = crearLabelCampo("Tamano de cada particion");
        txtParticion = campo(String.valueOf(actual.getParticionTamano()));
        fila(gbc, 1, lblParticion, txtParticion);

        lblParticiones = crearLabelCampo("Tamanos de las particiones (separados por coma)");
        txtParticiones = campo(actual.getParticiones());
        fila(gbc, 2, lblParticiones, txtParticiones);

        lblPagina = crearLabelCampo("Tamano de pagina (y de marco)");
        txtPagina = campo(String.valueOf(actual.getPaginaTamano()));
        fila(gbc, 3, lblPagina, txtPagina);

        lblMarcos = crearLabelCampo("Marcos por proceso");
        txtMarcos = campo(String.valueOf(actual.getMarcosPorProceso()));
        fila(gbc, 4, lblMarcos, txtMarcos);

        lblReemplazo = crearLabelCampo("Algoritmo de reemplazo");
        comboReemplazo = new JComboBox<>(ParametrosMemoria.REEMPLAZOS);
        comboReemplazo.setSelectedItem(actual.getReemplazo());
        comboReemplazo.setFont(Paleta.FUENTE_MONO);
        fila(gbc, 5, lblReemplazo, comboReemplazo);

        lblDescripcion = new JLabel();
        lblDescripcion.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblDescripcion.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 6, 6, 6);
        add(lblDescripcion, gbc);

        comboEstrategia.addActionListener(e -> actualizarVisibilidad());
        actualizarVisibilidad();
    }

    /**
     * Revisa los valores. Si algo esta mal, muestra el problema y devuelve
     * false (el dialogo no se cierra).
     */
    public boolean validar() {
        List<String> errores;
        try {
            errores = getParametrosMemoria().validar();
        } catch (NumberFormatException e) {
            errores = List.of("Los tamanos y los marcos deben ser numeros enteros.");
        }
        if (errores.isEmpty()) return true;
        JOptionPane.showMessageDialog(this, String.join("\n", errores),
                "Asignacion de memoria", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * @return la estrategia elegida y sus parametros.
     * @throws NumberFormatException si un campo numerico no es un entero
     */
    public ParametrosMemoria getParametrosMemoria() {
        return new ParametrosMemoria(
                (String) comboEstrategia.getSelectedItem(),
                Integer.parseInt(txtParticion.getText().trim()),
                txtParticiones.getText().trim(),
                Integer.parseInt(txtPagina.getText().trim()),
                Integer.parseInt(txtMarcos.getText().trim()),
                (String) comboReemplazo.getSelectedItem());
    }

    // Muestra solo los parametros de la estrategia elegida y su explicacion.
    private void actualizarVisibilidad() {
        String e = (String) comboEstrategia.getSelectedItem();
        boolean igual = ParticionesFijas.NOMBRE_IGUAL.equals(e);
        boolean distinta = ParticionesFijas.NOMBRE_DISTINTA.equals(e);
        boolean virtual = PaginacionVirtual.NOMBRE.equals(e);
        boolean pagina = Paginacion.NOMBRE.equals(e) || virtual;

        lblParticion.setVisible(igual);
        txtParticion.setVisible(igual);
        lblParticiones.setVisible(distinta);
        txtParticiones.setVisible(distinta);
        lblPagina.setVisible(pagina);
        txtPagina.setVisible(pagina);
        lblMarcos.setVisible(virtual);
        txtMarcos.setVisible(virtual);
        lblReemplazo.setVisible(virtual);
        comboReemplazo.setVisible(virtual);

        lblDescripcion.setText("<html><body style='width: 290px'>" + explicacion(e) + "</body></html>");
        revalidate();
        repaint();
    }

    // Explicacion corta de cada estrategia (Stallings, cap. 7 y 8).
    private static String explicacion(String e) {
        if (e == null) return "";
        switch (e) {
            case "First Fit":
                return "Dinamica: cada programa ocupa exactamente su tamano. Usa el primer hueco,"
                        + " desde el inicio de la memoria, que alcance (Stallings 7.2).";
            case "Next Fit":
                return "Dinamica: busca desde donde termino la ultima asignacion y usa el siguiente"
                        + " hueco que alcance, dando la vuelta al final (Stallings 7.2).";
            case "Best Fit":
                return "Dinamica: usa el hueco mas pequeno que alcance; deja los sobrantes mas"
                        + " pequenos (Stallings 7.2).";
            case ParticionesFijas.NOMBRE_IGUAL:
                return "La zona de usuario se divide en particiones del mismo tamano. Un programa"
                        + " mas grande que la particion se rechaza; lo que sobra dentro es"
                        + " fragmentacion interna (Stallings 7.2).";
            case ParticionesFijas.NOMBRE_DISTINTA:
                return "Particiones de los tamanos indicados, en ese orden. Cada programa va a la"
                        + " particion libre mas pequena donde quepa (una sola cola, Figura 7.3b).";
            case Paginacion.NOMBRE:
                return "La memoria se divide en marcos y cada programa en paginas del mismo tamano."
                        + " Se cargan todas las paginas, en cualquier marco libre (Stallings 7.3).";
            case PaginacionVirtual.NOMBRE:
                return "Paginacion por demanda: al cargar solo entra la pagina 0. Si el PC cae en"
                        + " una pagina que no esta hay un fallo de pagina (el proceso pierde ese"
                        + " segundo). Cada proceso tiene un numero fijo de marcos y reemplaza sus"
                        + " propias paginas con FIFO o LRU (Stallings 8.2).";
            default:
                return "";
        }
    }

    private void fila(GridBagConstraints gbc, int y, JLabel etiqueta, java.awt.Component campo) {
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.weightx = 0;
        add(etiqueta, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(campo, gbc);
    }

    private JTextField campo(String valor) {
        JTextField t = new JTextField(valor, 12);
        t.setFont(Paleta.FUENTE_MONO);
        return t;
    }

    // Crea una etiqueta con el estilo del formulario.
    private JLabel crearLabelCampo(String texto) {
        JLabel label = new JLabel(texto + ":");
        label.setFont(Paleta.FUENTE_LABEL_BOLD);
        label.setForeground(Paleta.TEXTO_NORMAL);
        return label;
    }
}
