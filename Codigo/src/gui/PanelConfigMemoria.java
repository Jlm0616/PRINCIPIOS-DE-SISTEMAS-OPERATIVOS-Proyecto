package gui;

import modelo.Memoria;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion de la memoria principal.
 *
 * REGLA: el limite del kernel SIEMPRE debe ser al menos el 30% de la
 * memoria total, y debe poder albergar los BCPs de todas las CPUs.
 */
public class PanelConfigMemoria extends JPanel {

    private JTextField txtTamanoMemoria;
    private JTextField txtLimiteKernel;

    private boolean limiteEditadoManualmente = false;
    private boolean actualizandoAutomaticamente = false;

    /** Porcentaje minimo del kernel respecto a la memoria total. */
    private static final double PORCENTAJE_MINIMO_KERNEL = 0.30;

    /** Tamano maximo permitido de memoria (para evitar valores absurdos). */
    private static final int TAMANO_MAXIMO = 65536;

    /** Posiciones minimas que deben quedar en la zona de usuario. */
    private static final int POSICIONES_MINIMAS_USUARIO = 1;

    /** Cantidad de archivos configurada (para mostrar info en la ayuda). */
    private final int maxArchivos;

    /** Cantidad de CPUs configurada (para calcular el minimo real). */
    private final int cpus;

    /** Memoria minima calculada dinámicamente según las CPUs. */
    private final int memoriaMinimaReal;

    public PanelConfigMemoria(int tamanoActual, int limiteActual,
                               int maxArchivos, int cpus) {
        this.maxArchivos = maxArchivos;
        this.cpus = cpus;
        
        // Calculamos el mínimo requerido según las CPUs
        this.memoriaMinimaReal = config.ConfiguracionExterna.calcularMemoriaMinimaParaCPUs(cpus);
        
        construirInterfaz(tamanoActual, limiteActual);
    }

