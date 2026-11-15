# Proyecto #2 — Gestor de Procesos y Memoria — IC-6600

## Integrantes

| Nombre | Carné |
|---|---|
| Julian Lizano Monge | 2024188887 |
| Keyner Cerdas Morales | 2024108270 |

- **Curso:** Principios de Sistemas Operativos (IC-6600)
- **Profesor:** Ing. Cristian Campos Agüero
- **Centro Académico:** de Limón
- **Institución:** Instituto Tecnológico de Costa Rica

---

## Video de demostración

**Pendiente:** falta

---

## Valoración del proyecto

**5 / 5.** Están todos los algoritmos de CPU y de memoria que pide el enunciado, hasta 5 CPU, la asignación de núcleo por proceso, el Gantt en vivo, los cambios de contexto y las estadísticas acumuladas. FCFS, SPN, SRT, HRRN, RR y MLFQ reproducen los valores de la Tabla 9.5 de Stallings.

---

## Tecnologías utilizadas

- **Java 21**
- **Java Swing** para la interfaz gráfica
- **NetBeans** (proyecto Ant, carpeta `Codigo/`)
- **Git / GitHub** para control de versiones

---

## Descripción

Simulador de un sistema operativo con varias CPU, basado en el Proyecto 1. A partir de los archivos `.asm` que se cargan, el simulador crea procesos, los planifica con el algoritmo elegido, les asigna memoria con la estrategia elegida y muestra la ejecución segundo a segundo.

- **De 1 a 5 CPU**, cada una con su despachador, y hasta **5 procesos por CPU**.
- **10 algoritmos de planificación:** FCFS, SPN, SRT, RR, HRRN, Selfish RR, Lottery, Prioridad, Guaranteed y MLFQ.
- **7 estrategias de memoria:** dinámica First Fit, Next Fit y Best Fit; particiones fijas iguales y de distinto tamaño; paginación y paginación virtual (con FIFO o LRU).
- **Ráfaga** = cantidad de instrucciones del archivo. **Cada instrucción dura 1 segundo.**
- **Tiempos de llegada** aleatorios al cargar (entre el reloj actual y 6 segundos después), y editables antes de ejecutar.
- **Memoria principal**, **disco** y **memoria virtual (swap)** configurables.
- **Diagrama de Gantt en vivo**, **bitácora de cambios de contexto** y **estadísticas acumuladas** entre ejecuciones.

---

## Objetivos alcanzados

Numerados como en el enunciado.

- [x] **1.** Algoritmos de planificación de la CPU agregados sobre el Proyecto 1.
- [x] **2 y 3.** Ráfaga y tiempo de llegada de cada proceso; la ráfaga es la cantidad de líneas del archivo.
- [x] **4.** Cada línea del archivo ocupa una posición de memoria.
- [x] **5.** Ventana "Asignar Tiempos y Prioridades" para cambiar el tiempo de llegada, la prioridad, el **núcleo asignado** y los **boletos de Lottery** de cada proceso.
- [x] **7.** Ventana de configuración con el algoritmo y sus parámetros: quantum (RR, Selfish RR, Lottery), `a` y `b` (Selfish RR), quantum de cada cola (MLFQ) y semilla de los sorteos (Lottery).
- [x] **8.** Configuración del tamaño de la memoria principal, del disco y del swap, y de la estrategia de asignación con sus parámetros.
- [x] **9 y 10.** De 1 a 5 CPU; cada CPU acepta hasta 5 procesos asignados.
- [x] **11.** Al terminar, el proceso pasa a EXIT, se libera su memoria (bloque, partición o marcos) y su BCP, y entra el siguiente proceso que estaba esperando en el swap.
- [x] **12.** Ejecución automática (segundo a segundo) y manual (paso a paso).
- [x] **13.** Las interrupciones no piden ENTER y ya no existe INT 09H (teclado).
- [x] **14.** Cada instrucción dura 1 segundo. Diagrama de Gantt en vivo: Núcleo | Proceso | 1, 2, 3 ... 80 (crece si la ejecución dura más).
- [x] **15.** Selección del algoritmo de CPU y de la estrategia de memoria:

  | CPU | Memoria |
  |---|---|
  | FCFS, SRT, SPN, RR, HRRN | Paginación física y virtual (FIFO o LRU) |
  | Selfish Round Robin, Lottery | Particiones fijas iguales |
  | Prioridad, Guaranteed Scheduling | Particiones fijas de distinto tamaño |
  | Multilevel Feedback Queue | Dinámica First Fit, Next Fit y Best Fit |

