# Proveedores de IA gratuitos de TOols

TOols está configurado únicamente con proveedores que ofrecen un nivel de servicio/API gratuito. El uso gratuito está sujeto a los límites y cuotas que establezca cada proveedor.

## Prioridad de fallback

| Prioridad | Proveedor | Modelo predeterminado | Endpoint |
|---:|---|---|---|
| 0 | Google Gemini | `gemini-3.6-flash` | Gemini API |
| 1 | Groq | `openai/gpt-oss-120b` | Groq OpenAI-compatible API |
| 2 | Mistral | `mistral-small-latest` | Mistral API |

## Google Gemini

Gemini 3.6 Flash es el proveedor principal. Google ofrece un Free Tier para la API; los límites dependen del modelo y del uso.

## Groq

Groq ofrece acceso para desarrolladores con límites gratuitos. TOols usa su API compatible con OpenAI.

Endpoint:
`https://api.groq.com/openai/v1/chat/completions`

Modelo predeterminado:
`openai/gpt-oss-120b`

## Mistral

Mistral Studio dispone de Free mode sin tarjeta para el acceso incluido y con límites de uso. TOols usa la API de chat de Mistral.

Endpoint:
`https://api.mistral.ai/v1/chat/completions`

Modelo predeterminado:
`mistral-small-latest`

## Fallback automático y diagnóstico

TOols intenta los proveedores en orden de prioridad. Si un proveedor falla, continúa con el siguiente configurado y muestra en pantalla el proveedor, etapa y causa resumida del fallo.

Para Groq, si `openai/gpt-oss-120b` devuelve HTTP 403 por restricciones del modelo/proyecto, TOols realiza un segundo intento temporal con `openai/gpt-oss-20b` sin modificar la configuración guardada. Si también falla, continúa con el siguiente proveedor.

Los errores 401, 403, 429, DNS y timeout se clasifican para evitar mensajes genéricos como “check your network settings” cuando el problema real es una clave, cuota o permiso de modelo.

## Seguridad

- Las API keys se almacenan mediante `EncryptedSharedPreferences` cuando está disponible.
- Las claves no se muestran después de guardarlas en el formulario.
- No se incluyen API keys en el repositorio.

## Proveedores eliminados

Se eliminaron de la configuración activa de TOols los proveedores de pago: DeepSeek, MiniMax, OpenAI y Anthropic. También se eliminó el proveedor genérico OpenAI-compatible para evitar configurar accidentalmente un servicio de pago.

## TOols Device Agent

TOols 2.7 adds a device-level file agent. When the user explicitly grants Android's **All files access**, the AI can list, search, read, create folders, write files, rename, copy, move and delete files under shared storage. Destructive operations are shown for explicit approval before execution.

The agent uses structured `TOOL_CALL` actions rather than blindly executing arbitrary shell text. Paths are canonicalized under `/storage/emulated/0` and attempts to escape that root are rejected. Android still protects restricted areas such as `Android/data` and `Android/obb`.
