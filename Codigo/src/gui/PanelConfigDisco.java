package gui;

import modelo.Disco;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion del disco (almacenamiento secundario).
 *
 * Configura los 3 parametros del disco:
 *   1. Tamaño total del disco (indice + swap + archivos).
 *   2. Cantidad maxima de archivos en el indice.
 *   3. Tamaño de la memoria virtual (swap).
 *
 * El disco mínimo se calcula dinámicamente según la cantidad de CPUs
 * y la configuración de archivos/swap.
 */
public class PanelConfigDisco extends JPanel {

    private JTextField txtTamanoDisco;
    private JTextField txtMaxArchivos;
    private JTextField txtTamanoSwap;

    private static final int TAMANO_DISCO_MAXIMO = 65536;
    private static final int MAX_ARCHIVOS_MINIMO = Disco.MAX_ARCHIVOS_MINIMO;
    private static final int MAX_ARCHIVOS_MAXIMO = 100;
    private static final int TAMANO_SWAP_MINIMO = Disco.TAMANO_SWAP_MINIMO;
    private static final int TAMANO_SWAP_MAXIMO = 65536;

    /** Cantidad de CPUs configurada (para calcular el mínimo real del disco). */
    private final int cpus;

    /** Disco mínimo calculado dinámicamente. */
    private final int discoMinimoReal;

    // Crea el panel con los valores actuales de configuración.
    public PanelConfigDisco(int tamanoActual, int maxArchivosActual, int swapActual, int cpus) {
        this.cpus = cpus;
        
        // Calculamos el mínimo requerido
        this.discoMinimoReal = config.ConfiguracionExterna.calcularDiscoMinimoParaCPUs(
                cpus, maxArchivosActual, swapActual);
                
        construirInterfaz(tamanoActual, maxArchivosActual, swapActual);
    }

