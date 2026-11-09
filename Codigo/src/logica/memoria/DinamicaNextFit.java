package logica.memoria;

import logica.ParticionadorDinamico;
import modelo.Memoria;

/**
 * Particionamiento dinamico con NEXT FIT (Stallings, seccion 7.2,
 * "Placement Algorithm", Figura 7.5).
 *
 * "Next-fit begins to scan memory from the location of the last placement
 * and chooses the next available block that is large enough." Recuerda
 * donde termino la ultima asignacion y busca desde ahi hacia adelante,
 * dando la vuelta al llegar al final de la memoria.
 *
 * Segun el libro suele ser algo peor que First Fit: rompe rapido el hueco
 * grande del final de la memoria.
 */
public class DinamicaNextFit extends ParticionadorDinamico {

    public static final String NOMBRE = "Next Fit";

    /** Direccion donde termino la ultima asignacion. */
    private int ultimaPosicion;

    public DinamicaNextFit(Memoria memoria, int inicioZonaUsuario, int espacioUsuarioDisponible) {
        super(memoria, inicioZonaUsuario, espacioUsuarioDisponible);
        this.ultimaPosicion = inicioZonaUsuario;
    }

    @Override
    public String getNombre() {
        return NOMBRE;
    }

    /**
     * Empieza por el primer hueco que termina despues de la ultima
     * asignacion y recorre la lista en forma circular. La lista de huecos
     * esta ordenada por direccion.
     */
    @Override
    protected int elegirHueco(int tamano) {
        int n = bloquesLibres.size();
        if (n == 0) return -1;

        int inicio = 0;
        while (inicio < n) {
            BloqueLibre b = bloquesLibres.get(inicio);
            if (b.inicio + b.tamano > ultimaPosicion) break;
            inicio++;
        }

        for (int k = 0; k < n; k++) {
            int i = (inicio + k) % n;
            if (bloquesLibres.get(i).tamano >= tamano) return i;
        }
        return -1;
    }

    @Override
    protected void despuesDeAsignar(int base, int tamano) {
        ultimaPosicion = base + tamano;
    }

    @Override
    public String resumen() {
        return super.resumen() + " | ultima asignacion termino en " + ultimaPosicion;
    }
}
