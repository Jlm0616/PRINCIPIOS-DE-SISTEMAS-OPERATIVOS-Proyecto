package gui;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Ventana modal de configuracion del sistema.
 *
 * Agrupa los paneles de configuracion en 3 pestañas:
 *   - Memoria principal (PanelConfigMemoria)
 *   - Disco (PanelConfigDisco)
 *   - CPUs (Cantidad de procesadores)
 */
public class VentanaConfiguracion extends JDialog {

    private PanelConfigMemoria panelMemoria;
    private PanelConfigDisco panelDisco;
    private JPanel panelCPUs;
    private JTabbedPane tabs;

    private JComboBox<Integer> comboCPUs;

    private boolean confirmado;

    private int tamanoMemoria;
    private int limiteKernel;
    private int tamanoDisco;
    private int maxArchivos;
    private int tamanoSwap;
    private int cantidadCPUs;

    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual,
                                 int maxProcesosActual,
                                 int cpusActual) {
        super(propietario, "Configuracion", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoMemoriaActual;
        this.limiteKernel = limiteKernelActual;
        this.tamanoDisco = tamanoDiscoActual;
        this.maxArchivos = maxArchivosActual;
        this.tamanoSwap = tamanoSwapActual;
        this.cantidadCPUs = cpusActual;

        construirInterfaz(tamanoMemoriaActual, limiteKernelActual,
                          tamanoDiscoActual, maxArchivosActual,
                          tamanoSwapActual, maxProcesosActual, cpusActual);
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    // Construye la interfaz: pestañas Memoria/Disco/CPUs + botones Aceptar/Cancelar.
    private void construirInterfaz(int tamanoMemoriaActual,
                                    int limiteKernelActual,
                                    int tamanoDiscoActual,
                                    int maxArchivosActual,
                                    int tamanoSwapActual,
                                    int maxProcesosActual,
                                    int cpusActual) {
        panelMemoria = new PanelConfigMemoria(
                tamanoMemoriaActual, limiteKernelActual,
                maxProcesosActual, maxArchivosActual);

        panelDisco = new PanelConfigDisco(
                tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

        panelCPUs = crearPanelCPUs(cpusActual);

        tabs = new JTabbedPane();
        tabs.addTab("Memoria", panelMemoria);
        tabs.addTab("Disco", panelDisco);
        tabs.addTab("CPUs", panelCPUs);

        JButton btnAceptar = new JButton("Aceptar");
        JButton btnCancelar = new JButton("Cancelar");
        btnAceptar.addActionListener(e -> validarYConfirmar());
        btnCancelar.addActionListener(e -> {
            confirmado = false;
            dispose();
        });

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panelBotones.setBorder(new EmptyBorder(0, 20, 15, 20));
        panelBotones.add(btnCancelar);
        panelBotones.add(btnAceptar);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);
    }

    // Crea el panel para seleccionar la cantidad de CPUs
    private JPanel crearPanelCPUs(int cpusActual) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Paleta.FONDO_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
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

        JLabel lblInfo = new JLabel("Cantidad de núcleos a simular (1 a 5)");
        lblInfo.setFont(Paleta.FUENTE_LABEL_BOLD);
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(lblInfo, gbc);

        comboCPUs = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
        comboCPUs.setSelectedItem(cpusActual);
        comboCPUs.setFont(Paleta.FUENTE_MONO);
        gbc.gridx = 1;
        panel.add(comboCPUs, gbc);

        return panel;
    }

    // Valida ambos paneles y cierra si todo es correcto.
    private void validarYConfirmar() {
        if (!panelMemoria.validar()) {
            tabs.setSelectedComponent(panelMemoria);
            return;
        }
        if (!panelDisco.validar()) {
            tabs.setSelectedComponent(panelDisco);
            return;
        }

        int limiteKernel = panelMemoria.getLimiteKernel();
        int maxArchivos = panelDisco.getMaxArchivos();
        int kernelMinimo = modelo.Memoria.getTamanoKernelMinimo(1, maxArchivos);

        if (limiteKernel < kernelMinimo) {
            int maxArchivosPosibles = modelo.Memoria.calcularMaxArchivosQueCaben(limiteKernel);
            JOptionPane.showMessageDialog(this,
                    "Configuracion invalida:\n\n"
                    + "El kernel no alcanza para " + maxArchivos + " archivos + 1 proceso.\n"
                    + "Minimo requerido: " + kernelMinimo + " posiciones.\n\n"
                    + "Reduce max_archivos a " + maxArchivosPosibles + " o menos.",
                    "Datos invalidos",
                    JOptionPane.ERROR_MESSAGE);
            tabs.setSelectedComponent(panelDisco);
            return;
        }

        // Guardar valores
        tamanoMemoria = panelMemoria.getTamanoMemoria();
        this.limiteKernel = limiteKernel;
        tamanoDisco = panelDisco.getTamanoDisco();
        this.maxArchivos = maxArchivos;
        tamanoSwap = panelDisco.getTamanoSwap();
        cantidadCPUs = (Integer) comboCPUs.getSelectedItem();
        
        confirmado = true;
        dispose();
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    public int getTamanoMemoria() {
        return tamanoMemoria;
    }

    public int getLimiteKernel() {
        return limiteKernel;
    }

    public int getTamanoDisco() {
        return tamanoDisco;
    }

    public int getMaxArchivos() {
        return maxArchivos;
    }

    public int getTamanoSwap() {
        return tamanoSwap;
    }

    public int getCantidadCPUs() {
        return cantidadCPUs;
    }
}