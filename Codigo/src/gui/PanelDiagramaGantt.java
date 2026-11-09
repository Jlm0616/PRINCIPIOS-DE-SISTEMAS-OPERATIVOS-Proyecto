package gui;

import logica.estadisticas.Estadisticas;
import logica.estadisticas.IntervaloEjecucion;
import logica.estadisticas.LineaProceso;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Panel que dibuja el DIAGRAMA DE GANTT de ejecucion de procesos.
 *
 * Se dibuja un panel por CPU, con:
 *   - Encabezado: instantes (t=1, t=2, ...).
 *   - Filas: un proceso por fila, ORDENADAS POR ID (P1, P2, P3, ...).
 *   - Celdas: ■ si el proceso ejecuto en ese instante, vacio si no.
 *   - Colores: cada proceso tiene un color estable.
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo DIBUJA. No calcula estadisticas (las recibe ya hechas
 *    de {@link Estadisticas}). No sabe de planificacion.
 *  - OCP: si se quiere otro estilo (barras en vez de ■), se extiende
 *    con un nuevo panel.
 *  - DIP: depende de la abstraccion {@link Estadisticas}, no de la
 *    fuente de datos.
 */
public class PanelDiagramaGantt extends JPanel {

    // Celdas mas grandes para mejor legibilidad
    private static final int ANCHO_CELDA = 42;
    private static final int ALTO_FILA  = 34;
    private static final int ANCHO_ETIQUETA_PROCESO = 110;
    private static final int ALTO_ENCABEZADO = 32;
    private static final int MARGEN = 10;

    private final Estadisticas estadisticas;
    private final List<Integer> cpusPresentes;

    // Panel interno que hace el dibujo real (con scroll)
    private final PanelDibujo panelDibujo;

