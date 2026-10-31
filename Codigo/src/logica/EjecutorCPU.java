package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;
import modelo.Disco;

/**
 * Ejecutor del ciclo de instrucción (fetch-decode-execute) para el Proyecto 2.
 *
 * VALIDACIONES DE PC:
 *   - Si el PC apunta fuera del alcance del proceso, termina con EXIT y error.
 *   - Si un salto (JMP/JE/JNE) cae fuera del alcance, termina con EXIT.
 *   - Si el PC llega al final del proceso sin salto, termina normalmente.
 *
 * Los mensajes de fin y error se envian a:
 *   - Consola (System.out.println) para debugging.
 *   - PanelPantalla (via callback salidaPantalla) para el usuario.
 *
 * NOTA: en el Proyecto 2 ya no hay INT 09H (teclado).
 *
 * PROYECTO 2: el tiempo de fin simulado NO se marca aqui. Se marca en
 * GestorProcesos.procesoTerminado(relojGlobal), que es quien conoce el
 * reloj global. Aqui solo se cambia el estado a EXIT.
 */
public class EjecutorCPU {

    private CPU cpu;
    private Memoria memoria;
    private BCP bcp;
    private Interrupciones interrupciones;

    /** Callback para enviar mensajes a la pantalla de la GUI. */
    private java.util.function.Consumer<String> salidaPantalla;

    /** Indica si el programa ya terminó (INT 20H, EXIT, o error fatal). */
    private boolean programaTerminado;

    public static final int MAX_CICLOS_AUTOMATICO = 1;

    // Crea el ejecutor para un BCP, con su CPU, memoria y disco.
    public EjecutorCPU(CPU cpu, Memoria memoria, BCP bcp, Disco disco) {
        this.cpu = cpu;
        this.memoria = memoria;
        this.bcp = bcp;
        this.interrupciones = new Interrupciones(disco);
        this.programaTerminado = false;
    }

    /* ==================== CICLO PRINCIPAL ==================== */

    // Ejecuta un "segundo" de CPU: fetch, decode y execute de una instrucción.
    public boolean ejecutarSegundoDeCPU() {
        if (programaTerminado) {
            return false;
        }

        if (bcp.getPesoPendiente() == 0) {
            int pc = cpu.getPC();

            // === VALIDACION 1: PC dentro del alcance ===
            if (!esPcValido(pc)) {
                terminarConError("PC fuera del alcance (PC=" + pc
                        + ", base=" + bcp.getBase()
                        + ", alcance=" + bcp.getAlcance() + ")");
                return true;
            }

            Instruccion instr = memoria.leerInstruccion(pc);

            if (instr == null) {
                terminarNormal("no hay más instrucciones en la posición " + pc);
                return true;
            }

            bcp.setPesoPendiente(instr.getPeso());
            cpu.setIR(pc);
        }

        bcp.setPesoPendiente(bcp.getPesoPendiente() - 1);

        if (bcp.getPesoPendiente() > 0) {
            return false;
        }

        int pc = cpu.getPC();
        Instruccion instr = memoria.leerInstruccion(pc);

        boolean saltoEjecutado;
        try {
            saltoEjecutado = ejecutarOperacion(instr, pc);
        } catch (IllegalStateException e) {
            terminarConError(e.getMessage());
            return true;
        }

        if (!saltoEjecutado) {
            int nuevoPc = pc + 1;

            // === VALIDACION 2: PC despues de avanzar ===
            if (!esPcValido(nuevoPc)) {
                terminarNormal("PC llegó al final del proceso (" + nuevoPc + ")");
                return true;
            }

            cpu.setPC(nuevoPc);
        }

        bcp.actualizarDesdeCPU(cpu);
        return true;
    }

    // Ejecuta el ciclo hasta que el programa termine o se alcance el máximo.
    public int ejecutarHastaTerminar() {
        int segundos = 0;
        while (!programaTerminado && segundos < MAX_CICLOS_AUTOMATICO) {
            ejecutarSegundoDeCPU();
            segundos++;
        }
        if (segundos >= MAX_CICLOS_AUTOMATICO) {
            terminarConError("Máximo de ciclos alcanzado (posible ciclo infinito)");
        }
        return segundos;
    }

    /* ==================== VALIDACIONES ==================== */

    // Indica si el PC está dentro del rango [base, base+alcance).
    private boolean esPcValido(int pc) {
        int base = bcp.getBase();
        int alcance = bcp.getAlcance();
        return pc >= base && pc < base + alcance;
    }

    /**
     * Termina el proceso con un error fatal (EXIT).
     * Envia el mensaje a consola Y a pantalla.
     *
     * NOTA: NO marca el fin simulado. Eso lo hace GestorProcesos
     * con el relojGlobal al procesar la lista de terminados.
     */
    private void terminarConError(String mensaje) {
        bcp.setEstado(EstadoProceso.EXIT);
        programaTerminado = true;
        bcp.actualizarDesdeCPU(cpu);

        String texto = "[ERROR FATAL] Proceso " + bcp.getId()
                + " terminado: " + mensaje;

        System.out.println(texto);
        if (salidaPantalla != null) {
            salidaPantalla.accept(texto);
        }
    }

