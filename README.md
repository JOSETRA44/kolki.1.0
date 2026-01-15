# Kolki - Control de Gastos con Voz

Aplicación Android para control de gastos personales con registro por voz y visualizaciones útiles.

## Características Principales

### Funcionalidades Core
- **Registro rápido por voz**: Doble toque en botones de volumen para activar
- **Registro manual**: Formulario intuitivo con categorías predefinidas
- **Base de datos local**: Almacenamiento offline con SQLite/Room
- **Navegación por pestañas**: Gastos, Estadísticas y Perfiles

### Reconocimiento de Voz
- **Sistema Android**: Usa `SpeechRecognizer` con `RecognitionListener`
- **Formato natural**: "Comida, 25, almuerzo en la universidad"
- **Categorías inteligentes**: Reconoce categorías comunes automáticamente
- **Números en español**: Convierte palabras a números
- **Estabilidad mejorada**: Limpieza robusta y prevención de sesiones concurrentes del micrófono

### Estadísticas y Visualización
- **Totales por período**: Semanal, mensual, anual y rangos personalizados
- **Distribución por categoría**: Gráfico circular con desglose y drilldown
- **Barras semanales**: Comparativa por días de la semana
- **Top tarjetas unificadas**: Dos tarjetas superiores unificadas visualmente con un divider fino, manteniendo zonas táctiles independientes:
  - Superior (toggle): "Este Mes (gasto)" / "Saldo Restante" con barra segmentada Rojo/Verde.
  - Inferior (toggle): "Presupuesto (Hoy)" / "Presupuesto (Mes)" con barras horizontales (diaria o mensual) Rojo/Verde.
- **Gastos recientes**: Lista de últimos movimientos

## Arquitectura Técnica

### Stack Tecnológico
- **Lenguaje**: Kotlin
- **UI**: Material Design 3 + View Binding
- **Base de datos**: Room (SQLite)
- **Arquitectura**: MVVM + Repository Pattern
- **Navegación**: Navigation Component
- **Reconocimiento de voz**: Android `SpeechRecognizer`
- **Concurrencia**: Kotlin Coroutines

### Estructura del Proyecto (simplificada)
```
app/src/main/java/com/example/kolki/
├── data/                    # Modelos y base de datos
│   ├── Expense.kt          # Entidad principal
│   ├── ExpenseDao.kt       # Acceso a datos
│   ├── ExpenseDatabase.kt  # Configuración Room
│   └── Converters.kt       # Convertidores de tipos
├── repository/             # Capa de repositorio
│   └── ExpenseRepository.kt
├── ui/                     # Interfaz de usuario
│   ├── expenses/           # Pantalla principal
│   ├── statistics/         # Estadísticas
│   ├── add/                # Agregar gasto (con voz)
│   ├── quick/              # Overlay rápido por voz
│   └── profile/            # Perfil
├── speech/                 # Reconocimiento de voz
│   ├── SimpleSpeechRecognizer.kt
│   └── ExpenseVoiceParser.kt
└── service/                # Servicios de fondo
    ├── VolumeKeyService.kt
    └── RecognizerService.kt
```

## Uso de la Aplicación

### Registro Manual
1. Abrir la app en la pestaña "Gastos"
2. Completar el formulario:
   - **Categoría**: Seleccionar o escribir nueva
   - **Monto**: Cantidad en soles
   - **Comentario**: Descripción opcional
3. Presionar "Guardar"

### Registro por Voz
**Método 1: Desde la app**
1. Presionar botón "Por Voz"
2. Dictar en formato: "categoría, monto, comentario"
3. Ejemplo: "Comida, veinticinco, almuerzo en la universidad"

**Método 2: Acceso rápido**
1. Doble toque rápido en botones de volumen
2. Dictar el gasto cuando aparezca la interfaz
3. Se guarda automáticamente

### Ver Estadísticas
- **Totales y saldo**: Toggle en la tarjeta superior
- **Períodos**: Semana, mes y rango
- **Presupuesto**: Toggle diario/mensual con barras Rojo/Verde
- **Categorías**: Distribución con drilldown
- **Recientes**: Últimos gastos

### Configuración
- **Reconocimiento de voz**: Preferencias de idioma, offline, auto-guardado y alertas de presupuesto
- **Moneda**: Símbolo de moneda
- **Presupuesto**: Modo (diario, fin de mes, personalizado) y montos
- **Notificaciones**: Sonido de alerta

## Configuración de Desarrollo

### Requisitos
- Android Studio Arctic Fox o superior
- SDK mínimo: API 26 (Android 8.0)
- SDK objetivo: API 36 (Android 15)
- Kotlin 2.0.21

### Instalación
1. Clonar el repositorio
2. Abrir en Android Studio
3. Sync del proyecto con Gradle
4. Ejecutar en dispositivo/emulador

### Notas sobre reconocimiento de voz
- Usa el motor de voz del sistema.
- Requiere permisos de micrófono.
- El overlay rápido despierta la pantalla brevemente para dictar.

## Permisos Requeridos

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## Características de UX

### Diseño Intuitivo
- **Material Design 3**: Interfaz moderna y familiar
- **Navegación clara**: Bottom navigation con iconos descriptivos
- **Feedback visual**: Animaciones y estados de carga
- **Accesibilidad**: Soporte para lectores de pantalla

### Rendimiento
- **Base de datos local**: Acceso instantáneo sin internet
- **Reconocimiento offline**: No requiere conexión después de la configuración inicial
- **Arquitectura eficiente**: MVVM con LiveData para actualizaciones reactivas

## Funcionalidades Futuras

- [ ] Sincronización en la nube
- [ ] Presupuestos y metas de gasto
- [ ] Gráficos avanzados y reportes
- [ ] Recordatorios de gastos
- [ ] Múltiples cuentas/carteras
- [ ] Exportación a diferentes formatos
- [ ] Modo oscuro
- [ ] Widget para pantalla de inicio

## Solución de Problemas

### Reconocimiento de Voz No Funciona
- Verificar permisos de micrófono
- Asegurar que no haya otra app usando el micrófono
- Si falla tras uso prolongado: cerrar la pantalla de voz; el app limpia el motor y reintenta con backoff
- Reiniciar la aplicación si persiste

### Base de Datos Corrupta
- Usar la opción "Limpiar Datos" en Perfil
- Reinstalar la aplicación si persiste el problema

### Rendimiento Lento
- Limpiar datos antiguos
- Verificar espacio de almacenamiento disponible

## Cambios recientes destacados
- **Estabilidad de voz**: Bloqueo de sesiones concurrentes y destrucción segura del `SpeechRecognizer` en `VolumeKeyService` y `SimpleSpeechRecognizer`.
- **Backoff en reintentos**: Pequeña espera antes de recrear el reconocedor tras errores.
- **UI de Estadísticas**: Tarjetas superiores unificadas visualmente con barras segmentadas rojo/verde (mensual/saldo y diario/mensual).
- **Navegación**: Optimización de backstack y retorno al root por pestaña.

## Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

---

**Desarrollado usando Android Studio y Kotlin**