    // Construye la interfaz gráfica del panel con sus campos y etiquetas.
    private void construirInterfaz(int tamanoActual, int maxArchivosActual, int swapActual) {
        setLayout(new GridBagLayout());
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                        "Disco (almacenamiento secundario)",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.MORADO_ACENTO
                ),
                new EmptyBorder(15, 20, 15, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // ==== Etiqueta informativa ====
        JLabel lblInfo = new JLabel("Disco minimo para " + cpus + " CPU(s): " + discoMinimoReal
                + "  |  Max permitido: " + TAMANO_DISCO_MAXIMO
                + "  |  Max archivos: " + MAX_ARCHIVOS_MAXIMO);
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblInfo.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(lblInfo, gbc);
        gbc.gridwidth = 1;

        // ==== Fila 1: Tamano total del disco ====
        JLabel lblTamano = crearLabelCampo("Tamano total del disco");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        add(lblTamano, gbc);

        txtTamanoDisco = crearCampoTexto(String.valueOf(tamanoActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtTamanoDisco, gbc);

        // ==== Fila 2: Maximo de archivos ====
        JLabel lblMaxArch = crearLabelCampo("Cantidad maxima de archivos");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        add(lblMaxArch, gbc);

        txtMaxArchivos = crearCampoTexto(String.valueOf(maxArchivosActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtMaxArchivos, gbc);

        // ==== Fila 3: Tamano del swap ====
        JLabel lblSwap = crearLabelCampo("Tamano de memoria virtual (swap)");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        add(lblSwap, gbc);

        txtTamanoSwap = crearCampoTexto(String.valueOf(swapActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtTamanoSwap, gbc);

        // ==== Fila 4: Texto de ayuda ====
        JLabel lblAyuda = new JLabel("<html><body style='width: 420px'>"
                + "El disco se divide en 4 zonas:<br>"
                + "&nbsp;&nbsp;<b>Indice ASM:</b> maxArchivos * 4 posiciones.<br>"
                + "&nbsp;&nbsp;<b>Indice PROCESO:</b> maxArchivos * 4 posiciones.<br>"
                + "&nbsp;&nbsp;<b>Swap:</b> tamaño de memoria virtual.<br>"
                + "&nbsp;&nbsp;<b>Archivos:</b> resto del disco (min " + Disco.ESPACIO_ARCHIVOS_MINIMO + ").<br><br>"
                + "<b>Minimo requerido para " + cpus + " CPUs:</b> " + discoMinimoReal + " posiciones.<br><br>"
                + "Los archivos .asm se guardan en la zona de archivos.<br>"
                + "Los procesos suspendidos van a la zona de swap."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAyuda.setForeground(new Color(0x60, 0x60, 0x60));
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 6, 6, 6);
        add(lblAyuda, gbc);
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
                BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return campo;
    }

    // Valida los valores ingresados; muestra diálogo de error si fallan.
    public boolean validar() {
        int tamanoIngresado;
        int maxArchivosIngresado;
        int swapIngresado;

        try {
            tamanoIngresado = Integer.parseInt(txtTamanoDisco.getText().trim());
            maxArchivosIngresado = Integer.parseInt(txtMaxArchivos.getText().trim());
            swapIngresado = Integer.parseInt(txtTamanoSwap.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("Todos los campos deben ser numeros enteros.");
            return false;
        }

        if (tamanoIngresado < discoMinimoReal) {
            mostrarError("El tamaño del disco debe ser al menos " + discoMinimoReal + ".\n"
                    + "Esto es necesario para " + cpus + " CPU(s) con " 
                    + maxArchivosIngresado + " archivos.");
            return false;
        }
        if (tamanoIngresado > TAMANO_DISCO_MAXIMO) {
            mostrarError("El tamaño del disco no puede superar " + TAMANO_DISCO_MAXIMO + ".");
            return false;
        }

        if (maxArchivosIngresado < MAX_ARCHIVOS_MINIMO) {
            mostrarError("Debe haber al menos " + MAX_ARCHIVOS_MINIMO + " archivo.");
            return false;
        }
        if (maxArchivosIngresado > MAX_ARCHIVOS_MAXIMO) {
            mostrarError("El maximo de archivos no puede superar " + MAX_ARCHIVOS_MAXIMO + ".");
            return false;
        }

        if (swapIngresado < TAMANO_SWAP_MINIMO) {
            mostrarError("El tamaño de la memoria virtual debe ser al menos "
                    + TAMANO_SWAP_MINIMO + ".");
            return false;
        }
        if (swapIngresado > TAMANO_SWAP_MAXIMO) {
            mostrarError("El tamaño de la memoria virtual no puede superar "
                    + TAMANO_SWAP_MAXIMO + ".");
            return false;
        }

        int espacioIndice = maxArchivosIngresado * Disco.POSICIONES_POR_ENTRADA_INDICE * 2; // ASM + PROCESO
        int espacioOcupado = espacioIndice + swapIngresado;
        int espacioArchivos = tamanoIngresado - espacioOcupado;

        if (espacioArchivos < Disco.ESPACIO_ARCHIVOS_MINIMO) {
            mostrarError("El disco es muy pequeño:\n"
                    + "  Indices (" + maxArchivosIngresado + " archivos x 8) = " + espacioIndice + "\n"
                    + "  Swap = " + swapIngresado + "\n"
                    + "  Total ocupado = " + espacioOcupado + "\n"
                    + "  Espacio para archivos = " + espacioArchivos
                    + " (minimo " + Disco.ESPACIO_ARCHIVOS_MINIMO + ")\n\n"
                    + "Aumenta el tamaño del disco o reduce maxArchivos/swap.");
            return false;
        }

        return true;
    }

    // Muestra un diálogo modal de error con el mensaje indicado.
    private void mostrarError(String mensaje) {
        javax.swing.JOptionPane.showMessageDialog(this, mensaje,
                "Datos invalidos", javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    // Devuelve el tamaño total del disco ingresado.
    public int getTamanoDisco() {
        return Integer.parseInt(txtTamanoDisco.getText().trim());
    }

    // Devuelve la cantidad máxima de archivos ingresada.
    public int getMaxArchivos() {
        return Integer.parseInt(txtMaxArchivos.getText().trim());
    }

    // Devuelve el tamaño del swap ingresado.
    public int getTamanoSwap() {
        return Integer.parseInt(txtTamanoSwap.getText().trim());
    }
}