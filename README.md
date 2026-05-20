# FocusTime

FocusTime es una aplicacion local de escritorio para registrar el tiempo dedicado a tareas personales, habitos o bloques de trabajo. Esta pensada para usar objetivos diarios claros, fichar sesiones de tiempo y revisar el historial desde un calendario.

La aplicacion funciona completamente en local: no tiene servidor, no usa login y guarda los datos en SQLite en la maquina del usuario.

## Descargar

Descarga la ultima version para Windows desde [GitHub Releases](https://github.com/CarlesVall/FocusTime/releases/latest).

Opciones disponibles:

- Instalador: `FocusTime-1.0.0-SNAPSHOT-windows-setup.exe`.
- Portable: `FocusTime-1.0.0-SNAPSHOT-windows-portable.zip`.

## Funcionalidades principales

- Crear, editar y eliminar tareas.
- Definir un objetivo diario por tarea en horas y minutos.
- Seleccionar los dias de la semana previstos para cada tarea.
- Iniciar, pausar y cancelar temporizadores por tarea.
- Registrar automaticamente sesiones al pausar un temporizador.
- Ejecutar varias tareas con temporizador activo al mismo tiempo.
- Ver sesiones del dia, filtradas por tarea o mostrando todas.
- Editar y eliminar sesiones registradas en la vista de hoy.
- Consultar el historial en un calendario semanal o mensual.
- Ver el detalle de un dia con resumen por tarea y tabla de sesiones.

## Pantallas

### Tareas

La pestana de tareas permite gestionar los objetos principales de seguimiento.

Cada tarea se muestra como una tarjeta con:

- Nombre.
- Objetivo diario.
- Dias planificados.

Al crear o editar una tarea se puede configurar:

- Nombre de la tarea.
- Objetivo diario dividido en horas y minutos.
- Dias de la semana en los que se espera trabajar esa tarea.

Al eliminar una tarea tambien se eliminan sus sesiones asociadas, por lo que la accion pide confirmacion.

### Hoy

La pestana de hoy esta orientada al fichaje diario.

Muestra:

- Fecha actual y dia de la semana.
- Lista de tareas activas.
- Estado visual de tareas pendientes, completadas o con temporizador en marcha.
- Temporizador en formato `HH:mm:ss`.
- Botones para iniciar, pausar o cancelar la tarea seleccionada.
- Tabla de sesiones registradas en el dia.
- Total del tiempo mostrado en la tabla.

Las sesiones de hoy pueden editarse o eliminarse si se registra un tiempo incorrecto.

### Calendario

El calendario sirve solo para consultar el registro historico.

Incluye:

- Vista mensual.
- Vista semanal.
- Navegacion entre meses o semanas.
- Indicadores visuales en los dias con registros.
- Tiempo total y numero de sesiones por dia.

Al seleccionar un dia se muestra una vista de detalle con:

- Total registrado.
- Numero de sesiones.
- Resumen visual por tarea.
- Comparacion entre tiempo empleado y objetivo.
- Tabla de sesiones del dia.

Desde el detalle se puede volver facilmente al calendario.

## Persistencia de datos

FocusTime guarda los datos en una base de datos SQLite local.

La ruta usada por la aplicacion es:

```text
%APPDATA%\FocusTime\focustime.db
```

En Windows suele equivaler a:

```text
C:\Users\<usuario>\AppData\Roaming\FocusTime\focustime.db
```

La base de datos no se guarda dentro del repositorio ni junto al ejecutable portable. Esto permite actualizar la aplicacion sin perder los registros.

## Stack tecnico

- Java 21.
- JavaFX.
- Maven.
- SQLite.
- JPA.
- Hibernate ORM.

## Ejecutar en desarrollo

Requisitos:

- JDK 21.
- Maven.

Desde la raiz del repositorio:

```powershell
mvn clean javafx:run
```

## Descargar la aplicacion

Los usuarios finales no necesitan Java, Maven ni clonar el repositorio.

La forma recomendada es descargar FocusTime desde la seccion de releases del proyecto:

```text
https://github.com/CarlesVall/FocusTime/releases
```

Hay dos formatos para Windows:

- `FocusTime-<version>-windows-setup.exe`: instalador normal de Windows.
- `FocusTime-<version>-windows-portable.zip`: version portable sin instalacion.

El instalador crea accesos directos y una entrada normal de aplicacion en Windows.

El ZIP portable contiene:

```text
FocusTime/
    FocusTime.exe
    app/
    runtime/
```

El ejecutable portable debe mantenerse junto a las carpetas `app/` y `runtime/`.

## Estado del proyecto

FocusTime esta en una fase funcional de uso local. El objetivo actual es mantener una aplicacion simple, sin backend separado ni despliegue online, centrada en registrar tiempo y revisar progreso diario e historico.
