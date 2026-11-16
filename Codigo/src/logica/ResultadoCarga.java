
package logica;

import modelo.BCP;

/**
 * Resultado de intentar cargar un programa .asm.
 *
 * EXITO:     Proceso admitido en RAM (READY).
 * ERROR:     No se pudo cargar el programa.
 * EN_ESPERA: Proceso en NEW, almacenado en swap.
 * PENDIENTE: Programa con tiempo de llegada futuro.
 *            No ocupa RAM, BCP ni swap.
 *            No aparece en el panel de procesos.
 */
public class ResultadoCarga {

    public enum Estado {
        EXITO,
        ERROR,
        EN_ESPERA,
        PENDIENTE
    }

    private final Estado estado;
    private final BCP bcp;
    private final String mensajeError;
    private final int idPendiente;

    private ResultadoCarga(Estado estado, BCP bcp,
                           String mensajeError, int idPendiente) {
        this.estado = estado;
        this.bcp = bcp;
        this.mensajeError = mensajeError;
        this.idPendiente = idPendiente;
    }

    public static ResultadoCarga exito(BCP bcp) {
        return new ResultadoCarga(Estado.EXITO, bcp, null, -1);
    }

    public static ResultadoCarga error(String mensaje) {
        return new ResultadoCarga(Estado.ERROR, null, mensaje, -1);
    }

    public static ResultadoCarga enEspera() {
        return new ResultadoCarga(Estado.EN_ESPERA, null, null, -1);
    }

    public static ResultadoCarga pendiente(int idProceso) {
        return new ResultadoCarga(Estado.PENDIENTE, null, null, idProceso);
    }

    public Estado getEstado() {
        return estado;
    }

    public BCP getBcp() {
        return bcp;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public int getIdPendiente() {
        return idPendiente;
    }
}
