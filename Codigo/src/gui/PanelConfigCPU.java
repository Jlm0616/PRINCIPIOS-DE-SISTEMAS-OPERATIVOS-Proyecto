package gui;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion de las CPUs.
 * 
 * Permite elegir la cantidad de núcleos y el algoritmo de planificación.
 */
public class PanelConfigCPU extends JPanel {

    private JComboBox<Integer> comboCPUs;
    private JComboBox<String> comboAlgoritmo;
    
    // Callback para avisar a la ventana cuando cambia la cantidad de CPUs
    private Runnable onCpusChanged;

    // Crea el panel con los valores actuales.
    public PanelConfigCPU(int cpusActual, String algoritmoActual) {
        construirInterfaz(cpusActual, algoritmoActual);
    }

    // Construye la interfaz del panel.
    private void construirInterfaz(int cpusActual, String algoritmoActual) {
        setLayout(new GridBagLayout());
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Procesadores (CPUs)",
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

        // ==== Cantidad de CPUs ====
        JLabel lblCantidad = crearLabelCampo("Cantidad de núcleos a simular (1 a 5)");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(lblCantidad, gbc);

        comboCPUs = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
        comboCPUs.setSelectedItem(cpusActual);
        comboCPUs.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(comboCPUs, gbc);

        // ==== Algoritmo de Planificación ====
        JLabel lblAlgoritmo = crearLabelCampo("Algoritmo de Planificación");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        add(lblAlgoritmo, gbc);

        comboAlgoritmo = new JComboBox<>(new String[]{
            "FCFS", 
            "SPN" 
            // Aquí se irán agregando los demás algoritmos
        });
        comboAlgoritmo.setSelectedItem(algoritmoActual);
        comboAlgoritmo.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(comboAlgoritmo, gbc);

        // ==== Texto de ayuda ====
        JLabel lblAyuda = new JLabel("<html><body style='width: 300px'>"
                + "El algoritmo seleccionado se aplicará a todas las CPUs.<br>"
                + "Al cambiar el número de CPUs, el mínimo de memoria y disco "
                + "se actualizará automáticamente."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAyuda.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 6, 6, 6);
        add(lblAyuda, gbc);
        
        // ==== Listener para avisar cuando cambie la cantidad de CPUs ====
        comboCPUs.addActionListener(e -> {
            if (onCpusChanged != null) {
                onCpusChanged.run();
            }
        });
    }

    // Crea una etiqueta con el estilo del formulario.
    private JLabel crearLabelCampo(String texto) {
        JLabel label = new JLabel(texto + ":");
        label.setFont(Paleta.FUENTE_LABEL_BOLD);
        label.setForeground(Paleta.TEXTO_NORMAL);
        return label;
    }

    // Configura el callback que se ejecuta cuando cambia la cantidad de CPUs.
    public void setOnCpusChanged(Runnable callback) {
        this.onCpusChanged = callback;
    }

    // Devuelve la cantidad de CPUs seleccionada.
    public int getCantidadCPUs() {
        return (Integer) comboCPUs.getSelectedItem();
    }

    // Devuelve el algoritmo seleccionado.
    public String getAlgoritmoCPU() {
        return (String) comboAlgoritmo.getSelectedItem();
    }
}