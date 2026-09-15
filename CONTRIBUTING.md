# Contributing to TOols

¡Gracias por tu interés en contribuir a TOols! Este documento proporciona pautas y instrucciones para colaboradores.

## Empezar

### Prerrequisitos
- JDK 17 o superior
- Android SDK 31+
- Git
- Android Studio (recomendado)

### Configuración Local

```bash
# Clonar repositorio
git clone https://github.com/yourusername/TOols.git
cd TOols

# Construir proyecto
./gradlew build

# Abrir en Android Studio
# File > Open > TOols
```

## Proceso de Contribución

### 1. Crear Issue
Antes de empezar, abre un issue describiendo:
- Qué feature quieres agregar
- Qué bug encontraste
- Contexto y ejemplos

### 2. Fork y Branch
```bash
# Fork en GitHub (button arriba a la derecha)
git checkout -b feature/TuFeature
```

### 3. Desarrollo
- Sigue las convenciones de código
- Escribe código documentado (KDoc)
- Agrega tests cuando sea posible
- Mantén commits limpios y descriptivos

### 4. Tests
```bash
# Ejecutar unit tests
./gradlew testDebugUnitTest

# Ejecutar lint
./gradlew lint

# Build completo
./gradlew build
```

### 5. Commit y Push
```bash
git add .
git commit -m "feat: Add amazing feature (#123)"
git push origin feature/TuFeature
```

### 6. Pull Request
- Describe los cambios claramente
- Referencia el issue relacionado (#123)
- Asegúrate que CI/CD pasa

## Convenciones de Código

### Kotlin
- Sigue [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
- Use nombres descriptivos
- Evita variables single-letter excepto en loops

```kotlin
// Bien ✅
fun createProjectWithValidation(name: String): Result<Project> {
    /**
     * Crear proyecto con validación completa.
     */
}

// Mal ❌
fun createP(n: String): R<P> {
    // Sin documentación
}
```

### Documentación
- Usa KDoc para clases y funciones públicas
- Explica el "por qué", no solo el "qué"

```kotlin
/**
 * Manejador robusto de errores con circuit breaker.
 * 
 * Previene fallos en cascada cuando APIs externas están caídas.
 * Implementa estados CLOSED/OPEN/HALF_OPEN.
 * 
 * @param failureThreshold Número de fallos antes de abrir el circuito
 * @param timeoutMs Tiempo en millisegundos antes de intentar recovery
 */
class CircuitBreaker(failureThreshold: Int, timeoutMs: Long) {
    // ...
}
```

### Testing
- Escribe tests para lógica de negocio
- Usa Mockito para dependencias
- Apunta al 80%+ de coverage

```kotlin
@Test
fun `createProject with valid input should succeed`() {
    // Arrange
    val useCase = ProjectUseCases(mockRepository)
    
    // Act
    val result = useCase.createProject("Test", null, "KOTLIN", "/path")
    
    // Assert
    assertTrue(result.isSuccess)
}
```

## Áreas de Contribución

### Fácil (Good First Issue)
- [ ] Traducción a otros idiomas
- [ ] Mejorar documentación
- [ ] Agregar comentarios de código
- [ ] Fixing typos

### Moderado
- [ ] Agregar tests unitarios
- [ ] Mejorar UI/UX
- [ ] Optimizaciones de rendimiento
- [ ] Soporte para más lenguajes de programación

### Difícil
- [ ] Implementar editor de código
- [ ] Chat IA multiproveedor
- [ ] Terminal integrada
- [ ] Integración GitHub completa

## Code Review

### Qué buscamos
✅ Código limpio y legible  
✅ Tests que pasen  
✅ Documentación adecuada  
✅ Sin breaking changes  
✅ Seguimiento de convenciones  

### Feedback constructivo
Buscamos ayudarte a mejorar. Si te pedimos cambios:
- No es personal
- Estamos cuidando la calidad
- Explica qué mejorar y por qué

## Reporting Bugs

Incluye:
```
- Versión de Android
- Device/Emulator
- Pasos para reproducir
- Comportamiento esperado
- Logs/stacktrace
```

## Preguntas?

- Abre un GitHub Discussion
- Lee README.md y QUICK_START.md
- Revisa issues anteriores

## Licencia

Al contribuir, aceptas que tu código se publique bajo Apache 2.0.

---

¡Gracias por hacer TOols mejor! 🙌
