package gui;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Color;

/**
 * Panel que simula la pantalla de la minicomputadora.
 *
 * En el Proyecto 2, SOLO se usa para mostrar mensajes de salida (INT 10H).
 * Ya no hay entrada de teclado (INT 09H eliminado).
 */
public class PanelPantalla extends JPanel {

    private JTextArea areaPantalla;

    // Crea el panel y construye su interfaz gráfica.
    public PanelPantalla() {
        construirInterfaz();
    }

    // Construye la interfaz del panel (solo área de pantalla).
    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Pantalla",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(5, 5, 5, 5)
        ));

        // --- Area de salida (pantalla) ---
        areaPantalla = new JTextArea();
        areaPantalla.setEditable(false);
        areaPantalla.setFont(Paleta.FUENTE_CONSOLA);
        areaPantalla.setBackground(Color.WHITE);
        areaPantalla.setForeground(Paleta.TEXTO_NORMAL);
        areaPantalla.setLineWrap(true);
        areaPantalla.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(areaPantalla);
        scroll.setPreferredSize(new Dimension(0, 200));
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Agrega una linea a la salida de la pantalla.
     */
    public void agregarMensaje(String linea) {
        areaPantalla.append(linea + "\n");
        // Auto-scroll al final
        areaPantalla.setCaretPosition(areaPantalla.getDocument().getLength());
    }

    /**
     * Limpia toda la pantalla.
     */
    public void limpiar() {
        areaPantalla.setText("");
    }
}