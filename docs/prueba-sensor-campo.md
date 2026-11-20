# Laboratorio de calibración y prueba del sensor

Corrección del 19 de noviembre de 2026: se recuperan las gráficas y se elimina el
campo obligatorio de montaje y el marcado manual de detecciones. El laboratorio
sigue en Dispositivo → Prueba del sensor, en la compilación de desarrollo.

## Flujo en el carro

1. Fija el módulo rígidamente. Conecta AVIGEM One por Bluetooth y abre el laboratorio.
   Verás acelerómetro y giroscopio en vivo; no necesitas escribir valores.
2. Con el carro detenido y motor apagado, pulsa **Calibrar reposo** y espera 30 s.
   La app calcula referencia, sesgo del giroscopio y comprueba variabilidad. Si se
   mueve, faltan muestras o falla la recepción, pide repetir.
3. El acompañante inicia **Aprender: aceleración normal** cuando se pueda acelerar
   suavemente en línea recta. Durante esa captura evita frenar o girar. Pulsa
   **Terminar esta medición**. La dirección de avance se estima a partir de esos datos.
4. Repite con **frenada normal**, reduciendo velocidad suavemente en recta, y con
   **giros normales** hacia ambos lados. Cada medición requiere muestras suficientes;
   se detiene como máximo a los 60 s. Si no observó la maniobra, no inventa un límite.
5. Con las tres maniobras aprendidas, pulsa **Iniciar prueba con buzzer**. La detección
   es automática: si una señal supera sostenidamente su límite aprendido, se muestra
   el exceso y Android envía la orden BLE al ESP32. El firmware ejecuta su pulso
   limitado y devuelve BUZZER_DONE; la pantalla distingue enviado y confirmado.
6. **Detener prueba y guardar** conserva las muestras. En **Ver datos y archivos
   guardados** puedes exportar CSV para revisarlo/importarlo en Laravel.

No hace falta provocar una frenada en seco para calibrar. Las lecciones registran
conducción normal. El acompañante opera la app; el conductor mantiene su atención
en la conducción. Mantén esta pantalla abierta: salir o perder recepción detiene
la captura/detección. Esta versión no graba ni dispara buzzer en segundo plano.

## Perfil aprendido

Se guarda un perfil por dirección BLE en almacenamiento privado del teléfono:
referencia de aceleración, sesgo gyro, sentido de avance aprendido, nivel normal
observado y límites calculados. Al reconectar requiere verificar reposo. Puede
reutilizar los límites anteriores si la referencia vertical resulta compatible,
pero eso no demuestra que no haya cambiado el montaje: una rotación horizontal
puede ser indistinguible en reposo. Si cambias montaje, repite la aceleración normal
(eso descarta también los demás límites) y las otras dos lecciones.

No se restan todos los ejes como si la gravedad fuera error. El sentido de avance
se infiere de una aceleración horizontal coherente y el eje de giro de la vertical
del reposo. Los cambios dinámicos de inclinación/pendiente no están compensados por
una fusión de orientación completa y pueden generar falsos positivos.

## Criterios iniciales de prueba

- Reposo: al menos 100 muestras en 30 s, sin intervalos >750 ms; DE nominal por eje
  ≤0.02 g y ≤1 °/s; magnitud de reposo entre 0.7 y 1.3 g como control amplio.
- Cada lección: al menos 12 muestras y 3 s; al menos 8 muestras con maniobra observada.
  Aceleración/frenada útil >0.03 g; giro útil >2 °/s, con ambos sentidos observados.
- Nivel normal: percentil 95 de las muestras útiles de la maniobra.
- Límite provisional: máximo entre 1.8 veces ese nivel, un margen de ruido y un
  mínimo de 0.12 g o 10 °/s. La fórmula no procede de una validación vehicular.
- Detección: 3 muestras consecutivas sobre límite y duración ≥300 ms. No se unen
  intervalos >750 ms. Pausa entre detecciones de 3 s y confirmación de buzzer de 2 s.

Estos límites describen un **exceso respecto al comportamiento enseñado**; no son
una clasificación certificada de peligro. La calibración real depende de las
muestras que aún debes obtener en el carro. Las pruebas de software son sintéticas.

## Captura y límites actuales

Se conservan los CSV existentes de 40 columnas para compatibilidad con Laravel:
RAW, conversión nominal, referencia de reposo, diferencias, GPS y tiempos Android.
En la prueba, las etiquetas se asignan automáticamente a la muestra de detección;
no son anotaciones manuales ni eventos SENSOR oficiales. En el aprendizaje de giros
se conserva NORMAL y el motivo de cierre indica la lección, para no inventar un
sentido de giro que aún no está siendo clasificado. Los RAW permanecen originales.

La detección y el perfil están en **Android**, no en firmware autónomo. El ESP32
mide y ejecuta el buzzer por orden BLE. Sin el teléfono conectado y esta prueba
activa, este laboratorio no detecta ni avisa. No se cambió la ruta MQTT/API ni se
cargó firmware en esta corrección.

El firmware actual usa 250 ms por muestra (4 Hz). La app muestra la frecuencia real.
Los picos breves pueden quedar fuera; con 4 Hz la regla de tres muestras tarda al
menos aproximadamente 500 ms. No se afirma detección completa de frenadas rápidas.
Antes de usarlo como detector de conducción debe evaluarse una frecuencia mayor,
fusión de orientación, velocidad y validación física. Desinstalar o borrar datos
de la app elimina perfiles y CSV privados; exporta los ensayos que quieras conservar.

Verificación de esta corrección: compilación y APK correctos; 126 pruebas unitarias y 2 pruebas de interfaz aprobadas en emulador Android 14. No se instaló este APK en el teléfono físico ni se comprobó el sonido real del buzzer o un recorrido en vehículo desde aquí.
