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
 * Para RR, Lottery y Selfish RR, muestra un campo adicional de quantum.
 * Para Selfish RR, muestra además a (incremento de los nuevos) y
 * b (incremento de los aceptados).
 *
 * PROYECTO 2: algoritmos disponibles actualmente:
 *   FCFS, SPN, SRT, RR, HRRN, Prioridad, Selfish RR, Lottery, Guaranteed, MLFQ.
 *
 * SOLID:
 *  - SRP: solo maneja la configuracion visual de CPUs y algoritmo.
 *  - OCP: agregar un nuevo algoritmo es solo agregar al array y, si
 *    necesita parametro extra, mostrar/ocultar su campo.
 *  - ISP: expone solo getters concretos.
 */
public class PanelConfigCPU extends JPanel {

    private static final int QUANTUM_DEFAULT = 1;
    private static final String SELFISH_RR = "Selfish RR";

    private JComboBox<Integer> comboCPUs;
    private JComboBox<String> comboAlgoritmo;

    // Campo de quantum (solo visible cuando se selecciona RR, Lottery o Selfish RR)
    private JLabel lblQuantum;
    private JTextField txtQuantum;

    // Campos de Selfish RR (solo visibles cuando se selecciona Selfish RR)
    private JLabel lblSrrA;
    private JTextField txtSrrA;
    private JLabel lblSrrB;
    private JTextField txtSrrB;

    // Callback para avisar a la ventana cuando cambia la cantidad de CPUs
    private Runnable onCpusChanged;

    // Crea el panel con los valores actuales (quantum default).
    public PanelConfigCPU(int cpusActual, String algoritmoActual) {
        this(cpusActual, algoritmoActual, QUANTUM_DEFAULT);
    }

    // Crea el panel con los valores actuales (incluye quantum).
    public PanelConfigCPU(int cpusActual, String algoritmoActual, int quantumActual) {
        this(cpusActual, algoritmoActual, quantumActual,
             config.ConfiguracionExterna.SRR_A_DEFAULT,
             config.ConfiguracionExterna.SRR_B_DEFAULT);
    }

    // Crea el panel con los valores actuales (incluye quantum y a, b de Selfish RR).
    public PanelConfigCPU(int cpusActual, String algoritmoActual, int quantumActual,
                          int srrAActual, int srrBActual) {
        construirInterfaz(cpusActual, algoritmoActual, quantumActual, srrAActual, srrBActual);
    }

    // Construye la interfaz del panel.
    private void construirInterfaz(int cpusActual, String algoritmoActual, int quantumActual,
                                   int srrAActual, int srrBActual) {
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
            "RR",
            "HRRN",
            "Prioridad",
            SELFISH_RR,
            "Lottery",
            "Guaranteed",
            "MLFQ"
        });
        seleccionarAlgoritmo(algoritmoActual);
        comboAlgoritmo.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(comboAlgoritmo, gbc);

        // ==== Quantum (solo visible para RR, Lottery y Selfish RR) ====
        lblQuantum = crearLabelCampo("Quantum");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        add(lblQuantum, gbc);

        txtQuantum = new JTextField(String.valueOf(quantumActual), 5);
        txtQuantum.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtQuantum, gbc);

        // ==== a y b (solo visibles para Selfish RR) ====
        lblSrrA = crearLabelCampo("a: incremento de prioridad de los nuevos");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        add(lblSrrA, gbc);

        txtSrrA = new JTextField(String.valueOf(srrAActual), 5);
        txtSrrA.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtSrrA, gbc);

        lblSrrB = crearLabelCampo("b: incremento de prioridad de los aceptados");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        add(lblSrrB, gbc);

        txtSrrB = new JTextField(String.valueOf(srrBActual), 5);
        txtSrrB.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtSrrB, gbc);

        // ==== Texto de ayuda ====
        JLabel lblAyuda = new JLabel("<html><body style='width: 300px'>"
                + "El algoritmo seleccionado se aplicará a todas las CPUs.<br>"
                + "Al cambiar el número de CPUs, el mínimo de memoria y disco "
                + "se actualizará automáticamente.<br>"
                + "Selfish RR: un proceso nuevo espera hasta que su prioridad "
                + "alcanza la de los aceptados. Con b = 0 se comporta como RR; "
                + "con b &gt;= a, como FCFS."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAyuda.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 5;
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
     * Muestra u oculta los campos de quantum, a y b según el algoritmo
     * seleccionado. El quantum aplica para RR, Lottery y Selfish RR; a y b
     * solo para Selfish RR.
     */
    private void actualizarVisibilidadQuantum() {
        Object seleccionado = comboAlgoritmo.getSelectedItem();
        boolean esSelfishRR = SELFISH_RR.equals(seleccionado);
        boolean usaQuantum = "RR".equals(seleccionado) || esSelfishRR 
                          || "Lottery".equals(seleccionado);
        
        lblQuantum.setVisible(usaQuantum);
        txtQuantum.setVisible(usaQuantum);
        
        lblSrrA.setVisible(esSelfishRR);
        txtSrrA.setVisible(esSelfishRR);
        lblSrrB.setVisible(esSelfishRR);
        txtSrrB.setVisible(esSelfishRR);
        
        revalidate();
        repaint();
    }

    /**
     * Selecciona el algoritmo en el combo sin distinguir mayúsculas:
     * config.txt lo guarda en mayúsculas ("SELFISH RR", "PRIORIDAD").
     */
    private void seleccionarAlgoritmo(String algoritmo) {
        for (int i = 0; i < comboAlgoritmo.getItemCount(); i++) {
            if (comboAlgoritmo.getItemAt(i).equalsIgnoreCase(algoritmo)) {
                comboAlgoritmo.setSelectedIndex(i);
                return;
            }
        }
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
     * Devuelve el quantum configurado para RR, Lottery o Selfish RR.
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

    /**
     * Devuelve a (incremento de prioridad de los nuevos) para Selfish RR.
     * Si el campo es inválido o está fuera de rango, devuelve el default.
     */
    public int getSrrA() {
        return leerEntero(txtSrrA, config.ConfiguracionExterna.SRR_A_MINIMO,
                config.ConfiguracionExterna.SRR_A_MAXIMO,
                config.ConfiguracionExterna.SRR_A_DEFAULT);
    }

    /**
     * Devuelve b (incremento de prioridad de los aceptados) para Selfish RR.
     * Si el campo es inválido o está fuera de rango, devuelve el default.
     */
    public int getSrrB() {
        return leerEntero(txtSrrB, config.ConfiguracionExterna.SRR_B_MINIMO,
                config.ConfiguracionExterna.SRR_B_MAXIMO,
                config.ConfiguracionExterna.SRR_B_DEFAULT);
    }

    // Lee un entero del campo; si no es número o está fuera de rango, usa el default.
    private int leerEntero(JTextField campo, int minimo, int maximo, int porDefecto) {
        try {
            int v = Integer.parseInt(campo.getText().trim());
            return (v >= minimo && v <= maximo) ? v : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}