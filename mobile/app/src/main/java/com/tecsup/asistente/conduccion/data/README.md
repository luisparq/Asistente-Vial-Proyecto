# Data

`local` contiene la base Room, entidades, DAO, relación viaje/eventos e índices.
`repository/RoomTripRepository` implementa el contrato de dominio con transacciones
y mapea las entidades a modelos independientes de Room. No hay API.

Solo este repositorio realiza escrituras de la aplicación. Iniciar recupera el
viaje activo o crea uno; el índice único nullable `activeSlot` impide dos activos.
Finalizar libera el índice y conserva la fecha de cierre al repetirse. Generar un
evento comprueba dentro de la transacción que el viaje siga activo y siempre
escribe `SIMULATED`. Las consultas observables usan Flow; el detalle usa una
consulta transaccional y ordena eventos por fecha e identificador.

Base de archivo `asistente_vial.db`, versión 1, con esquema exportado en
`app/schemas`. No se configura borrado destructivo: cambios futuros del esquema
requerirán una migración explícita. No se insertan datos al abrir la aplicación.

Etapa 3A añade `ble`: acceso a permisos/estado/scanner de Android, coordinación
de escaneo con deduplicación y límite temporal, y preparación GATT pendiente.
No se abre una conexión ni se producen datos de sensores. Esta capa no escribe
eventos ni modifica el repositorio de viajes; no se definen UUID ni protocolo.
