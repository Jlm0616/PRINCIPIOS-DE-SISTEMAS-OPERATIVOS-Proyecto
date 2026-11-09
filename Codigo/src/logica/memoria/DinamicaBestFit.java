package logica.memoria;

import logica.ParticionadorDinamico;
import modelo.Memoria;

/**
 * Particionamiento dinamico con BEST FIT (Stallings, seccion 7.2,
 * "Placement Algorithm", Figura 7.5).
 *
 * "Best-fit chooses the block that is closest in size to the request."
 * Recorre todos los huecos y usa el mas pequeno que alcance; con empate,
 * el de menor direccion.
 *
 * Segun el libro suele ser el peor de los tres: cada asignacion deja el
 * sobrante mas pequeno posible y la memoria se llena de huecos que ya no
 * sirven para nada (fragmentacion externa).
 */
public class DinamicaBestFit extends ParticionadorDinamico {

    public static final String NOMBRE = "Best Fit";

    public DinamicaBestFit(Memoria memoria, int inicioZonaUsuario, int espacioUsuarioDisponible) {
        super(memoria, inicioZonaUsuario, espacioUsuarioDisponible);
    }

    @Override
    public String getNombre() {
        return NOMBRE;
    }

    @Override
    protected int elegirHueco(int tamano) {
        int mejor = -1;
        for (int i = 0; i < bloquesLibres.size(); i++) {
            int t = bloquesLibres.get(i).tamano;
            if (t >= tamano && (mejor < 0 || t < bloquesLibres.get(mejor).tamano)) {
                mejor = i;
            }
        }
        return mejor;
    }
}
