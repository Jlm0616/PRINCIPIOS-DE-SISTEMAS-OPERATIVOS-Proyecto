package gui;

import java.awt.Color;
import java.awt.Font;

/**
 * Paleta de colores y fuentes de la interfaz.
 *
 * Todos los colores y fuentes estan centralizados aca para que
 * cambiar el estilo sea cuestion de modificar este archivo.
 *
 * Los nombres son genericos (VERDE_PRINCIPAL, MORADO_ACENTO, etc.)
 * para que se puedan cambiar los valores sin tener que renombrar
 * todos los usos en el resto del codigo.
 */
public class Paleta {

    /* ==================== COLORES PRINCIPALES ==================== */

    public static final Color VERDE_PRINCIPAL = new Color(0x43, 0xA0, 0x47);
    public static final Color VERDE_OSCURO = new Color(0x1B, 0x5E, 0x20);
    public static final Color MORADO_ACENTO = new Color(0x5E, 0x35, 0xB1);
    public static final Color MORADO_CLARO = new Color(0x7E, 0x57, 0xC2);
    public static final Color AMARILLO_ADVERTENCIA = new Color(0xF9, 0xA8, 0x25);
    public static final Color ROJO_PELIGRO = new Color(0xC6, 0x28, 0x28);

    /* ==================== COLORES DE FONDO ==================== */

    public static final Color FONDO_GENERAL = new Color(0xEC, 0xEF, 0xF1);
    public static final Color FONDO_PANEL = Color.WHITE;
    public static final Color FONDO_FILA_ALT = new Color(0xF5, 0xF7, 0xFA);
    public static final Color FONDO_TITULO = new Color(0x1B, 0x5E, 0x20);

    /* ==================== COLORES DE TEXTO ==================== */

    public static final Color TEXTO_NORMAL = new Color(0x37, 0x47, 0x4F);
    public static final Color TEXTO_CLARO = Color.WHITE;

    /* ==================== COLORES DE ESTADO ==================== */

    public static final Color ESTADO_READY = new Color(0x66, 0xBB, 0x6A);
    public static final Color ESTADO_RUNNING = new Color(0x43, 0xA0, 0x47);
    public static final Color ESTADO_BLOCKED = new Color(0xE5, 0x39, 0x35);
    public static final Color ESTADO_EXIT = new Color(0x9E, 0x9E, 0x9E);
    public static final Color ESTADO_NEW = new Color(0x42, 0xA5, 0xF5);
    public static final Color ESTADO_SUSPENDIDO = new Color(0xAB, 0x47, 0xBC);

    /* ==================== COLORES DE CPUS (Estilo Militar) ==================== */

    public static final Color CPU_0 = new Color(0xC2, 0xB2, 0x80); // Caqui / Arena
    public static final Color CPU_1 = new Color(0x8F, 0x9A, 0x7B); // Verde Oliva
    public static final Color CPU_2 = new Color(0x9C, 0x7A, 0x5C); // Marrón tierra
    public static final Color CPU_3 = new Color(0x6B, 0x7A, 0x5C); // Verde militar oscuro
    public static final Color CPU_4 = new Color(0xB5, 0xA6, 0x8C); // Beige oscuro

    // Devuelve el color según el ID del núcleo.
    public static Color getColorCPU(int idNucleo) {
        switch (idNucleo) {
            case 0: return CPU_0;
            case 1: return CPU_1;
            case 2: return CPU_2;
            case 3: return CPU_3;
            case 4: return CPU_4;
            default: return Color.LIGHT_GRAY;
        }
    }

    /* ==================== FUENTES ==================== */

    public static final Font FUENTE_TITULO = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FUENTE_TITULO_PANEL = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FUENTE_LABEL = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FUENTE_LABEL_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FUENTE_MONO = new Font("Consolas", Font.PLAIN, 13);
    public static final Font FUENTE_MONO_BOLD = new Font("Consolas", Font.BOLD, 13);
    public static final Font FUENTE_BOTON = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FUENTE_CONSOLA = new Font("Consolas", Font.PLAIN, 13);

    /** Constructor privado: clase de constantes, no se instancia. */
    private Paleta() {
    }
}