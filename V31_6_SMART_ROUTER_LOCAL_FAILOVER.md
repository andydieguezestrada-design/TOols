# TOols V31.6 — Smart Router + Local Failover

## Objetivo
Evitar que una cuota, rate limit, permiso temporal o fallo de red de un proveedor bloquee al agente completo.

## Comportamiento
1. TOols intenta los proveedores online habilitados por prioridad.
2. Si un proveedor devuelve 429/cuota, 403, 401, timeout o error de red, se registra el fallo y se aplica un cooldown temporal.
3. El router pasa automáticamente al siguiente proveedor disponible.
4. Cuando termina el cooldown, el proveedor vuelve a estar disponible automáticamente.
5. Se añade un motor local OpenAI-compatible/Ollama como último respaldo.
6. El motor local no requiere API key ni consume cuota de un proveedor externo.

## Importante
El motor local requiere un servidor de inferencia local o accesible por red. Esta APK no puede convertir una API externa limitada en ilimitada ni intenta evadir límites mediante rotación de cuentas o claves.

## UI
La pantalla de Proveedores muestra el estado de cada motor y permite configurar el endpoint/modelo local.