    /**
     * Termina el proceso normalmente (EXIT).
     * Envia el mensaje a consola Y a pantalla.
     *
     * NOTA: NO marca el fin simulado. Eso lo hace GestorProcesos
     * con el relojGlobal al procesar la lista de terminados.
     */
    private void terminarNormal(String motivo) {
        bcp.setEstado(EstadoProceso.EXIT);
        programaTerminado = true;
        bcp.actualizarDesdeCPU(cpu);

        String texto = "[FIN] Proceso " + bcp.getId() + " terminó: " + motivo;

        System.out.println(texto);
        if (salidaPantalla != null) {
            salidaPantalla.accept(texto);
        }
    }

    /* ==================== EJECUCIÓN POR OPCODE ==================== */

    // Ejecuta la operación según el opcode. Devuelve true si fue un salto.
    private boolean ejecutarOperacion(Instruccion instr, int pc) {
        String opcode = instr.getOpcode();

        switch (opcode) {
            case "LOAD":
                cpu.setAC(leerRegistro(instr.getArgumento(0)));
                return false;

            case "STORE":
                escribirRegistro(instr.getArgumento(0), cpu.getAC());
                return false;

            case "MOV":
                ejecutarMOV(instr);
                return false;

            case "ADD": {
                int suma = cpu.getAC() + leerRegistro(instr.getArgumento(0));
                if (suma > 32767 || suma < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(suma));
                return false;
            }

            case "SUB": {
                int resta = cpu.getAC() - leerRegistro(instr.getArgumento(0));
                if (resta > 32767 || resta < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(resta));
                return false;
            }

            case "INC":
                ejecutarINC(instr);
                return false;

            case "DEC":
                ejecutarDEC(instr);
                return false;

            case "SWAP":
                ejecutarSWAP(instr);
                return false;

            case "CMP":
                ejecutarCMP(instr);
                return false;

            case "JMP": {
                int nuevoPc = pc + 1 + instr.getArgumentoComoEntero(0);
                if (!esPcValido(nuevoPc)) {
                    terminarConError("JMP fuera del alcance (PC=" + nuevoPc
                            + ", base=" + bcp.getBase()
                            + ", alcance=" + bcp.getAlcance() + ")");
                    return true;
                }
                cpu.setPC(nuevoPc);
                return true;
            }

            case "JE": {
                if (cpu.getBanderaIgual()) {
                    int nuevoPc = pc + 1 + instr.getArgumentoComoEntero(0);
                    if (!esPcValido(nuevoPc)) {
                        terminarConError("JE fuera del alcance (PC=" + nuevoPc
                                + ", base=" + bcp.getBase()
                                + ", alcance=" + bcp.getAlcance() + ")");
                        return true;
                    }
                    cpu.setPC(nuevoPc);
                    return true;
                }
                return false;
            }

            case "JNE": {
                if (!cpu.getBanderaIgual()) {
                    int nuevoPc = pc + 1 + instr.getArgumentoComoEntero(0);
                    if (!esPcValido(nuevoPc)) {
                        terminarConError("JNE fuera del alcance (PC=" + nuevoPc
                                + ", base=" + bcp.getBase()
                                + ", alcance=" + bcp.getAlcance() + ")");
                        return true;
                    }
                    cpu.setPC(nuevoPc);
                    return true;
                }
                return false;
            }

            case "PUSH":
                bcp.apilar(leerRegistro(instr.getArgumento(0)));
                return false;

            case "POP":
                escribirRegistro(instr.getArgumento(0), bcp.desapilar());
                return false;

            case "PARAM":
                for (int i = 0; i < instr.cantidadArgumentos(); i++) {
                    bcp.apilar(instr.getArgumentoComoEntero(i));
                }
                return false;

            case "INT":
                ejecutarINT(instr.getCodigoInterrupcion(0));
                return false;

            default:
                throw new UnsupportedOperationException(
                    "Opcode no soportado: " + opcode);
        }
    }

    /* ==================== HELPERS POR INSTRUCCIÓN ==================== */

    // Ejecuta MOV: registro ← registro | literal string | número.
    private void ejecutarMOV(Instruccion instr) {
        String destino = instr.getArgumento(0);

        if (instr.cantidadArgumentos() >= 2 && instr.esStringLiteral(1)) {
            escribirRegistroString(destino, instr.getStringLiteral(1));
            return;
        }

        if (instr.cantidadArgumentos() >= 2 && instr.esRegistro(1)) {
            escribirRegistro(destino, leerRegistro(instr.getArgumento(1)));
            return;
        }

        escribirRegistro(destino, instr.getArgumentoComoEntero(1));
    }