    // Construye la interfaz gráfica del panel con sus campos y etiquetas.
    private void construirInterfaz(int tamanoActual, int limiteActual) {
        setLayout(new GridBagLayout());
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
                new EmptyBorder(15, 20, 15, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // ==== Etiqueta informativa ====
        int porcentajeMostrar = (int) (PORCENTAJE_MINIMO_KERNEL * 100);
        JLabel lblInfo = new JLabel("Minimo para " + cpus + " CPU(s): " + memoriaMinimaReal
                + "  |  Maximo permitido: " + TAMANO_MAXIMO
                + "  |  Kernel minimo: " + porcentajeMostrar + "%");
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblInfo.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(lblInfo, gbc);
        gbc.gridwidth = 1;

        // ==== Fila 1: Tamano total de memoria ====
        JLabel lblTamano = crearLabelCampo("Tamano total de memoria");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        add(lblTamano, gbc);

        txtTamanoMemoria = crearCampoTexto(String.valueOf(tamanoActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtTamanoMemoria, gbc);

        // ==== Fila 2: Limite Kernel/Usuario ====
        JLabel lblLimite = crearLabelCampo("Limite Kernel/Usuario");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        add(lblLimite, gbc);

        txtLimiteKernel = crearCampoTexto(String.valueOf(limiteActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtLimiteKernel, gbc);

        // ==== Fila 3: Texto de ayuda ====
        int kernelMinimoParaTodas = Memoria.getTamanoKernelMinimo(cpus * 5, maxArchivos);
        int tamanoListaTrabajos = maxArchivos * Memoria.POSICIONES_POR_ENTRADA_LISTA_TRABAJOS;

        JLabel lblAyuda = new JLabel("<html><body style='width: 420px'>"
                + "Posiciones 0 a (limite-1) = zona <b>Kernel</b>.<br>"
                + "Posiciones limite a (tamano-1) = zona <b>Usuario</b>.<br><br>"
                + "El kernel debe ocupar al menos el <b>" + porcentajeMostrar + "%</b> "
                + "de la memoria.<br><br>"
                + "Con <b>" + cpus + " CPUs</b> y <b>" + maxArchivos
                + " archivos</b>, el kernel <b>ideal</b> necesita <b>"
                + kernelMinimoParaTodas + "</b> posiciones para albergar "
                + (cpus * 5) + " BCPs:<br>"
                + "&nbsp;&nbsp;ListaDeTrabajos = " + tamanoListaTrabajos
                + " (" + maxArchivos + " × " + Memoria.POSICIONES_POR_ENTRADA_LISTA_TRABAJOS + ")<br>"
                + "&nbsp;&nbsp;BCPs + TablaMemoria = "
                + (kernelMinimoParaTodas - tamanoListaTrabajos) + "<br><br>"
                + "<b>Si el kernel no alcanza para todos, los procesos sobrantes "
                + "iran a la ListaDeTrabajos (swap) y esperaran a que se libere "
                + "espacio en RAM.</b>"
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAyuda.setForeground(new Color(0x60, 0x60, 0x60));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 6, 6, 6);
        add(lblAyuda, gbc);

        // ==== Listeners ====
        txtTamanoMemoria.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override public void removeUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override public void changedUpdate(DocumentEvent e) { sugerirLimite(); }
        });

        txtLimiteKernel.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { marcarComoManual(); }
            @Override public void removeUpdate(DocumentEvent e) { marcarComoManual(); }
            @Override public void changedUpdate(DocumentEvent e) { marcarComoManual(); }

            private void marcarComoManual() {
                if (!actualizandoAutomaticamente) {
                    limiteEditadoManualmente = true;
                }
            }
        });
    }

    // Crea una etiqueta con el estilo de campo del formulario.
    private JLabel crearLabelCampo(String texto) {
        JLabel label = new JLabel(texto + ":");
        label.setFont(Paleta.FUENTE_LABEL_BOLD);
        label.setForeground(Paleta.TEXTO_NORMAL);
        return label;
    }

    // Crea un campo de texto con el estilo del formulario.
    private JTextField crearCampoTexto(String valorInicial) {
        JTextField campo = new JTextField(valorInicial, 12);
        campo.setFont(Paleta.FUENTE_MONO);
        campo.setForeground(Paleta.TEXTO_NORMAL);
        campo.setBackground(Color.WHITE);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Paleta.VERDE_PRINCIPAL, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return campo;
    }

    // Sugiere automáticamente el 30% de la memoria como límite del kernel.
    private void sugerirLimite() {
        if (limiteEditadoManualmente) return;
        try {
            int tamano = Integer.parseInt(txtTamanoMemoria.getText().trim());
            int sugerido = (int) Math.ceil(tamano * PORCENTAJE_MINIMO_KERNEL);

            actualizandoAutomaticamente = true;
            try {
                txtLimiteKernel.setText(String.valueOf(sugerido));
            } finally {
                actualizandoAutomaticamente = false;
            }
        } catch (NumberFormatException e) {
            // el usuario esta escribiendo, ignorar
        }
    }

    /**
     * Valida los campos del panel.
     */
    public boolean validar() {
        int tamanoIngresado;
        int limiteIngresado;

        try {
            tamanoIngresado = Integer.parseInt(txtTamanoMemoria.getText().trim());
            limiteIngresado = Integer.parseInt(txtLimiteKernel.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("El tamano de memoria y el limite del kernel deben ser numeros enteros.");
            return false;
        }

        if (tamanoIngresado < memoriaMinimaReal) {
            mostrarError("El tamano de memoria debe ser al menos " + memoriaMinimaReal + " posiciones.\n"
                    + "Esto es necesario para soportar " + cpus + " CPU(s) con sus 5 procesos cada una.");
            return false;
        }

        if (tamanoIngresado > TAMANO_MAXIMO) {
            mostrarError("El tamano de memoria no puede superar " + TAMANO_MAXIMO + " posiciones.");
            return false;
        }

        if (limiteIngresado >= tamanoIngresado) {
            mostrarError("El limite del Kernel no puede ser igual o mayor al tamano total.");
            return false;
        }

        int minimoKernel = (int) Math.ceil(tamanoIngresado * PORCENTAJE_MINIMO_KERNEL);
        if (limiteIngresado < minimoKernel) {
            int porcentajeMostrar = (int) (PORCENTAJE_MINIMO_KERNEL * 100);
            mostrarError("El limite del Kernel debe ser al menos el " + porcentajeMostrar
                    + "% de la memoria total (" + minimoKernel + " posiciones para "
                    + tamanoIngresado + " de memoria).");
            return false;
        }

        if (tamanoIngresado - limiteIngresado < POSICIONES_MINIMAS_USUARIO) {
            mostrarError("Debe quedar espacio para al menos " + POSICIONES_MINIMAS_USUARIO
                    + " instruccion(es) en la zona de Usuario.");
            return false;
        }

        // === Validacion: al menos 1 BCP debe caber ===
        int kernelMinimoParaUno = Memoria.getTamanoKernelMinimo(1, maxArchivos);
        if (limiteIngresado < kernelMinimoParaUno) {
            mostrarError("El kernel debe alcanzar al menos para 1 proceso.\n\n"
                    + "Con " + maxArchivos + " archivos configurados, el minimo es "
                    + kernelMinimoParaUno + " posiciones (ListaDeTrabajos + 1 BCP + TablaMemoria).");
            return false;
        }

        return true;
    }

    // Muestra un diálogo modal de error con el mensaje indicado.
    private void mostrarError(String mensaje) {
        javax.swing.JOptionPane.showMessageDialog(this, mensaje,
                "Datos invalidos", javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    // Devuelve el tamaño de memoria ingresado.
    public int getTamanoMemoria() {
        return Integer.parseInt(txtTamanoMemoria.getText().trim());
    }

    // Devuelve el límite kernel/usuario ingresado.
    public int getLimiteKernel() {
        return Integer.parseInt(txtLimiteKernel.getText().trim());
    }
}