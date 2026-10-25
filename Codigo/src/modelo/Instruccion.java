package modelo;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Representa una instrucción ensamblador de la máquina virtual.
 *
 * Los argumentos numéricos pueden estar en decimal ("5", "-3") o en
 * hexadecimal ("3Ch", "40H"). El sufijo 'h' o 'H' indica hexadecimal.
 *
 * Los argumentos de tipo string van entre comillas dobles, ej:
 *   MOV DX, "datos.txt"
 */
public class Instruccion {

    private String opcode;
    private List<String> argumentos;

    // Crea una instrucción con opcode y lista de argumentos.
    public Instruccion(String opcode, List<String> argumentos) {
        this.opcode = opcode;
        this.argumentos = (argumentos != null) ? argumentos : new ArrayList<>();
    }

    // Crea una instrucción sin argumentos.
    public Instruccion(String opcode) {
        this(opcode, new ArrayList<>());
    }

    /* ==================== GETTERS BASICOS ==================== */

    // Devuelve el opcode de la instrucción.
    public String getOpcode() {
        return opcode;
    }

    // Devuelve la lista de argumentos (solo lectura).
    public List<String> getArgumentos() {
        return Collections.unmodifiableList(argumentos);
    }

    // Devuelve cuántos argumentos tiene la instrucción.
    public int cantidadArgumentos() {
        return argumentos.size();
    }

    // Devuelve el argumento en la posición indicada.
    public String getArgumento(int indice) {
        return argumentos.get(indice);
    }

    /**
     * Obtiene un argumento por posicion, convertido a entero.
     *
     * Acepta:
     *   - Decimal: "5", "10", "-3".
     *   - Hexadecimal: "3Ch", "40H", "0A0h".
     *
     * @param indice posicion del argumento (0 = primero)
     * @return el argumento en esa posicion, convertido a int
     * @throws IndexOutOfBoundsException si el indice no existe
     * @throws NumberFormatException si el argumento no es un numero valido
     */
    public int getArgumentoComoEntero(int indice) {
        return parsearNumero(argumentos.get(indice));
    }

    /**
     * Parsea un numero que puede estar en decimal o hexadecimal.
     *
     * @param arg numero en texto
     * @return el valor entero
     * @throws NumberFormatException si no es un numero valido
     */
    public static int parsearNumero(String arg) {
        if (arg == null || arg.isEmpty()) {
            throw new NumberFormatException("numero vacio");
        }
        String s = arg.trim();
        if (s.toLowerCase().endsWith("h")) {
            String sinH = s.substring(0, s.length() - 1);
            return Integer.parseInt(sinH, 16);
        } else {
            return Integer.parseInt(s);
        }
    }

    /**
     * Indica si el argumento en la posicion dada es un nombre de registro
     * conocido (AC, AX, BX, CX, DX, AH, AL) en lugar de un valor numerico.
     *
     * @param indice posicion del argumento a revisar
     * @return true si el argumento es un nombre de registro
     */
    public boolean esRegistro(int indice) {
        if (indice < 0 || indice >= argumentos.size()) {
            return false;
        }
        String arg = argumentos.get(indice).toUpperCase();
        return arg.equals("AC") || arg.equals("AX") || arg.equals("BX")
                || arg.equals("CX") || arg.equals("DX")
                || arg.equals("AH") || arg.equals("AL");
    }

    /**
     * Indica si el argumento en la posicion dada es un string literal
     * (esta entre comillas dobles).
     *
     * Ejemplo: MOV DX, "datos.txt" → esStringLiteral(1) devuelve true.
     *
     * @param indice posicion del argumento a revisar
     * @return true si el argumento es un string entre comillas
     */
    public boolean esStringLiteral(int indice) {
        if (indice < 0 || indice >= argumentos.size()) {
            return false;
        }
        String arg = argumentos.get(indice).trim();
        return arg.length() >= 2
                && arg.startsWith("\"")
                && arg.endsWith("\"");
    }

    /**
     * Devuelve el contenido del string literal sin las comillas.
     *
     * Ejemplo: MOV DX, "datos.txt" → getStringLiteral(1) devuelve "datos.txt".
     *
     * @param indice posicion del argumento
     * @return el string sin comillas, o el argumento crudo si no es literal
     */
    public String getStringLiteral(int indice) {
        String arg = argumentos.get(indice).trim();
        if (arg.length() >= 2 && arg.startsWith("\"") && arg.endsWith("\"")) {
            return arg.substring(1, arg.length() - 1);
        }
        return arg;
    }

    // Devuelve el código hexadecimal de la interrupción (ej. "21H" → 0x21).
    public int getCodigoInterrupcion(int indice) {
        String codigo = argumentos.get(indice).toUpperCase();
        return Integer.parseInt(codigo.substring(0, codigo.length() - 1), 16);
    }

    /* ==================== PESO ==================== */

    // Devuelve el peso (en ciclos) de la instrucción según su opcode.
    public int getPeso() {
        return 1;
    }

    @Override
    public String toString() {
        if (argumentos.isEmpty()) {
            return opcode;
        }
        return opcode + " " + String.join(", ", argumentos);
    }
}