    // Ejecuta INC: incrementa AC o el registro indicado.
    private void ejecutarINC(Instruccion instr) {
        if (instr.cantidadArgumentos() == 0) {
            cpu.setAC(limitarA16Bits(cpu.getAC() + 1));
        } else {
            String reg = instr.getArgumento(0);
            escribirRegistro(reg, leerRegistro(reg) + 1);
        }
    }

    // Ejecuta DEC: decrementa AC o el registro indicado.
    private void ejecutarDEC(Instruccion instr) {
        if (instr.cantidadArgumentos() == 0) {
            cpu.setAC(limitarA16Bits(cpu.getAC() - 1));
        } else {
            String reg = instr.getArgumento(0);
            escribirRegistro(reg, leerRegistro(reg) - 1);
        }
    }

    // Ejecuta SWAP: intercambia los valores de dos registros.
    private void ejecutarSWAP(Instruccion instr) {
        String r1 = instr.getArgumento(0);
        String r2 = instr.getArgumento(1);
        int v1 = leerRegistro(r1);
        int v2 = leerRegistro(r2);
        escribirRegistro(r1, v2);
        escribirRegistro(r2, v1);
    }

    // Ejecuta CMP: actualiza la bandera de igualdad comparando dos registros.
    private void ejecutarCMP(Instruccion instr) {
        int v1 = leerRegistro(instr.getArgumento(0));
        int v2 = leerRegistro(instr.getArgumento(1));
        cpu.setBanderaIgual(v1 == v2);
    }

    // Ejecuta INT: delega al manejador de interrupciones y actualiza el estado.
    private void ejecutarINT(int codigo) {
        Interrupciones.ResultadoInterrupcion resultado =
                interrupciones.ejecutar(codigo, bcp);

        switch (resultado) {
            case RUNNING:
                break;
            case BLOQUEADO:
                bcp.setEstado(EstadoProceso.BLOCKED);
                break;
            case TERMINADO:
                bcp.setEstado(EstadoProceso.EXIT);
                programaTerminado = true;
                break;
        }
    }

    /* ==================== CALLBACKS DE INTERRUPCIONES ==================== */

    // Configura el callback de salida a pantalla (propaga a Interrupciones).
    public void setSalidaPantalla(java.util.function.Consumer<String> callback) {
        this.salidaPantalla = callback;
        interrupciones.setSalidaPantalla(callback);
    }

    // Configura el callback de solicitud de archivo (propaga a Interrupciones).
    public void setSolicitudArchivo(java.util.function.Consumer<BCP> callback) {
        interrupciones.setSolicitudArchivo(callback);
    }

    /* ==================== HELPERS GENERALES ==================== */

    // Convierte el valor a su representación de 16 bits con signo.
    private int limitarA16Bits(int valor) {
        int v = valor & 0xFFFF;
        return (v >= 32768) ? v - 65536 : v;
    }

    // Devuelve el valor del registro indicado como entero.
    private int leerRegistro(String nombre) {
        switch (nombre) {
            case "AC": return cpu.getAC();
            case "AX": return cpu.getAX();
            case "BX": return cpu.getBX();
            case "CX": return cpu.getCX();
            case "DX": return cpu.getDXAsInt();
            case "AH": return (cpu.getAX() >> 8) & 0xFF;
            case "AL": return cpu.getAX() & 0xFF;
            default:
                throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }

    // Escribe un valor (limitado a 16 bits) en el registro indicado.
    private void escribirRegistro(String nombre, int valor) {
        int valorLimitado = limitarA16Bits(valor);
        switch (nombre) {
            case "AC": cpu.setAC(valorLimitado); break;
            case "AX": cpu.setAX(valorLimitado); break;
            case "BX": cpu.setBX(valorLimitado); break;
            case "CX": cpu.setCX(valorLimitado); break;
            case "DX": cpu.setDXAsInt(valorLimitado); break;
            case "AH": {
                int al = cpu.getAX() & 0xFF;
                cpu.setAX(((valorLimitado & 0xFF) << 8) | al);
                break;
            }
            case "AL": {
                int ah = (cpu.getAX() >> 8) & 0xFF;
                cpu.setAX((ah << 8) | (valorLimitado & 0xFF));
                break;
            }
            default:
                throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }

    // Escribe un string en el registro (solo DX lo admite como string).
    private void escribirRegistroString(String nombre, String valor) {
        if ("DX".equals(nombre)) {
            cpu.setDX(valor);
        } else {
            try {
                escribirRegistro(nombre, Integer.parseInt(valor));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "El registro " + nombre + " solo acepta valores numericos, no: " + valor);
            }
        }
    }

    /* ==================== GETTERS ==================== */

    public BCP getBcp() {
        return bcp;
    }

    public boolean isProgramaTerminado() {
        return programaTerminado;
    }
}