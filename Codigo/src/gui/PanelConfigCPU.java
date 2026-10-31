package gui;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
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
 * Para RR, muestra un campo adicional de quantum.
 *
 * PROYECTO 2: algoritmos disponibles actualmente:
 *   FCFS, SPN, SRT, RR.
 * Se irán agregando los demás (HRRN, Selfish RR, Lottery,
 * Prioridad, Guaranteed, MLFQ).
 *
 * SOLID:
 *  - SRP: solo maneja la configuracion visual de CPUs y algoritmo.
 *  - OCP: agregar un nuevo algoritmo es solo agregar al array y,
 *    si necesita parametro extra, mostrar/ocultar su campo.
 *  - ISP: expone solo getters concretos.
 */
public class PanelConfigCPU extends JPanel {

    private static final int QUANTUM_DEFAULT = 1;

    private JComboBox<Integer> comboCPUs;
    private JComboBox<String> comboAlgoritmo;

    // Campo de quantum (solo visible cuando se selecciona RR)
    private JLabel lblQuantum;
    private JTextField txtQuantum;

    // Callback para avisar a la ventana cuando cambia la cantidad de CPUs
    private Runnable onCpusChanged;

    // Crea el panel con los valores actuales (quantum default).
    public PanelConfigCPU(int cpusActual, String algoritmoActual) {
        this(cpusActual, algoritmoActual, QUANTUM_DEFAULT);
    }

    // Crea el panel con los valores actuales (incluye quantum).
    public PanelConfigCPU(int cpusActual, String algoritmoActual, int quantumActual) {
        construirInterfaz(cpusActual, algoritmoActual, quantumActual);
    }

    // Construye la interfaz del panel.
    private void construirInterfaz(int cpusActual, String algoritmoActual, int quantumActual) {
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
            "SPN",
            "SRT",
            "RR"
        });
        comboAlgoritmo.setSelectedItem(algoritmoActual);
        comboAlgoritmo.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(comboAlgoritmo, gbc);

        // ==== Quantum (solo visible para RR) ====
        lblQuantum = crearLabelCampo("Quantum (RR)");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        add(lblQuantum, gbc);

        txtQuantum = new JTextField(String.valueOf(quantumActual), 5);
        txtQuantum.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtQuantum, gbc);

        // ==== Texto de ayuda ====
        JLabel lblAyuda = new JLabel("<html><body style='width: 300px'>"
                + "El algoritmo seleccionado se aplicará a todas las CPUs.<br>"
                + "Al cambiar el número de CPUs, el mínimo de memoria y disco "
                + "se actualizará automáticamente."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAyuda.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 6, 6, 6);
        add(lblAyuda, gbc);

        // ==== Listener: mostrar/ocultar quantum según algoritmo ====
        comboAlgoritmo.addActionListener(e -> actualizarVisibilidadQuantum());
        actualizarVisibilidadQuantum();   // estado inicial

        // ==== Listener para avisar cuando cambie la cantidad de CPUs ====
        comboCPUs.addActionListener(e -> {
            if (onCpusChanged != null) {
                onCpusChanged.run();
            }
        });
    }

    /**
     * Muestra u oculta el campo de quantum según el algoritmo seleccionado.
     * El quantum solo aplica para RR.
     */
    private void actualizarVisibilidadQuantum() {
        boolean esRR = "RR".equals(comboAlgoritmo.getSelectedItem());
        lblQuantum.setVisible(esRR);
        txtQuantum.setVisible(esRR);
        revalidate();
        repaint();
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

    /**
     * Devuelve el quantum configurado para RR.
     * Si el campo es inválido (< 1), devuelve 1 (mínimo).
     *
     * @return quantum >= 1
     */
    public int getQuantum() {
        try {
            int q = Integer.parseInt(txtQuantum.getText().trim());
            return (q >= 1) ? q : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}