- [x] **16.** Estadísticas por ejecución y acumuladas: nombre del algoritmo, tiempo de inicio, tiempo final, tiempo ocioso, turnaround (fin − llegada), Tr/Ts, promedios, cantidad de CPU y estrategia de memoria.
- [x] Visualización del estado de los procesos, del BCP de cada CPU y de los cambios de contexto.
- [x] Carga de varios archivos a la vez con detección de errores de sintaxis.
- [x] Programas que nunca caben en memoria (por ejemplo, más grandes que la partición más grande) se rechazan al cargarlos con un mensaje que explica por qué.
- [x] Configuración guardada en `config.txt`, con corrección de valores inválidos.

## Objetivos no alcanzados o parciales


## ¿Cómo se usa?

### 1. Abrir y ejecutar

Abrir la carpeta `Codigo/` como proyecto en NetBeans (JDK 21) y ejecutar. La clase principal es `App.Main`.

### 2. Configurar

Botón **Configurar**. Tiene cuatro pestañas:

- **Memoria:** tamaño de la memoria principal y del kernel.
- **Disco:** tamaño del disco, del swap (memoria virtual) y cantidad de archivos.
- **CPUs:** cantidad de CPU (1 a 5), algoritmo y sus parámetros.
- **Asignacion de memoria:** estrategia y sus parámetros. Solo se muestran los de la estrategia elegida.

Todo se guarda en `Codigo/config.txt`. Ejemplo:

```properties
memoria=3030
kernel=909
disco=512
memoria_virtual=64
max_archivos=15
cpus=1
algoritmo_cpu=FCFS
quantum=1
srr_a=2
srr_b=1
mlfq_quantums=1,2,4
lottery_semilla=0
estrategia_memoria=First Fit
particion_tamano=16
particiones=8,16,32,64
pagina_tamano=4
marcos_por_proceso=2
reemplazo=FIFO
```

| Clave | Para qué sirve |
|---|---|
| `algoritmo_cpu` | `FCFS`, `SPN`, `SRT`, `RR`, `HRRN`, `Selfish RR`, `Lottery`, `Prioridad`, `Guaranteed` o `MLFQ` |
| `quantum` | Quantum de RR, Selfish RR y Lottery |
| `srr_a`, `srr_b` | Selfish RR: cuánto sube por segundo la prioridad de un proceso nuevo (`a`) y la de uno aceptado (`b`) |
| `mlfq_quantums` | Quantum de cada cola de MLFQ, de la más alta a la más baja (1 a 10 colas) |
| `lottery_semilla` | Semilla de los sorteos de Lottery; `0` = sorteos distintos en cada ejecución |
| `estrategia_memoria` | `First Fit`, `Next Fit`, `Best Fit`, `Fija igual`, `Fija distinta`, `Paginacion` o `Paginacion virtual` |
| `particion_tamano` | Tamaño de cada partición (Fija igual) |
| `particiones` | Tamaños de las particiones, en orden (Fija distinta) |
| `pagina_tamano` | Tamaño de página y de marco (Paginacion y Paginacion virtual) |
| `marcos_por_proceso` | Marcos que recibe cada proceso (Paginacion virtual) |
| `reemplazo` | `FIFO` o `LRU` (Paginacion virtual) |

Si un valor es inválido, se corrige al valor por defecto y se avisa.

### 3. Cargar programas

Botón **Cargar archivos** y seleccionar uno o varios `.asm`. Cada proceso recibe un tiempo de llegada aleatorio. Los que llegan más tarde, o los que no caben ahora en la memoria, esperan en el swap en estado NEW.

### 4. Ajustar llegadas, prioridades y núcleos (opcional)

Botón **Asignar Tiempos y Prioridades**. Por cada proceso se puede cambiar:

- el **tiempo de llegada**;
- la **prioridad** (para el algoritmo Prioridad);
- el **núcleo**: "Cualquiera" (va a la primera CPU libre) o una CPU concreta (solo esa CPU lo ejecuta; máximo 5 procesos por CPU);
- los **boletos** de Lottery (por defecto, tantos como su ráfaga).

