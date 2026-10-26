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

public class VentanaConfiguracion extends JDialog {

    private PanelConfigMemoria panelMemoria;
    private PanelConfigDisco panelDisco;
    private PanelConfigCPU panelCPU; // <-- Usamos el nuevo panel
    private JTabbedPane tabs;

    private boolean confirmado;

    private int tamanoMemoria;
    private int limiteKernel;
    private int tamanoDisco;
    private int maxArchivos;
    private int tamanoSwap;
    private int cantidadCPUs;
    private String algoritmoCPU;

    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual,
                                 int maxProcesosActual,
                                 int cpusActual,
                                 String algoritmoActual) {
        super(propietario, "Configuracion", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoMemoriaActual;
        this.limiteKernel = limiteKernelActual;
        this.tamanoDisco = tamanoDiscoActual;
        this.maxArchivos = maxArchivosActual;
        this.tamanoSwap = tamanoSwapActual;
        this.cantidadCPUs = cpusActual;
        this.algoritmoCPU = algoritmoActual;

        construirInterfaz(tamanoMemoriaActual, limiteKernelActual,
                          tamanoDiscoActual, maxArchivosActual,
                          tamanoSwapActual, maxProcesosActual, cpusActual, algoritmoActual);
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    private void construirInterfaz(int tamanoMemoriaActual,
                                    int limiteKernelActual,
                                    int tamanoDiscoActual,
                                    int maxArchivosActual,
                                    int tamanoSwapActual,
                                    int maxProcesosActual,
                                    int cpusActual,
                                    String algoritmoActual) {
        panelMemoria = new PanelConfigMemoria(
                tamanoMemoriaActual, limiteKernelActual,
                maxProcesosActual, maxArchivosActual);

        panelDisco = new PanelConfigDisco(
                tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

        panelCPU = new PanelConfigCPU(cpusActual, algoritmoActual);

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
}