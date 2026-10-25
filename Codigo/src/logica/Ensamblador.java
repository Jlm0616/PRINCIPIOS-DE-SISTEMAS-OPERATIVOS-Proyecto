package logica;

import modelo.Instruccion;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.util.Scanner;

/**
 * Ensamblador de la maquina virtual (Proyecto 1).
 *
 * VALIDACIONES:
 *   1. Archivo existe y es legible.
 *   2. Extension .asm.
 *   3. Al menos 1 instruccion.
 *   4. Sintaxis de cada linea (opcode, argumentos, registros, numeros).
 *   5. Debe contener al menos un 'INT 20H' para finalizar el programa.
 *   6. INT 09H (teclado) ya no esta soportada (Proyecto 2).
 */
public class Ensamblador {

    /** Cantidad minima y maxima de argumentos permitida por opcode. */
    private static final Map<String, int[]> RANGO_ARGUMENTOS = new HashMap<>();
    static {
        RANGO_ARGUMENTOS.put("LOAD",  new int[]{1, 1});
        RANGO_ARGUMENTOS.put("STORE", new int[]{1, 1});
        RANGO_ARGUMENTOS.put("MOV",   new int[]{2, 2});
        RANGO_ARGUMENTOS.put("ADD",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("SUB",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("INC",   new int[]{0, 1});
        RANGO_ARGUMENTOS.put("DEC",   new int[]{0, 1});
        RANGO_ARGUMENTOS.put("SWAP",  new int[]{2, 2});
        RANGO_ARGUMENTOS.put("INT",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("JMP",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("CMP",   new int[]{2, 2});
        RANGO_ARGUMENTOS.put("JE",    new int[]{1, 1});
        RANGO_ARGUMENTOS.put("JNE",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("PARAM", new int[]{1, 3});
        RANGO_ARGUMENTOS.put("PUSH",  new int[]{1, 1});
        RANGO_ARGUMENTOS.put("POP",   new int[]{1, 1});
    }

    /** Opcodes cuyo primer argumento (si existe) debe ser un registro. */
    private static final Set<String> PRIMER_ARG_REGISTRO = new HashSet<>(Arrays.asList(
            "LOAD", "STORE", "ADD", "SUB", "INC", "DEC", "SWAP", "CMP", "PUSH", "POP", "MOV"));

    /** Opcodes de salto, cuyo unico argumento es un desplazamiento con signo. */
    private static final Set<String> OPCODES_SALTO = new HashSet<>(Arrays.asList("JMP", "JE", "JNE"));

    /** Registros validos que pueden usarse como argumento en las instrucciones. */
    private static final Set<String> REGISTROS_VALIDOS = new HashSet<>(
            Arrays.asList("AC", "AX", "BX", "CX", "DX", "AH", "AL"));

    /** Patron esperado para codigos de interrupcion, ej. "20H", "09H". */
    private static final String PATRON_CODIGO_INTERRUPCION = "^[0-9A-Fa-f]{2}[Hh]$";

    private List<String> erroresEncontrados;

    /* ==================== VALIDACION DE ARCHIVO ==================== */

    // Valida que el archivo exista, sea .asm, tenga instrucciones y contenga INT 20H.
    public boolean esArchivoValido(File archivo) {
        erroresEncontrados = new ArrayList<>();

        if (archivo == null || !archivo.exists() || !archivo.canRead()) {
            erroresEncontrados.add("El archivo no existe o no se puede leer.");
            return false;
        }

        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            erroresEncontrados.add("El archivo debe tener extension .asm");
            return false;
        }

        int numeroLinea = 0;
        boolean tieneAlMenosUnaInstruccion = false;
        boolean tieneInt20H = false;

        try (Scanner lector = new Scanner(archivo)) {
            while (lector.hasNextLine()) {
                numeroLinea++;
                String linea = lector.nextLine().trim();

                if (linea.isEmpty()) {
                    continue;
                }

                try {
                    Instruccion instr = parsearLinea(linea);
                    tieneAlMenosUnaInstruccion = true;

                    // Verificar si es INT 20H
                    if ("INT".equals(instr.getOpcode())
                            && instr.getCodigoInterrupcion(0) == 0x20) {
                        tieneInt20H = true;
                    }
                } catch (IllegalArgumentException e) {
                    erroresEncontrados.add("Linea " + numeroLinea + ": " + e.getMessage()
                            + "\n-> \"" + linea + "\"");
                }
            }
        } catch (Exception e) {
            erroresEncontrados.add("No se pudo leer el archivo: " + e.getMessage());
            return false;
        }

        if (!tieneAlMenosUnaInstruccion) {
            erroresEncontrados.add("El archivo no contiene ninguna instruccion.");
        }

        // === VALIDACION: debe tener INT 20H ===
        if (!tieneInt20H) {
            erroresEncontrados.add(
                "El archivo debe contener al menos un 'INT 20H' para finalizar el programa.");
        }

        return erroresEncontrados.isEmpty();
    }

    // Devuelve todos los errores encontrados como un solo texto.
    public String getErroresComoTexto() {
        if (erroresEncontrados == null || erroresEncontrados.isEmpty()) {
            return "";
        }
        return String.join("\n\n", erroresEncontrados);
    }

    // Devuelve la lista de errores encontrados.
    public List<String> getErroresEncontrados() {
        return erroresEncontrados;
    }

    /* ==================== LECTURA DE ARCHIVO ==================== */

    // Lee el archivo .asm y devuelve la lista de instrucciones.
    public List<Instruccion> leerArchivo(File archivoEnsamblador) {
        List<Instruccion> instrucciones = new ArrayList<>();

        try (Scanner lector = new Scanner(archivoEnsamblador)) {
            while (lector.hasNextLine()) {
                String linea = lector.nextLine().trim();

                if (linea.isEmpty()) {
                    continue;
                }

                instrucciones.add(parsearLinea(linea));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error leyendo el archivo: " + e.getMessage());
        }

        return instrucciones;
    }

    /* ==================== PARSEO CENTRAL ==================== */

    // Convierte una línea en una Instruccion, validando opcode y argumentos.
    private Instruccion parsearLinea(String linea) {
        int primerEspacio = linea.indexOf(' ');
        String opcode = (primerEspacio == -1) ? linea : linea.substring(0, primerEspacio);
        opcode = opcode.toUpperCase();

        String resto = (primerEspacio == -1) ? "" : linea.substring(primerEspacio + 1).trim();

        List<String> argumentos = parsearArgumentos(resto);

        if (!RANGO_ARGUMENTOS.containsKey(opcode)) {
            throw new IllegalArgumentException("opcode desconocido '" + opcode + "'");
        }

        int[] rango = RANGO_ARGUMENTOS.get(opcode);
        int minimo = rango[0];
        int maximo = rango[1];
        if (argumentos.size() < minimo || argumentos.size() > maximo) {
            throw new IllegalArgumentException(
                    "el opcode '" + opcode + "' espera entre " + minimo + " y " + maximo
                    + " argumento(s), se encontraron " + argumentos.size());
        }

        validarArgumentos(opcode, argumentos);

        return new Instruccion(opcode, argumentos);
    }

    // Separa los argumentos por coma, respetando comillas dobles.
    private List<String> parsearArgumentos(String resto) {
        List<String> argumentos = new ArrayList<>();
        if (resto == null || resto.isEmpty()) {
            return argumentos;
        }

        StringBuilder actual = new StringBuilder();
        boolean dentroComillas = false;

        for (int i = 0; i < resto.length(); i++) {
            char c = resto.charAt(i);

            if (c == '"') {
                dentroComillas = !dentroComillas;
                actual.append(c);
            } else if (c == ',' && !dentroComillas) {
                argumentos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }

        if (actual.length() > 0) {
            argumentos.add(actual.toString().trim());
        }

        return argumentos;
    }

    // Valida la forma de los argumentos según el opcode.
    private void validarArgumentos(String opcode, List<String> argumentos) {

        if (OPCODES_SALTO.contains(opcode)) {
            validarEntero(argumentos.get(0), "desplazamiento");
            return;
        }

        if (opcode.equals("INT")) {
            String codigo = argumentos.get(0);
            if (!codigo.matches(PATRON_CODIGO_INTERRUPCION)) {
                throw new IllegalArgumentException(
                        "codigo de interrupcion invalido '" + codigo
                        + "', se esperaba formato como '20H'");
            }

            //INT 09H (lectura de teclado) ya no se usa
            int codigoNum = Integer.parseInt(codigo.substring(0, 2), 16);
            if (codigoNum == 0x09) {
                throw new IllegalArgumentException(
                        "INT 09H (lectura de teclado) ya no esta soportada en el Proyecto 2");
            }
            return;
        }

        if (opcode.equals("PARAM")) {
            for (String arg : argumentos) {
                validarEntero(arg, "parametro");
            }
            return;
        }

        if (opcode.equals("MOV")) {
            validarRegistro(argumentos.get(0));
            String segundo = argumentos.get(1);

            if (REGISTROS_VALIDOS.contains(segundo.toUpperCase())) {
                return;
            }

            if (esStringLiteral(segundo)) {
                return;
            }

            validarEntero(segundo, "valor");
            return;
        }

        if (PRIMER_ARG_REGISTRO.contains(opcode)) {
            for (String arg : argumentos) {
                validarRegistro(arg);
            }
        }
    }

    // Indica si el argumento es un string entre comillas dobles.
    private boolean esStringLiteral(String arg) {
        if (arg == null) return false;
        String s = arg.trim();
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"");
    }

    // Valida que el argumento sea un registro conocido.
    private void validarRegistro(String arg) {
        if (!REGISTROS_VALIDOS.contains(arg.toUpperCase())) {
            throw new IllegalArgumentException(
                    "registro desconocido '" + arg + "'. Validos: " + REGISTROS_VALIDOS);
        }
    }

    // Valida que el argumento sea un número entero (decimal o hexadecimal).
    private void validarEntero(String arg, String descripcion) {
        try {
            parsearNumero(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "el " + descripcion + " '" + arg + "' no es un numero entero valido");
        }
    }

    /* ==================== HELPERS DE NUMEROS ==================== */

    // Parsea un número en decimal o hexadecimal (sufijo h/H).
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
}