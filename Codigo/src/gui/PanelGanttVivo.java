package gui;

import logica.estadisticas.RegistroGantt;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Diagrama de Gantt EN VIVO de la ventana principal.
 *
 * Dibuja la tabla del requisito 14 del enunciado:
 *
 *   Nucleo | Proceso | 1 2 3 ... 80
 *
 * Una fila por cada proceso que paso por cada nucleo, y una celda por
 * segundo. Se actualiza en cada paso de la simulacion (manual o
 * automatica), resalta la columna del instante actual y se desplaza
 * sola para que esa columna siempre se vea.
 *
 * Celdas:
 *   - llena con el color del proceso: el proceso ejecuto en ese segundo;
 *   - rayada: el proceso ocupo el nucleo esperando el disco (INT 21H).
 *
 * Los datos vienen de {@link RegistroGantt}; este panel solo dibuja.
 */
public class PanelGanttVivo extends JPanel {

    /** Columnas que se muestran como minimo, como en el ejemplo del enunciado. */
    private static final int COLUMNAS_MINIMAS = 80;

    private static final int ANCHO_NUCLEO = 70;
    private static final int ANCHO_PROCESO = 70;
    private static final int ANCHO_CELDA = 24;
    private static final int ALTO_FILA = 22;
    private static final int ALTO_ENCABEZADO = 24;

    private static final Color COLOR_REJILLA = new Color(0xE0, 0xE0, 0xE0);
    private static final Color COLOR_VACIO = new Color(0xFA, 0xFA, 0xFA);
    private static final Color COLOR_ACTUAL = new Color(0xFF, 0xF3, 0xC4);

    private final Lienzo lienzo;
    private final JScrollPane scroll;

    private List<RegistroGantt.Fila> filas = new ArrayList<>();
    private int cantidadNucleos = 1;
    private int reloj = 0;
    private int primeraColumna = 1;
    private int ultimaColumna = COLUMNAS_MINIMAS;
    private IntFunction<String> nombreProceso = id -> null;

