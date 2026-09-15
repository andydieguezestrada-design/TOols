# TOols V31.8 — Persistent Memory + CI/CA + Hybrid RAG + Local Coding Engine

## Objetivo
Esta versión convierte el agente en un sistema de continuidad de proyecto: al cerrar la APK y volver días después, TOols conserva checkpoints, decisiones, bloqueos, archivos tocados, próximos pasos e historial reciente.

## Memoria persistente
- Checkpoint por proyecto y memoria global.
- Última solicitud, respuesta, ronda y etapa.
- Resultados de herramientas y aprobaciones pendientes.
- Plan de cambios.
- Archivos tocados, decisiones, siguientes acciones y bloqueos.
- Escritura atómica para evitar corrupción si la app se cierra durante el guardado.
- La memoria nunca sustituye la inspección del estado real del proyecto.

## CI — Context Intelligence
- Fusiona memoria + estructura del proyecto + estadísticas de código + dependencias + RAG + estado de proveedores.
- El agente recibe contexto relevante antes de comenzar y se actualiza después de cada ronda.
- Añade herramientas explícitas `project_analyze`, `rag_search` y `rag_reindex`.

## CA — Context Awareness
- Detecta estado actual de archivos y proyecto antes de actuar.
- Conserva hashes del contenido indexado.
- Señala hotspots de complejidad, lenguajes, líneas y relaciones internas.
- Prioriza evidencia actual sobre memoria histórica.

## RAG híbrido
- Índice local persistente por proyecto.
- Chunking con solapamiento para Kotlin/Java/Gradle/Python/texto.
- Recuperación léxica por términos, rutas y símbolos.
- Embeddings opcionales mediante el motor local.
- Fusión de puntuación léxica + similitud coseno.
- Reindexación incremental por SHA-256: no vuelve a procesar archivos sin cambios.
- Conserva metadata de archivo, líneas, lenguaje y símbolos.

## Análisis de archivos/código
El agente dispone de árbol de carpetas, búsqueda por nombre, búsqueda por contenido, inspección de metadata/hash, lectura y análisis profundo del proyecto. Las operaciones destructivas siguen requiriendo aprobación.

## Motor local
- Controlador de salud y descubrimiento de modelos.
- Perfil configurable: endpoint, modelo, contexto y embeddings.
- Compatible con backend OpenAI-compatible como llama.cpp y con Ollama para chat/embeddings.
- Perfil de embeddings separado (`embeddinggemma` por defecto para Ollama).
- Se utiliza como último proveedor del router, no como mecanismo para evadir cuotas externas.

## Nota de arquitectura
La APK no incorpora mágicamente un LLM de varios GB. El motor local necesita un runtime compatible (por ejemplo llama.cpp/Ollama/servidor local) y un modelo instalado/accesible. La arquitectura queda preparada para ese backend.

## Build
La versión es `3.1.8` / versionCode `39`. El proyecto de esta entrega sigue sin `gradle-wrapper.jar`, por lo que no se afirma que el APK haya sido compilado localmente. Debe compilarse mediante GitHub Actions o restaurando el wrapper JAR.
