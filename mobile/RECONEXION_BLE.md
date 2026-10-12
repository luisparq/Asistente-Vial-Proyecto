# Reconexión BLE limitada

El dispositivo elegido se recuerda localmente por servidor/empresa/usuario de la sesión, cifrado AES-GCM mediante la clave Android Keystore existente. No crea una asignación Device en Laravel. Demo permite conexión manual pero no reconexión persistente. Cambiar de cuenta cierra GATT; las preferencias de cada cuenta permanecen separadas.

Conectar desde Dispositivo habilita el recuerdo y la reconexión para esa cuenta. Al abrir la app en primer plano se recupera la selección y se utiliza el mismo GattBleConnection. Hasta 4 intentos por ventana de primer plano, con esperas 0/5/15/30 s y timeout GATT existente de 20 s por intento. El presupuesto también limita pérdidas repetidas dentro de esa ventana; no se reinicia con cada conexión exitosa. No hay escaneo automático ni bucle periódico. Si el dispositivo regresa dentro de los intentos, puede reconectar; al agotarlos se requiere Habilitar reconexión, volver al primer plano o restaurar Bluetooth/permisos.

Bluetooth/permisos no disponibles y segundo plano cancelan intentos automáticos. No se solicitan permisos nuevos. Las conexiones ya establecidas conservan el comportamiento existente; no se promete recepción ni reconexión si Android suspende/cierra el proceso. Sin foreground service, ejecución permanente ni reconexión garantizada tras restricciones de batería.

Desconectar guarda automatic=false y libera GATT; no vuelve a conectar hasta Conectar/Habilitar reconexión. Olvidar dispositivo elimina el recuerdo de esa cuenta. Cada sesión nueva redescubre servicios y suscribe IMU y después GPS con el transporte existente; las muestras anteriores se limpian y callbacks de sesiones cerradas se ignoran.

Prueba física pendiente: iniciar sesión, conectar AsistenteVial-IMU y confirmar IMU/GPS. Cerrar/reabrir la app, comprobar conexión automática. Apagar/encender ESP32 dentro de la ventana de reintentos; verificar nuevas mediciones. Pulsar Desconectar y reiniciar la app: debe permanecer desconectado. Habilitar reconexión permite otro intento. Revocar permisos/apagar Bluetooth suspende; restaurarlos con la app visible permite recuperar. No se cambian UUID, frecuencias, firmware, Room, API ni eventos SENSOR.

## Archivos de esta fase
Rutas Kotlin relativas a `app/src/main/java/com/tecsup/asistente/conduccion/`:
- Nuevos: `data/ble/BleReconnectController.kt`, `SecureBleDeviceStore.kt` y `domain/ble/RememberedBleStore.kt`.
- Modificados: `MainActivity.kt`, `core/di/BleModule.kt`, `data/ble/DefaultBleRepository.kt`, `data/network/SecureSessionStore.kt` (solo helpers de cifrado local; sesión intacta), `domain/ble/BleRepository.kt`, `feature/ble/BleViewModel.kt`, `BleScreen.kt`, `BleContent.kt`.
- Pruebas: nuevo `app/src/test/java/com/tecsup/asistente/conduccion/BleReconnectTest.kt`; ajustados `BleUiTest.kt` y `SecureSessionTest.kt` en androidTest.

## Verificación ejecutada — 11/10/2026
`assembleDebug` correcto. 28 pruebas unitarias seleccionadas aprobadas: BleReconnectTest 8, BleRepositoryTest 7, GattConnectionTest 4, BleViewModelTest 1, GpsTelemetryTest 6 y BlePermissionPolicyTest 2. Incluye fallo GATT síncrono, presupuesto progresivo, recuperación de ambos flujos, callbacks tardíos, suspensión manual persistente, permisos, segundo plano, cambio de cuenta y dispositivo. Transportes/frames son TEST, sin prueba física.

En emulador Android 14: 7 pruebas instrumentadas aprobadas (BleUiTest 5 y SecureSessionTest 2), con controles de reconexión/desconexión y almacenamiento AES-GCM restaurado entre instancias y separado por cuenta. El APK final y los 28 casos unitarios se recompilaron tras reforzar el guard de simultaneidad; no se repitió instrumentación para ese guard interno, sin cambios de interfaz/cifrado. No se ejecutó suite completa, Laravel ni firmware. Sin commit/push.
