# TOols V31.7 — Memoria persistente del agente

## Objetivo
TOols conserva un checkpoint persistente por proyecto y otro para el modo global. Al volver a abrir el proyecto, el agente recupera la última tarea, respuesta, etapa, ronda, resultados, plan, aprobaciones pendientes y nombres de adjuntos.

## Comportamiento
- La memoria se guarda en el almacenamiento interno privado de la aplicación, en `agent_memory/`.
- Se actualiza al iniciar una tarea, después de cada ronda y al finalizar.
- Conserva las últimas 8 interacciones, con límites de tamaño para evitar crecimiento indefinido.
- No copia el contenido binario de los adjuntos; solo guarda sus nombres.
- El agente recibe la memoria como contexto y tiene la instrucción de verificar siempre el estado real del proyecto antes de continuar.
- La interfaz muestra cuando se recuperó una memoria previa.
- Si el proyecto se elimina, su memoria no se mezcla con otros proyectos porque la clave es el `projectId`.
- El modo sin proyecto usa una memoria global independiente.

## Seguridad / límites
La memoria es un checkpoint de continuidad, no una autoridad sobre el estado del proyecto. Los archivos reales siempre tienen prioridad y deben inspeccionarse antes de modificar o afirmar que algo sigue hecho.
