package logica;

import modelo.BCP;
import modelo.Disco;

import java.util.function.Consumer;

/**
 * Maneja las interrupciones del sistema (llamadas al sistema).
 *
 * Segun Stallings (seccion 3.4, "Process Control"), las interrupciones
 * son supervisor calls: el programa en ejecucion pide un servicio al SO.
 *
 * En el Proyecto 2:
 *   - INT 20H: termina el programa.
 *   - INT 10H: imprime en pantalla el valor de DX.
 *   - INT 21H: manejo de archivos (crear, abrir, leer, escribir, eliminar).
 *
 * NOTA: INT 09H (lectura de teclado) YA NO SE USA en el Proyecto 2.
 *
 * INT 21H: manejo de archivos.
 *   AH = operacion:
 *     3Ch -> crear
 *     3Dh -> abrir
 *     4Dh -> leer
 *     40h -> escribir
 *     41h -> eliminar
 *   DX = cadena de texto con el nombre del archivo (String).
 *   AL = resultado (0 exito, 1 error, o contenido leido).
 *
 * IMPORTANTE: todos los archivos creados por procesos (INT 21H) van al
 * INDICE PROCESO del disco, NO al indice ASM.
 *
 * Los mensajes [DISCO] van a consola Y a PanelPantalla (via callback).
 *
 * PROYECTO 2: INT 20H ya NO marca el fin simulado del proceso. Ese
 * marcado lo hace GestorProcesos.procesoTerminado(relojGlobal), que es
 * quien conoce el reloj global. Aqui solo se devuelve TERMINADO.
 */
public class Interrupciones {

    private final Disco disco;

    /* ==================== CALLBACKS ==================== */

    private Consumer<String> salidaPantalla;
    private Consumer<BCP> solicitudArchivo;

    /* ==================== CONSTRUCTOR ==================== */

    // Crea el manejador de interrupciones con el disco indicado.
    public Interrupciones(Disco disco) {
        this.disco = disco;
    }

    /* ==================== EJECUCION ==================== */

    // Ejecuta la interrupción correspondiente al código indicado.
    public ResultadoInterrupcion ejecutar(int codigo, BCP bcp) {
        switch (codigo) {
            case 0x20: return ejecutarFinPrograma(bcp);
            case 0x10: return ejecutarImprimirPantalla(bcp);
            case 0x21: return ejecutarManejoArchivos(bcp);
            default:
                throw new UnsupportedOperationException(
                    "Interrupcion no soportada: " + Integer.toHexString(codigo) + "H");
        }
    }

    /* ==================== MANEJADORES ==================== */

    /**
     * INT 20H: termina el programa.
     *
     * NOTA: NO marca el fin simulado. Eso lo hace GestorProcesos
     * con el relojGlobal al procesar la lista de terminados.
     */
    private ResultadoInterrupcion ejecutarFinPrograma(BCP bcp) {
        return ResultadoInterrupcion.TERMINADO;
    }

    /**
     * INT 10H: imprime en pantalla el valor de DX.
     */
    private ResultadoInterrupcion ejecutarImprimirPantalla(BCP bcp) {
        String mensaje = "[PANTALLA] DX = " + bcp.getDx();
        if (salidaPantalla != null) {
            salidaPantalla.accept(mensaje);
        }
        return ResultadoInterrupcion.RUNNING;
    }

    /**
     * INT 21H: manejo de archivos.
     */
    private ResultadoInterrupcion ejecutarManejoArchivos(BCP bcp) {
        if (solicitudArchivo != null) {
            solicitudArchivo.accept(bcp);
        }

        int ah = bcp.getAh();
        String nombre = bcp.getDx();

        // Validacion: nombre vacio
        if (nombre == null || nombre.isEmpty()) {
            bcp.setAl(1);
            enviarMensaje("[DISCO] ERROR: nombre de archivo vacio (DX=\"" + nombre + "\")");
            return ResultadoInterrupcion.BLOQUEADO;
        }

        switch (ah) {
            case 0x3C: {  // crear archivo
                boolean creado = crearArchivoEnDisco(nombre);
                bcp.setAl(creado ? 0 : 1);
                enviarMensaje("[DISCO] crear(\"" + nombre + "\") = " + creado);
                break;
            }

            case 0x3D: {  // abrir archivo
                if (!existeArchivoProceso(nombre)) {
                    bcp.setAl(1);   // archivo no existe
                    enviarMensaje("[DISCO] abrir(\"" + nombre + "\") = NO EXISTE");
                    break;
                }
                boolean abierto = bcp.abrirArchivo(nombre);
                if (abierto) {
                    bcp.setAl(0);
                    enviarMensaje("[DISCO] abrir(\"" + nombre + "\") = OK");
                } else {
                    bcp.setAl(1);
                    enviarMensaje("[DISCO] abrir(\"" + nombre + "\") = ERROR (max "
                            + BCP.TAMANO_MAXIMO_ARCHIVOS + " archivos abiertos)");
                }
                break;
            }

            case 0x4D: {  // leer archivo
                String contenido = leerArchivoDeDisco(nombre);
                if (contenido != null && !contenido.isEmpty()) {
                    // Parsear el contenido como número (no como carácter ASCII)
                    try {
                        int valor = Integer.parseInt(contenido.trim());
                        bcp.setAl(valor & 0xFF);   // limitar a 8 bits
                    } catch (NumberFormatException e) {
                        // Si no es número, usar el primer carácter como número (0-255)
                        bcp.setAl(contenido.charAt(0) & 0xFF);
                    }
                } else {
                    bcp.setAl(0);
                }
                enviarMensaje("[DISCO] leer(\"" + nombre + "\") = \"" + contenido + "\"");
                break;
            }

            case 0x40: {  // escribir archivo
                int al = bcp.getAl();
                // Escribir el número como texto (no como carácter ASCII)
                String aEscribir = String.valueOf(al);
                boolean escrito = escribirArchivoEnDisco(nombre, aEscribir);
                bcp.setAl(escrito ? 0 : 1);
                enviarMensaje("[DISCO] escribir(\"" + nombre + "\", \"" + aEscribir + "\")");
                break;
            }

            case 0x41: {  // eliminar archivo
                bcp.cerrarArchivo(nombre);

                boolean eliminado = eliminarArchivoDeDisco(nombre);
                bcp.setAl(eliminado ? 0 : 1);
                enviarMensaje("[DISCO] eliminar(\"" + nombre + "\") = " + eliminado);
                break;
            }

            default: {
                bcp.setAl(0xFF);
                enviarMensaje("[DISCO] operacion desconocida: AH="
                        + Integer.toHexString(ah));
                break;
            }
        }

        return ResultadoInterrupcion.BLOQUEADO;
    }

