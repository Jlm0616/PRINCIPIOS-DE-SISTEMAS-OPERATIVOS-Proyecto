package logica.estadisticas;

/**
 * DTO inmutable que representa un intervalo CONTIGUO de ejecucion de
 * un proceso en una CPU.
 *
 * Un proceso puede tener VARIOS intervalos si fue expulsado (SRT) o
 * bloqueado (I/O) y luego reanudado.
 *
 * Ejemplo: P2 que corre en t=4 y luego en t=11-15 tiene:
 *   IntervaloEjecucion(4, 4)
 *   IntervaloEjecucion(11, 15)
 *
 * PROYECTO 2 - SOLID:
 *  - SRP: solo representa un rango [inicio, fin]. No calcula, no
 *    formatea, no conoce la GUI.
 *  - OCP: si se necesita agregar "cpu" o "prioridad", se extiende el
 *    constructor sin romper a los llamadores actuales (con un
 *    constructor sobrecargado).
 *  - ISP: expone solo lo necesario.
 *  - DIP: no depende de nadie.
 */
public class IntervaloEjecucion {

    private final int inicio;
    private final int fin;

    /**
     * Crea un intervalo [inicio, fin] inclusivo en ambos extremos.
     *
     * @param inicio instante simulado de inicio (inclusive)
     * @param fin    instante simulado de fin (inclusive)
     * @throws IllegalArgumentException si fin < inicio
     */
    public IntervaloEjecucion(int inicio, int fin) {
        if (fin < inicio) {
            throw new IllegalArgumentException(
                "fin (" + fin + ") no puede ser menor que inicio (" + inicio + ")");
        }
        this.inicio = inicio;
        this.fin = fin;
    }

    /* ==================== GETTERS ==================== */

    public int getInicio() {
        return inicio;
    }

    public int getFin() {
        return fin;
    }

    /**
     * @return cantidad de instantes del intervalo (inclusive).
     */
    public int getLongitud() {
        return (fin - inicio) + 1;
    }

    /**
     * Indica si el instante t cae dentro del intervalo.
     */
    public boolean contiene(int t) {
        return t >= inicio && t <= fin;
    }

    /**
     * Intenta fusionar este intervalo con el siguiente.
     * Solo se fusiona si son CONTIGUOS (fin + 1 == otro.inicio).
     *
     * @param otro intervalo siguiente
     * @return el intervalo fusionado, o null si no son contiguos
     */
    public IntervaloEjecucion fusionarSiContiguo(IntervaloEjecucion otro) {
        if (otro == null) return null;
        if (this.fin + 1 == otro.inicio) {
            return new IntervaloEjecucion(this.inicio, otro.fin);
        }
        return null;
    }

    @Override
    public String toString() {
        return "[" + inicio + ".." + fin + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntervaloEjecucion)) return false;
        IntervaloEjecucion that = (IntervaloEjecucion) o;
        return inicio == that.inicio && fin == that.fin;
    }

    @Override
    public int hashCode() {
        return 31 * inicio + fin;
    }
}