### 5. Ejecutar

- **Paso a paso:** cada clic avanza 1 segundo.
- **Ejecutar:** modo automático, segundo a segundo, hasta que terminen todos los procesos.
- **Limpiar:** reinicia el sistema con la configuración actual y borra el historial.
- **Estadísticas:** abre la ventana de resultados.

---

## Paneles de la ventana principal

| Panel | Qué muestra |
|---|---|
| **Procesos** | Procesos en RAM y en NEW con su estado, y en qué CPU está cada uno |
| **Detalle del BCP** | El BCP del proceso de la CPU elegida: registros, PC, IR, base, alcance, estado |
| **Memoria principal** | Contenido de la memoria. En la zona de usuario, la columna Zona dice de quién es cada celda según la estrategia (bloque, partición, marco y página), con la fragmentación interna en naranja. Debajo, el resumen: huecos, particiones o marcos ocupados y fallos de página |
| **Disco** | Índice, memoria virtual (swap) y archivos |
| **Pantalla** | Salida de INT 10H y mensajes del sistema |
| **Cambios de contexto** | Cada carga y guardado de contexto, expulsión, bloqueo por disco, fallo de página y liberación de memoria, con el segundo en que pasó |
| **Diagrama de Gantt (en vivo)** | Núcleo, proceso y segundo. En color, lo que ejecuta; rayado, la espera de disco (INT 21H o fallo de página) |

## Ventana de estadísticas

- **Resumen:** algoritmo, procesos, duraciones, tiempo ocioso, tiempo de inicio y final, Tr promedio, Tr/Ts promedio y cantidad de CPU.
- **Procesos:** por proceso, CPU, llegada, inicio, fin, duración, turnaround y Tr/Ts.
- **Diagrama Gantt:** la ejecución completa por CPU.
- **Acumulados:** una fila por cada ejecución (algoritmo, memoria, CPU, inicio, fin, tiempo ocioso, Tr y Tr/Ts promedio, estado) y los promedios de todas.

---

## Formato de los archivos `.asm`

Una instrucción por línea, en mayúsculas o minúsculas. Desde `;` hasta el final de la línea es un comentario (salvo dentro de comillas). Las líneas vacías y las que solo tienen comentario no cuentan para la ráfaga.

```asm
; suma dos valores y la imprime
MOV AX, 10
MOV BX, 20
LOAD AX
ADD BX
STORE DX      ; el resultado queda en DX
INT 10H
INT 20H
```

Cada programa debe terminar con `INT 20H`. Se rechazan, con el número de línea: opcodes y registros desconocidos, números inválidos, argumentos de más o de menos, comas sobrantes (`ADD BX,`), comillas sin cerrar e `INT 09H`.

### Instrucciones soportadas

Todas duran 1 segundo. Registros válidos: `AC`, `AX`, `BX`, `CX`, `DX`, `AH`, `AL`.

| Instrucción | Descripción |
|---|---|
| `LOAD AX` | Carga el valor del registro en el AC |
| `STORE BX` | Guarda el AC en el registro |
| `MOV BX, AX` / `MOV BX, 5` | Copia un registro o un valor a un registro |
| `ADD BX` / `SUB BX` | Suma o resta el registro al AC |
| `INC` / `INC AX`, `DEC` / `DEC AX` | Incrementa o decrementa el AC o el registro |
| `SWAP AX, BX` | Intercambia dos registros |
| `CMP AX, BX` | Compara dos registros (bandera de igualdad) |
| `JMP +n`, `JE +n`, `JNE +n` | Salto relativo (con signo), siempre o según la comparación |
| `PARAM v1, v2, v3` | Hasta 3 valores a la pila |
| `PUSH AX` / `POP AX` | Pila de 5 posiciones |
| `INT 10H` | Imprime DX en pantalla |
| `INT 20H` | Termina el programa |
| `INT 21H` | Archivos según AH: `3CH` crear, `3DH` abrir, `4DH` leer, `40H` escribir, `41H` eliminar. El proceso queda bloqueado 1 segundo por el disco y conserva su CPU |

---

## Algoritmos de planificación