    /* ==================== HELPER DE MENSAJES ==================== */

    /**
     * Envia un mensaje a consola Y a PanelPantalla (si hay callback).
     */
    private void enviarMensaje(String texto) {
        System.out.println(texto);
        if (salidaPantalla != null) {
            salidaPantalla.accept(texto);
        }
    }

    /* ==================== HELPERS DE ARCHIVOS ==================== */

    // Indica si el archivo existe en el índice PROCESO del disco.
    private boolean existeArchivoProceso(String nombre) {
        return disco.existe(nombre, Disco.TIPO_PROCESO);
    }

    // Crea un archivo vacío en el disco y lo registra en el índice PROCESO.
    private boolean crearArchivoEnDisco(String nombre) {
        if (existeArchivoProceso(nombre)) return false;

        // Un archivo de INT 21H ocupa 1 sola posición (el valor de AL)
        int inicio = disco.reservarBloqueArchivo(1);
        if (inicio == -1) return false;

        disco.escribir(inicio, 0);   // valor inicial = 0

        return disco.registrarArchivoProceso(nombre, inicio, inicio, Disco.ZONA_PRINCIPAL);
    }

    // Lee el contenido completo del archivo desde el disco.
    private String leerArchivoDeDisco(String nombre) {
        if (!existeArchivoProceso(nombre)) return null;

        int inicio = disco.getInicioArchivo(nombre, Disco.TIPO_PROCESO);
        int fin = disco.getFinArchivo(nombre, Disco.TIPO_PROCESO);
        if (inicio == -1 || fin == -1) return null;

        // Leer SOLO el primer valor (el archivo tiene 1 sola posición)
        Object v = disco.leer(inicio);
        if (v == null) return null;

        return v.toString();
    }

    // Escribe el contenido en el archivo del disco, reubicándolo si crece.
    private boolean escribirArchivoEnDisco(String nombre, String contenido) {
        if (!existeArchivoProceso(nombre)) return false;
        if (contenido == null) return false;

        int inicioViejo = disco.getInicioArchivo(nombre, Disco.TIPO_PROCESO);
        int finViejo = disco.getFinArchivo(nombre, Disco.TIPO_PROCESO);
        int tamanoViejo = (finViejo - inicioViejo) + 1;

        // El contenido debe ser UN SOLO VALOR (un int)
        int valor;
        try {
            valor = Integer.parseInt(contenido.trim());
        } catch (NumberFormatException e) {
            // Si no es número, no se puede guardar en AL
            return false;
        }

        // Validar rango de AL (0-255)
        if (valor < 0 || valor > 255) {
            return false;
        }

        // Si ya hay espacio (1 posición), reescribir
        if (tamanoViejo >= 1) {
            disco.escribir(inicioViejo, valor);
            // Limpiar posiciones sobrantes si el archivo era más grande
            for (int i = 1; i < tamanoViejo; i++) {
                disco.escribir(inicioViejo + i, null);
            }
            return true;
        }

        // Si no hay espacio, reservar 1 posición
        int nuevoInicio = disco.reservarBloqueArchivo(1);
        if (nuevoInicio == -1) return false;

        disco.escribir(nuevoInicio, valor);

        // Liberar bloque viejo
        disco.liberarBloqueArchivo(inicioViejo, tamanoViejo);

        disco.eliminarDelIndice(nombre, Disco.TIPO_PROCESO);
        return disco.registrarArchivoProceso(
                nombre, nuevoInicio, nuevoInicio,   // ← solo 1 posición
                Disco.ZONA_PRINCIPAL);
    }

    // Elimina el archivo del disco y de su índice.
    private boolean eliminarArchivoDeDisco(String nombre) {
        if (!existeArchivoProceso(nombre)) return false;

        int inicio = disco.getInicioArchivo(nombre, Disco.TIPO_PROCESO);
        int fin = disco.getFinArchivo(nombre, Disco.TIPO_PROCESO);
        if (inicio == -1 || fin == -1) return false;

        int tamano = (fin - inicio) + 1;

        disco.liberarBloqueArchivo(inicio, tamano);
        disco.eliminarDelIndice(nombre, Disco.TIPO_PROCESO);

        return true;
    }

    /* ==================== CONFIGURACION DE CALLBACKS ==================== */

    // Configura el callback de salida a pantalla.
    public void setSalidaPantalla(Consumer<String> callback) {
        this.salidaPantalla = callback;
    }

    // Configura el callback de solicitud de archivo.
    public void setSolicitudArchivo(Consumer<BCP> callback) {
        this.solicitudArchivo = callback;
    }

    /* ==================== RESULTADO ==================== */

    // Resultado posible tras ejecutar una interrupción.
    public enum ResultadoInterrupcion {
        RUNNING,
        BLOQUEADO,
        TERMINADO
    }
}