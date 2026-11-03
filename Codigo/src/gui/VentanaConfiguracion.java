package gui;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;

/**
 * Ventana de configuracion del sistema.
 *
 * Pestañas: Memoria, Disco, CPUs.
 * La pestaña de CPUs permite elegir el algoritmo y, si es RR o
 * Selfish RR, configurar el quantum (y a, b para Selfish RR).
 */
public class VentanaConfiguracion extends JDialog {

    private static final int QUANTUM_DEFAULT = 1;

    private PanelConfigMemoria panelMemoria;
    private PanelConfigDisco panelDisco;
    private PanelConfigCPU panelCPU;
    private JTabbedPane tabs;

    private boolean confirmado;

    private int tamanoMemoria;
    private int limiteKernel;
    private int tamanoDisco;
    private int maxArchivos;
    private int tamanoSwap;
    private int cantidadCPUs;
    private String algoritmoCPU;
    private int quantumRR;
    private int srrA;
    private int srrB;

    /** Constructor legacy (sin quantum). Usa QUANTUM_DEFAULT. */
    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual,
                                 int cpusActual,
                                 String algoritmoActual) {
        this(propietario, tamanoMemoriaActual, limiteKernelActual,
             tamanoDiscoActual, maxArchivosActual, tamanoSwapActual,
             cpusActual, algoritmoActual, QUANTUM_DEFAULT);
    }

    /** Constructor con quantum (a y b de Selfish RR por defecto). */
    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual,
                                 int cpusActual,
                                 String algoritmoActual,
                                 int quantumActual) {
        this(propietario, tamanoMemoriaActual, limiteKernelActual,
             tamanoDiscoActual, maxArchivosActual, tamanoSwapActual,
             cpusActual, algoritmoActual, quantumActual,
             config.ConfiguracionExterna.SRR_A_DEFAULT,
             config.ConfiguracionExterna.SRR_B_DEFAULT);
    }

    /** Constructor completo (con quantum y a, b de Selfish RR). */
    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual,
                                 int cpusActual,
                                 String algoritmoActual,
                                 int quantumActual,
                                 int srrAActual,
                                 int srrBActual) {
        super(propietario, "Configuracion", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoMemoriaActual;
        this.limiteKernel = limiteKernelActual;
        this.tamanoDisco = tamanoDiscoActual;
        this.maxArchivos = maxArchivosActual;
        this.tamanoSwap = tamanoSwapActual;
        this.cantidadCPUs = cpusActual;
        this.algoritmoCPU = algoritmoActual;
        this.quantumRR = (quantumActual >= 1) ? quantumActual : QUANTUM_DEFAULT;
        this.srrA = srrAActual;
        this.srrB = srrBActual;

        construirInterfaz(tamanoMemoriaActual, limiteKernelActual,
                          tamanoDiscoActual, maxArchivosActual,
                          tamanoSwapActual, cpusActual, algoritmoActual,
                          this.quantumRR, srrAActual, srrBActual);
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    private void construirInterfaz(int tamanoMemoriaActual,
                                    int limiteKernelActual,
                                    int tamanoDiscoActual,
                                    int maxArchivosActual,
                                    int tamanoSwapActual,
                                    int cpusActual,
                                    String algoritmoActual,
                                    int quantumActual,
                                    int srrAActual,
                                    int srrBActual) {

        // Ambos paneles reciben cpusActual para calcular sus mínimos dinámicos
        panelMemoria = new PanelConfigMemoria(
                tamanoMemoriaActual, limiteKernelActual,
                maxArchivosActual, cpusActual);

        panelDisco = new PanelConfigDisco(
                tamanoDiscoActual, maxArchivosActual, tamanoSwapActual, cpusActual);

        panelCPU = new PanelConfigCPU(cpusActual, algoritmoActual, quantumActual,
                                      srrAActual, srrBActual);

        // === Callback para actualizar paneles cuando cambie el número de CPUs ===
        panelCPU.setOnCpusChanged(() -> {
            int nuevasCpus = panelCPU.getCantidadCPUs();

            // Guardar valores actuales antes de recrear
            int memActual = tamanoMemoriaActual;
            int kernelActual = limiteKernelActual;
            int discoActual = tamanoDiscoActual;
            int archivosActual = maxArchivosActual;
            int swapActual = tamanoSwapActual;

            try {
                if (panelMemoria != null) {
                    memActual = panelMemoria.getTamanoMemoria();
                    kernelActual = panelMemoria.getLimiteKernel();
                }
                if (panelDisco != null) {
                    discoActual = panelDisco.getTamanoDisco();
                    archivosActual = panelDisco.getMaxArchivos();
                    swapActual = panelDisco.getTamanoSwap();
                }
            } catch (Exception ex) {
                memActual = config.ConfiguracionExterna.MEMORIA_DEFAULT;
                discoActual = config.ConfiguracionExterna.DISCO_DEFAULT;
                swapActual = config.ConfiguracionExterna.MEMORIA_VIRTUAL_DEFAULT;
            }

            // Recrear paneles con el nuevo mínimo calculado
            panelMemoria = new PanelConfigMemoria(memActual, kernelActual, archivosActual, nuevasCpus);
            panelDisco = new PanelConfigDisco(discoActual, archivosActual, swapActual, nuevasCpus);

            // Reemplazar en las pestañas
            tabs.setComponentAt(0, panelMemoria);
            tabs.setComponentAt(1, panelDisco);
            tabs.setTitleAt(0, "Memoria");
            tabs.setTitleAt(1, "Disco");

            tabs.revalidate();
            tabs.repaint();
            pack();
        });

        tabs = new JTabbedPane();
        tabs.addTab("Memoria", panelMemoria);
        tabs.addTab("Disco", panelDisco);
        tabs.addTab("CPUs", panelCPU);

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

        cantidadCPUs = panelCPU.getCantidadCPUs();
        algoritmoCPU = panelCPU.getAlgoritmoCPU();
        quantumRR = panelCPU.getQuantum();
        srrA = panelCPU.getSrrA();
        srrB = panelCPU.getSrrB();

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

    public String getAlgoritmoCPU() {
        return algoritmoCPU;
    }

    /**
     * @return quantum configurado para RR (>= 1).
     */
    public int getQuantum() {
        return quantumRR;
    }

    /**
     * @return a de Selfish RR: incremento de prioridad de los nuevos.
     */
    public int getSrrA() {
        return srrA;
    }

    /**
     * @return b de Selfish RR: incremento de prioridad de los aceptados.
     */
    public int getSrrB() {
        return srrB;
    }
}