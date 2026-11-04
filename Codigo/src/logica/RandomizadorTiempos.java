package logica;

import java.util.Random;

/**
 * Clase utilitaria encargada de generar tiempos de llegada aleatorios
 * para los procesos nuevos.
 * 
 * Regla actual (sugerida por el profesor):
 * Un proceso tendra un tiempo de llegada aleatorio en el rango
 * [relojGlobal, relojGlobal + 6].
 * 
 * PROYECTO 2 - SOLID:
 *  - SRP: Su unica responsabilidad es generar el tiempo aleatorio.
 *  - OCP: Si el rango cambia (ej. 0 a 10), solo se modifica el
 *    parametro RANGO_ADICIONAL.
 *  - DIP: No depende de nadie, es una clase pura.
 */
public class RandomizadorTiempos {

    /** El rango adicional sobre el reloj global actual. */
    public static final int RANGO_ADICIONAL = 6;

    private final Random random;

    public RandomizadorTiempos() {
        this.random = new Random();
    }

    /**
     * Constructor que permite inyectar una semilla para pruebas.
     */
    public RandomizadorTiempos(long semilla) {
        this.random = new Random(semilla);
    }

    /**
     * Genera un tiempo de llegada aleatorio basado en el reloj global.
     * 
     * @param relojGlobal El tiempo actual del sistema.
     * @return Un entero entre relojGlobal y relojGlobal + RANGO_ADICIONAL.
     */
    public int generarTiempoLlegada(int relojGlobal) {
        if (relojGlobal < 0) relojGlobal = 0;
        
        // nextInt(7) genera un numero entre 0 y 6 (inclusive).
        // Al sumarle el reloj, obtenemos un rango [reloj, reloj+6].
        int offset = random.nextInt(RANGO_ADICIONAL + 1);
        
        return relojGlobal + offset;
    }
}