    /**
     * Construye el panel con el diagrama de todas las CPUs presentes.
     *
     * @param estadisticas estadisticas ya calculadas
     */
    public PanelDiagramaGantt(Estadisticas estadisticas) {
        this.estadisticas = estadisticas;
        this.cpusPresentes = estadisticas.getCpusPresentes();

        setLayout(new BorderLayout(5, 5));
        setOpaque(false);

        panelDibujo = new PanelDibujo();

        JScrollPane scroll = new JScrollPane(panelDibujo);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getHorizontalScrollBar().setUnitIncrement(ANCHO_CELDA);
        scroll.getVerticalScrollBar().setUnitIncrement(ALTO_FILA);
        // Sin altura fija: el scroll ocupa todo el espacio disponible
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Panel interno que dibuja el diagrama con paintComponent.
     */
    private class PanelDibujo extends JPanel {

        PanelDibujo() {
            setBackground(Color.WHITE);
            setOpaque(true);
            recalcularTamano();
        }

        private void recalcularTamano() {
            int cant = Math.max(1, estadisticas.getCantidadInstantes());
            int ancho = ANCHO_ETIQUETA_PROCESO
                    + MARGEN * 2
                    + cant * ANCHO_CELDA;

            int filasTotales = 0;
            for (Integer cpu : cpusPresentes) {
                filasTotales += 1;   // titulo de CPU
                filasTotales += estadisticas.getLineasDeCPU(cpu).size();
                filasTotales += 1;   // separacion entre CPUs
            }
            int alto = MARGEN * 2
                    + ALTO_ENCABEZADO
                    + filasTotales * ALTO_FILA;

            setPreferredSize(new Dimension(Math.max(ancho, 500),
                                           Math.max(alto, 300)));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                dibujarTodo(g2);
            } finally {
                g2.dispose();
            }
        }

        private void dibujarTodo(Graphics2D g2) {
            if (cpusPresentes.isEmpty()) {
                g2.setColor(Paleta.TEXTO_NORMAL);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                g2.drawString("Sin datos de ejecucion.", 20, 30);
                return;
            }

            int intMin = estadisticas.getInstanteMin();
            int cant   = Math.max(1, estadisticas.getCantidadInstantes());

            Font fuenteNum = new Font("Consolas", Font.PLAIN, 11);
            Font fuenteProceso = new Font("Consolas", Font.BOLD, 14);
            g2.setFont(fuenteNum);

            int y = MARGEN;

            for (Integer cpu : cpusPresentes) {
                y = dibujarCPU(g2, cpu, intMin, cant, y, fuenteNum, fuenteProceso);
                y += MARGEN * 2;   // separacion entre CPUs
            }
        }

        private int dibujarCPU(Graphics2D g2, int idCpu, int intMin,
                               int cant, int yInicial,
                               Font fuenteNum, Font fuenteProceso) {

            List<LineaProceso> lineas = estadisticas.getLineasDeCPU(idCpu);
            int y = yInicial;

            // === Titulo de la CPU ===
            g2.setFont(fuenteProceso);
            g2.setColor(Paleta.VERDE_OSCURO);
            g2.drawString("CPU " + idCpu, MARGEN, y + ALTO_ENCABEZADO - 8);
            y += ALTO_ENCABEZADO;

            // === Encabezado de instantes ===
            g2.setFont(fuenteNum);
            g2.setColor(Paleta.TEXTO_NORMAL);

            FontMetrics fmNum = g2.getFontMetrics(fuenteNum);
            int xBase = MARGEN + ANCHO_ETIQUETA_PROCESO;

            int altoFilas = lineas.size() * ALTO_FILA;

            for (int i = 0; i < cant; i++) {
                int t = intMin + i;
                int x = xBase + i * ANCHO_CELDA;

                String etq = "t=" + t;
                int anchoEtq = fmNum.stringWidth(etq);
                int xEtq = x + (ANCHO_CELDA - anchoEtq) / 2;
                g2.drawString(etq, xEtq, y + fmNum.getAscent() + 8);

                // Linea vertical tenue
                g2.setColor(new Color(0xEE, 0xEE, 0xEE));
                g2.drawLine(x, y + ALTO_ENCABEZADO - 4,
                            x, y + ALTO_ENCABEZADO + altoFilas);
                g2.setColor(Paleta.TEXTO_NORMAL);
            }
            y += ALTO_ENCABEZADO;

            if (lineas.isEmpty()) {
                g2.setFont(fuenteProceso);
                g2.setColor(Paleta.TEXTO_NORMAL);
                g2.drawString("(sin procesos)", MARGEN, y + ALTO_FILA - 8);
                y += ALTO_FILA;
                return y;
            }

            // === Filas de procesos (ya ordenadas por id desde Estadisticas) ===
            for (LineaProceso lp : lineas) {
                dibujarFilaProceso(g2, lp, intMin, cant, y);
                y += ALTO_FILA;
            }

            return y;
        }

        private void dibujarFilaProceso(Graphics2D g2, LineaProceso lp,
                                        int intMin, int cant, int y) {

            // === Etiqueta del proceso ===
            g2.setFont(new Font("Consolas", Font.BOLD, 14));
            g2.setColor(Paleta.TEXTO_NORMAL);
            String etqProceso = "P" + lp.getId();
            g2.drawString(etqProceso, MARGEN, y + ALTO_FILA - 10);

            Color colorProceso = getColorProceso(lp.getId());

            int xBase = MARGEN + ANCHO_ETIQUETA_PROCESO;
            for (int i = 0; i < cant; i++) {
                int t = intMin + i;
                boolean activo = ejecutaEn(lp, t);

                int x = xBase + i * ANCHO_CELDA;

                if (activo) {
                    g2.setColor(colorProceso);
                    g2.fillRect(x + 1, y + 1, ANCHO_CELDA - 2, ALTO_FILA - 2);

                    // Simbolo ■ grande centrado
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
                    FontMetrics fm = g2.getFontMetrics();
                    String simb = "■";
                    int anchoSimb = fm.stringWidth(simb);
                    g2.drawString(simb,
                            x + (ANCHO_CELDA - anchoSimb) / 2,
                            y + (ALTO_FILA + fm.getAscent()) / 2 - 2);
                } else {
                    g2.setColor(new Color(0xFA, 0xFA, 0xFA));
                    g2.fillRect(x + 1, y + 1, ANCHO_CELDA - 2, ALTO_FILA - 2);
                }

                g2.setColor(new Color(0xE0, 0xE0, 0xE0));
                g2.drawRect(x, y, ANCHO_CELDA, ALTO_FILA);
            }
        }

        private boolean ejecutaEn(LineaProceso lp, int t) {
            for (IntervaloEjecucion ie : lp.getIntervalos()) {
                if (ie.contiene(t)) return true;
            }
            return false;
        }

        private Color getColorProceso(int idProceso) {
            return Paleta.getColorProceso(idProceso);
        }
    }
}