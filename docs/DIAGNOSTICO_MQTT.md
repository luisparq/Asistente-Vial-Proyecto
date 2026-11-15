# Diagnóstico MQTT de AVIGEM

El ESP32 publica cuentas crudas de la IMU con el sketch independiente [modo taller](../firmware/DiagnosticoMqttImu/README.md). Laravel las recibe con `mqtt:listen`. El administrador de flota las consulta en **Dispositivos → Diagnóstico**.

## Preparar Laravel y el dispositivo

Desde `backend/`, instala las dependencias y aplica la migración aditiva.

```bash
composer install
php artisan migrate
```

Elige un dispositivo ya registrado en la empresa. Su ficha en Dispositivos tiene una URL `/dashboard/devices/<id>`. Usa ese ID numérico y el mismo `DEVICE_CODE` que configuraste en el sketch. Reemplaza los dos argumentos de ejemplo.

```bash
php artisan mqtt:bind-device ID_DEL_DISPOSITIVO CODIGO_DEL_ESP32
```

Cada código identifica un único dispositivo y distingue mayúsculas de minúsculas. El comando valida que el dispositivo exista y rechaza conflictos. Laravel obtiene la empresa de ese registro.

## Configurar Mosquitto

En la configuración del broker agrega un listener TLS. Ajusta las rutas del certificado y de la clave al servidor. El proceso de Mosquitto debe poder leerlos.

```ini
listener 8883
allow_anonymous false
certfile /ruta/al/certificado-con-cadena.pem
keyfile /ruta/a/la/clave.pem
password_file /etc/mosquitto/avigem.passwd
acl_file /etc/mosquitto/avigem.acl
```

Crea un usuario por dispositivo y un usuario de lectura para Laravel. Usa `-c` solo al crear el archivo por primera vez. El comando solicita las contraseñas de forma interactiva.

```bash
sudo mosquitto_passwd -c /etc/mosquitto/avigem.passwd avigem-listener
sudo mosquitto_passwd /etc/mosquitto/avigem.passwd taller-esp32-a
```

En `/etc/mosquitto/avigem.acl`, usa una entrada de publicación por dispositivo. Sustituye `TALLER-ESP32-A` por su código exacto y configura `taller-esp32-a` como el usuario MQTT de ese sketch.

```text
user avigem-listener
topic read avigem/devices/+/imu

user taller-esp32-a
topic write avigem/devices/TALLER-ESP32-A/imu
```

Protege ambos archivos y reinicia Mosquitto tras agregar el listener.

```bash
sudo chown root:mosquitto /etc/mosquitto/avigem.passwd /etc/mosquitto/avigem.acl
sudo chmod 640 /etc/mosquitto/avigem.passwd /etc/mosquitto/avigem.acl
sudo systemctl restart mosquitto
```

Consulta la [configuración de Mosquitto](https://mosquitto.org/man/mosquitto-conf-5.html) para las opciones de TLS y ACL.

## Ejecutar el listener

Completa estas variables en `backend/.env`. Usa las credenciales de lectura y el nombre de host del certificado. Deja `MQTT_CA_FILE` vacío si PHP ya confía en la CA. Si hay caché de configuración, vuelve a generarla tras editar `.env`.

```dotenv
MQTT_HOST=
MQTT_PORT=8883
MQTT_USERNAME=
MQTT_PASSWORD=
MQTT_TLS=true
MQTT_CA_FILE=
```

```bash
php artisan mqtt:listen
```

El comando se vuelve a conectar y suscribe tras una interrupción. En servidor, ejecuta una sola instancia con systemd o Supervisor. En los ejemplos, adapta la ruta y el usuario a tu instalación. El usuario del servicio debe poder leer `.env` y acceder al almacenamiento y la base de datos de Laravel.

### systemd

Guarda como `/etc/systemd/system/avigem-mqtt.service`.

```ini
[Unit]
Description=AVIGEM Laravel MQTT diagnostics
Wants=network-online.target
After=network-online.target

[Service]
User=www-data
WorkingDirectory=/var/www/asistente-vial/backend
ExecStart=/usr/bin/php artisan mqtt:listen
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now avigem-mqtt.service
journalctl -u avigem-mqtt.service -f
```

### Supervisor

Guarda como `/etc/supervisor/conf.d/avigem-mqtt.conf`.

```ini
[program:avigem-mqtt]
directory=/var/www/asistente-vial/backend
command=/usr/bin/php artisan mqtt:listen
user=www-data
autostart=true
autorestart=true
redirect_stderr=true
stdout_logfile=/var/log/avigem-mqtt.log
```

```bash
sudo supervisorctl reread
sudo supervisorctl update
sudo supervisorctl status avigem-mqtt
```

## Probar con mosquitto_pub

Usa un broker y un dispositivo de taller asociados a una base de pruebas. Las cuentas del ejemplo son **sintéticas**. En Linux, guarda el usuario y la contraseña de publicación en `~/.config/mosquitto_pub` con permisos `600`, mediante las opciones `-u` y `-P` en líneas separadas. Consulta el [manual de mosquitto_pub](https://mosquitto.org/man/mosquitto_pub-1.html) para la ubicación del archivo en otros sistemas.

```bash
mosquitto_pub -h BROKER_DE_PRUEBA -p 8883 --cafile /ruta/a/ca.pem -q 1 \
  -t 'avigem/devices/TALLER-ESP32-A/imu' \
  -m '{"sessionId":"566b07e4-78ca-4f0b-a15c-7ed5705ca2a0","deviceCode":"TALLER-ESP32-A","sequence":0,"axRaw":120,"ayRaw":-40,"azRaw":16500,"gxRaw":7,"gyRaw":-2,"gzRaw":4}'
```

1. Abre **Dispositivos → Diagnóstico** y selecciona el dispositivo y su sesión. Debe aparecer un punto por eje.
2. Repite el comando. La misma sesión y secuencia conservan la muestra original. Cambia la secuencia para agregar otra.
3. Cambia `deviceCode` sin cambiar el tema o envía un eje fuera de −32768 a 32767. Laravel debe descartar el mensaje.
4. Reinicia el broker de pruebas y publica una secuencia nueva cuando el listener vuelva a conectarse.

La gráfica usa los últimos 30 segundos respecto a la última muestra guardada de la sesión. `sampled_at` representa la hora de recepción porque el sketch no envía una marca de tiempo. `finished_at` queda vacío porque el sketch tampoco envía un cierre de sesión. El ESP32 publica con QoS 0, así que una desconexión puede causar huecos.