| Algoritmo | Expropiativo | Cómo elige |
|---|---|---|
| **FCFS** | No | Por orden de llegada |
| **SPN** | No | La ráfaga más corta |
| **SRT** | Sí | Lo que le queda más corto; expulsa si llega uno con menos |
| **RR** | Por quantum | Por turnos con quantum fijo |
| **HRRN** | No | La mayor razón de respuesta `(espera + servicio) / servicio` |
| **Selfish RR** | Por quantum | RR entre los aceptados; un nuevo entra cuando su prioridad (sube `a` por segundo) alcanza a la de los aceptados (suben `b`) |
| **Lottery** | Por quantum | Sorteo; más boletos = más probabilidad |
| **Prioridad** | No | El de prioridad más alta (número menor) |
| **Guaranteed** | Sí | El más atrasado respecto de lo que le toca: menor `CPU recibida / tiempo en el sistema` |
| **MLFQ** | Sí | Varias colas con quantum creciente; al agotar el quantum baja de cola, y uno que llega a una cola más alta expropia |

Con varias CPU, cada CPU libre toma el siguiente proceso que elige el algoritmo, entre los que puede ejecutar.

## Estrategias de memoria

| Estrategia | Cómo asigna |
|---|---|
| **First Fit** | Primer hueco que alcance, desde el inicio |
| **Next Fit** | Primer hueco que alcance desde donde terminó la última asignación |
| **Best Fit** | El hueco más pequeño que alcance |
| **Fija igual** | Particiones del mismo tamaño; cada proceso ocupa una |
| **Fija distinta** | Particiones de los tamaños dados; cada proceso va a la libre más pequeña donde quepa |
| **Paginación** | Páginas en cualquier marco libre; se cargan todas al inicio. El PC es una dirección lógica y la CPU la traduce con la tabla de páginas |
| **Paginación virtual** | Por demanda: al inicio solo entra la página 0. Si falta una página hay fallo de página (el proceso pierde ese segundo) y se reemplaza una página del mismo proceso con FIFO o LRU |

Las estrategias reparten la zona de usuario de la memoria principal. Los procesos que esperan (NEW) se guardan en el swap del disco.

---

## Estructura del proyecto

```
Codigo/src/
├── App/            Main
├── config/         ConfiguracionExterna (lee y guarda config.txt)
├── gui/            Ventana principal, configuración, estadísticas, paneles y Gantt en vivo
├── logica/         GestorProcesos, Despachador, EjecutorCPU, Ensamblador, Planificador...
│   ├── cpu/            GestorCPUs, Nucleo, Balanceador
│   ├── planificacion/  Un Planificador* por algoritmo (patrón Estrategia)
│   ├── memoria/        Una clase por estrategia de memoria (patrón Estrategia)
│   └── estadisticas/   Estadisticas, RegistroGantt, HistorialEjecuciones...
└── modelo/         BCP, CPU, Memoria, Disco, Instruccion, EstadoProceso
```

Los algoritmos de CPU implementan `EstrategiaPlanificacion` y las estrategias de memoria implementan `EstrategiaMemoria`. Así, agregar un algoritmo o una estrategia no cambia `GestorProcesos`.

---

## Seguridad

Se mantiene la de la versión anterior (ver [SEGURIDAD.md](SEGURIDAD.md), escrito para el Proyecto 1):

1. **Kernel y usuario separados:** los programas solo ocupan la zona de usuario; los BCP y las tablas del sistema están en el kernel.
2. **Validación del PC:** si sale del alcance del proceso, el proceso termina con error.
3. **Validación del ensamblador** antes de cargar cualquier archivo.
4. **Pila** de 5 posiciones y **archivos abiertos** (5 por proceso) con detección de desbordamiento.
5. **Configuración** con valores inválidos corregidos automáticamente.
6. **Ejecución automática** con límite: un ciclo infinito termina con un mensaje de error en vez de colgar la aplicación.

---

## Referencias

- W. Stallings, *Operating Systems: Internals and Design Principles*, 9.ª ed. Capítulos 7 (memoria), 8 (memoria virtual), 9 (planificación de un procesador) y 10 (planificación multiprocesador).
- A. Tanenbaum, *Modern Operating Systems*, sección 2.4.3 (Lottery y Guaranteed Scheduling).

---

## Repositorio

[GitHub — PRINCIPIOS-DE-SISTEMAS-OPERATIVOS-Proyecto](https://github.com/Jlm0616/PRINCIPIOS-DE-SISTEMAS-OPERATIVOS-Proyecto)
