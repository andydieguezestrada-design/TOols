# TOols V31.5 — Agent Speed + Connectivity

## Cambios

- Reutilización de conexiones HTTP y timeouts más ágiles.
- El agente informa en tiempo real qué está haciendo: preparación, razonamiento, conexión con proveedor, análisis de respuesta, ejecución de herramientas y finalización.
- El chatbot precarga el contexto del proyecto en segundo plano para evitar bloquear el envío.
- El contexto de proyecto se prepara fuera del hilo de UI.
- Las respuestas de proveedores se limitan a 4096 tokens para reducir latencia en tareas normales.
- Fallback automático conserva el orden configurado.
- Errores 401/403/429, DNS y timeout dejan de mostrarse como un genérico “check your network settings”.
- Groq: si `openai/gpt-oss-120b` devuelve 403, se intenta temporalmente `openai/gpt-oss-20b` sin cambiar la configuración guardada.
- El resultado final muestra rondas, herramientas procesadas y tiempo total.

## Nota sobre HTTP 403 de Groq

Un HTTP 403 en Groq no necesariamente significa que el teléfono no tenga Internet. Groq documenta que un modelo restringido por permisos de organización/proyecto devuelve 403. TOols ahora identifica ese caso y continúa con fallback.

## Validación

El proyecto conserva la arquitectura existente y no reemplaza el Core del agente. La compilación local no pudo ejecutarse en este entorno porque el ZIP original no incluye `gradle/wrapper/gradle-wrapper.jar` y no hay una instalación global de Gradle disponible. Se recomienda ejecutar el workflow de GitHub Actions del proyecto para la validación Android completa.
