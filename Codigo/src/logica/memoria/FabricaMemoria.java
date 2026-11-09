package logica.memoria;

import logica.ParticionadorDinamico;
import modelo.Memoria;

/**
 * Crea la estrategia de memoria que indican los parametros, sobre la zona
 * de usuario de la memoria. Es el unico lugar que conoce todas las
 * implementaciones: agregar una es crear su clase y un caso aqui.
 */
public final class FabricaMemoria {

    private FabricaMemoria() {
    }

    /**
     * @throws IllegalArgumentException si la estrategia no existe o sus
     *         parametros no sirven para esta memoria (por ejemplo, una
     *         particion mas grande que la zona de usuario)
     */
    public static EstrategiaMemoria crear(ParametrosMemoria p, Memoria memoria) {
        int inicio = memoria.getLimiteKernelUsuario();
        int tamano = memoria.getEspacioUsuarioDisponible();
        String nombre = p.getEstrategia();
        if (nombre == null) {
            throw new IllegalArgumentException("Estrategia de memoria desconocida");
        }
        switch (nombre) {
            case ParticionadorDinamico.NOMBRE:
                return new ParticionadorDinamico(memoria, inicio, tamano);
            case DinamicaNextFit.NOMBRE:
                return new DinamicaNextFit(memoria, inicio, tamano);
            case DinamicaBestFit.NOMBRE:
                return new DinamicaBestFit(memoria, inicio, tamano);
            case ParticionesFijas.NOMBRE_IGUAL:
                return ParticionesFijas.iguales(memoria, inicio, tamano, p.getParticionTamano());
            case ParticionesFijas.NOMBRE_DISTINTA:
                return ParticionesFijas.distintas(memoria, inicio, tamano, p.getListaParticiones());
            case Paginacion.NOMBRE:
                return new Paginacion(memoria, inicio, tamano, p.getPaginaTamano());
            case PaginacionVirtual.NOMBRE:
                return new PaginacionVirtual(memoria, inicio, tamano, p.getPaginaTamano(),
                        p.getMarcosPorProceso(), p.getReemplazo());
            default:
                throw new IllegalArgumentException("Estrategia de memoria desconocida: " + nombre);
        }
    }
}