    // Crea el panel vacio.
    public PanelGanttVivo() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Diagrama de Gantt (en vivo)",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(2, 5, 5, 5)
        ));

        add(crearLeyenda(), BorderLayout.NORTH);

        lienzo = new Lienzo();
        scroll = new JScrollPane(lienzo);
        scroll.setBorder(BorderFactory.createLineBorder(COLOR_REJILLA));
        scroll.getHorizontalScrollBar().setUnitIncrement(ANCHO_CELDA);
        scroll.getVerticalScrollBar().setUnitIncrement(ALTO_FILA);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Vuelve a dibujar el diagrama con el estado actual de la simulacion.
     *
     * @param registro        registro de ejecucion del GestorProcesos
     * @param cantidadNucleos nucleos configurados (los libres tambien se muestran)
     * @param reloj           instante actual del reloj global
     * @param nombreProceso   nombre del archivo de cada proceso, para el tooltip
     */
    public void actualizar(RegistroGantt registro, int cantidadNucleos, int reloj,
                           IntFunction<String> nombreProceso) {
        this.filas = registro.getFilas();
        this.cantidadNucleos = Math.max(1, cantidadNucleos);
        this.reloj = reloj;
        if (nombreProceso != null) this.nombreProceso = nombreProceso;

        // Si algun dia se registra el instante 0, el diagrama empieza en 0.
        int primero = registro.getPrimerInstante();
        this.primeraColumna = (primero >= 0) ? Math.min(1, primero) : 1;
        this.ultimaColumna = Math.max(COLUMNAS_MINIMAS,
                Math.max(reloj, registro.getUltimoInstante()));

        lienzo.recalcularTamano();
        mostrarColumnaActual();
    }

    // Desplaza el scroll para que la columna del instante actual quede a la vista.
    private void mostrarColumnaActual() {
        if (reloj < primeraColumna) return;
        int x = lienzo.xDeColumna(reloj);
        Rectangle visible = lienzo.getVisibleRect();
        if (x < visible.x + ANCHO_NUCLEO + ANCHO_PROCESO || x + ANCHO_CELDA > visible.x + visible.width) {
            int destino = Math.max(0, x - visible.width / 2);
            lienzo.scrollRectToVisible(new Rectangle(destino, visible.y, visible.width, 1));
        }
    }

    // Leyenda de los dos tipos de celda y de la columna actual.
    private JPanel crearLeyenda() {
        JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leyenda.setOpaque(false);
        leyenda.add(etiquetaLeyenda(new MuestraCelda(false), "Ejecuta en el nucleo"));
        leyenda.add(etiquetaLeyenda(new MuestraCelda(true), "Espera el disco (INT 21H)"));
        JPanel actual = new JPanel();
        actual.setBackground(COLOR_ACTUAL);
        actual.setPreferredSize(new Dimension(14, 14));
        leyenda.add(etiquetaLeyenda(actual, "Instante actual"));
        return leyenda;
    }

    private JPanel etiquetaLeyenda(JPanel muestra, String texto) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        p.add(muestra);
        JLabel l = new JLabel(texto);
        l.setFont(Paleta.FUENTE_LABEL);
        l.setForeground(Paleta.TEXTO_NORMAL);
        // Holgura para que el texto no se corte con los "..." de Swing.
        Dimension d = l.getPreferredSize();
        l.setPreferredSize(new Dimension(d.width + 12, d.height));
        p.add(l);
        return p;
    }

    // Dibuja una celda rayada (espera de disco) con el color del proceso.
    private static void dibujarRayado(Graphics2D g2, int x, int y, int ancho, int alto, Color color) {
        g2.setColor(aclarar(color));
        g2.fillRect(x, y, ancho, alto);
        Graphics2D g = (Graphics2D) g2.create();
        g.clipRect(x, y, ancho, alto);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        for (int d = -alto; d < ancho; d += 6) {
            g.drawLine(x + d, y + alto, x + d + alto, y);
        }
        g.dispose();
    }

    private static Color aclarar(Color c) {
        return new Color((c.getRed() + 3 * 255) / 4, (c.getGreen() + 3 * 255) / 4,
                         (c.getBlue() + 3 * 255) / 4);
    }

    /** Muestra pequena de un tipo de celda, para la leyenda. */
    private static class MuestraCelda extends JPanel {
        private final boolean rayada;

        MuestraCelda(boolean rayada) {
            this.rayada = rayada;
            setPreferredSize(new Dimension(14, 14));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Color c = Paleta.getColorProceso(1);
            if (rayada) {
                dibujarRayado(g2, 0, 0, getWidth(), getHeight(), c);
            } else {
                g2.setColor(c);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    /**
     * Lienzo que dibuja la tabla. Va dentro del scroll.
     */
    private class Lienzo extends JPanel {

        Lienzo() {
            setBackground(Color.WHITE);
            setToolTipText("");   // activa los tooltips por celda
        }

        int cantidadColumnas() {
            return ultimaColumna - primeraColumna + 1;
        }

        int xDeColumna(int t) {
            return ANCHO_NUCLEO + ANCHO_PROCESO + (t - primeraColumna) * ANCHO_CELDA;
        }

        void recalcularTamano() {
            int ancho = ANCHO_NUCLEO + ANCHO_PROCESO + cantidadColumnas() * ANCHO_CELDA + 1;
            int alto = ALTO_ENCABEZADO + filasDibujadas().size() * ALTO_FILA + 1;
            setPreferredSize(new Dimension(ancho, alto));
            revalidate();
            repaint();
        }

        /**
         * Filas en el orden en que se dibujan: por nucleo, y un nucleo sin
         * procesos todavia aparece con una fila vacia (idProceso = -1).
         */
        List<int[]> filasDibujadas() {
            List<int[]> resultado = new ArrayList<>();   // {indiceFila o -1, idNucleo}
            for (int n = 0; n < cantidadNucleos; n++) {
                boolean tiene = false;
                for (int i = 0; i < filas.size(); i++) {
                    if (filas.get(i).getIdNucleo() == n) {
                        resultado.add(new int[]{i, n});
                        tiene = true;
                    }
                }
                if (!tiene) resultado.add(new int[]{-1, n});
            }
            return resultado;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int fila = (e.getY() - ALTO_ENCABEZADO) / ALTO_FILA;
            List<int[]> dibujadas = filasDibujadas();
            if (e.getY() < ALTO_ENCABEZADO || fila < 0 || fila >= dibujadas.size()) return null;
            int indice = dibujadas.get(fila)[0];
            if (indice < 0) return null;

            RegistroGantt.Fila f = filas.get(indice);
            String nombre = nombreProceso.apply(f.getIdProceso());
            String texto = "P" + f.getIdProceso() + (nombre != null ? " (" + nombre + ")" : "")
                    + " en CPU " + f.getIdNucleo();

            int xCeldas = e.getX() - ANCHO_NUCLEO - ANCHO_PROCESO;
            if (xCeldas >= 0) {
                int t = primeraColumna + xCeldas / ANCHO_CELDA;
                if (f.getEjecucion().contains(t)) {
                    texto += ", t=" + t + ": ejecuta";
                } else if (f.getEspera().contains(t)) {
                    texto += ", t=" + t + ": espera el disco";
                }
            }
            return texto;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                dibujar(g2);
            } finally {
                g2.dispose();
            }
        }

        private void dibujar(Graphics2D g2) {
            List<int[]> dibujadas = filasDibujadas();
            int altoTabla = ALTO_ENCABEZADO + dibujadas.size() * ALTO_FILA;

            Font fuenteEncabezado = Paleta.FUENTE_LABEL_BOLD;
            Font fuenteNumero = new Font("Consolas", Font.PLAIN, 11);
            Font fuenteFila = Paleta.FUENTE_MONO_BOLD;

            // === Columna del instante actual ===
            if (reloj >= primeraColumna) {
                g2.setColor(COLOR_ACTUAL);
                g2.fillRect(xDeColumna(reloj), 0, ANCHO_CELDA, altoTabla);
            }

            // === Encabezado ===
            g2.setFont(fuenteEncabezado);
            g2.setColor(Paleta.VERDE_OSCURO);
            FontMetrics fmEnc = g2.getFontMetrics();
            int yTexto = (ALTO_ENCABEZADO + fmEnc.getAscent()) / 2 - 2;
            g2.drawString("Nucleo", 6, yTexto);
            g2.drawString("Proceso", ANCHO_NUCLEO + 6, yTexto);

            g2.setFont(fuenteNumero);
            g2.setColor(Paleta.TEXTO_NORMAL);
            FontMetrics fmNum = g2.getFontMetrics();
            for (int t = primeraColumna; t <= ultimaColumna; t++) {
                String etq = String.valueOf(t);
                int x = xDeColumna(t) + (ANCHO_CELDA - fmNum.stringWidth(etq)) / 2;
                g2.drawString(etq, x, (ALTO_ENCABEZADO + fmNum.getAscent()) / 2 - 2);
            }

            // === Filas ===
            int nucleoAnterior = -1;
            for (int r = 0; r < dibujadas.size(); r++) {
                int indice = dibujadas.get(r)[0];
                int nucleo = dibujadas.get(r)[1];
                int y = ALTO_ENCABEZADO + r * ALTO_FILA;

                // Nombre del nucleo solo en su primera fila, con su color.
                if (nucleo != nucleoAnterior) {
                    g2.setColor(Paleta.getColorCPU(nucleo));
                    int filasDelNucleo = 0;
                    for (int[] d : dibujadas) if (d[1] == nucleo) filasDelNucleo++;
                    g2.fillRect(0, y, ANCHO_NUCLEO, filasDelNucleo * ALTO_FILA);
                    g2.setColor(Color.WHITE);
                    g2.setFont(fuenteFila);
                    g2.drawString("CPU " + nucleo, 6, y + ALTO_FILA - 6);
                    nucleoAnterior = nucleo;
                }

                RegistroGantt.Fila f = (indice >= 0) ? filas.get(indice) : null;

                g2.setFont(fuenteFila);
                g2.setColor(Paleta.TEXTO_NORMAL);
                g2.drawString(f != null ? "P" + f.getIdProceso() : "(libre)",
                        ANCHO_NUCLEO + 6, y + ALTO_FILA - 6);

                Color colorProceso = (f != null) ? Paleta.getColorProceso(f.getIdProceso()) : null;
                for (int t = primeraColumna; t <= ultimaColumna; t++) {
                    int x = xDeColumna(t);
                    if (f != null && f.getEjecucion().contains(t)) {
                        g2.setColor(colorProceso);
                        g2.fillRect(x + 1, y + 1, ANCHO_CELDA - 1, ALTO_FILA - 1);
                    } else if (f != null && f.getEspera().contains(t)) {
                        dibujarRayado(g2, x + 1, y + 1, ANCHO_CELDA - 1, ALTO_FILA - 1, colorProceso);
                    } else if (t != reloj) {
                        g2.setColor(COLOR_VACIO);
                        g2.fillRect(x + 1, y + 1, ANCHO_CELDA - 1, ALTO_FILA - 1);
                    }
                }

                // Linea que separa un nucleo del siguiente.
                boolean ultimaDelNucleo = (r == dibujadas.size() - 1)
                        || dibujadas.get(r + 1)[1] != nucleo;
                if (ultimaDelNucleo) {
                    g2.setColor(Paleta.VERDE_OSCURO);
                    g2.drawLine(0, y + ALTO_FILA, xDeColumna(ultimaColumna) + ANCHO_CELDA, y + ALTO_FILA);
                }
            }

            // === Rejilla ===
            g2.setColor(COLOR_REJILLA);
            for (int t = primeraColumna; t <= ultimaColumna + 1; t++) {
                int x = xDeColumna(t);
                g2.drawLine(x, 0, x, altoTabla);
            }
            g2.drawLine(0, ALTO_ENCABEZADO, xDeColumna(ultimaColumna) + ANCHO_CELDA, ALTO_ENCABEZADO);
            g2.drawLine(ANCHO_NUCLEO, 0, ANCHO_NUCLEO, altoTabla);
        }
    }
}
