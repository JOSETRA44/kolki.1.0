# 💰 Kolki - Control de Gastos con Voz

Una aplicación Android moderna para el control de gastos personales con reconocimiento de voz offline usando Vosk.

## 🚀 Características Principales

### 📱 Funcionalidades Core
- **Registro rápido por voz**: Doble toque en botones de volumen para activar
- **Registro manual**: Formulario intuitivo con categorías predefinidas
- **Base de datos local**: Almacenamiento offline con SQLite/Room
- **Navegación por pestañas**: Gastos, Estadísticas y Perfiles 

### 🎤 Reconocimiento de Voz
- **Offline**: Usa la librería Vosk para reconocimiento sin internet
- **Formato natural**: "Comida, 25, almuerzo en la universidad"
- **Categorías inteligentes**: Reconoce categorías comunes automáticamente
- **Números en español**: Convierte palabras a números

### 📊 Estadísticas y Visualización
- **Totales por período**: Semanal, mensual, anual
- **Gastos por categoría**: Con barras de progreso
- **Gastos recientes**: Lista de últimos movimientos
- **Filtros y búsqueda**: Para encontrar gastos específicos

## 🏗️ Arquitectura Técnica

### 🛠️ Stack Tecnológico
- **Lenguaje**: Kotlin
- **UI**: Material Design 3 + View Binding
- **Base de datos**: Room (SQLite)
- **Arquitectura**: MVVM + Repository Pattern
- **Navegación**: Navigation Component
- **Reconocimiento de voz**: Vosk Android
- **Concurrencia**: Kotlin Coroutines

### 📁 Estructura del Proyecto
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
│   └── profile/            # Configuración
├── speech/                 # Reconocimiento de voz
│   ├── VoiceRecognitionService.kt
│   └── ExpenseVoiceParser.kt
└── service/                # Servicios de fondo
    └── VolumeKeyService.kt
```

## 🎯 Uso de la Aplicación

### 📝 Registro Manual
1. Abrir la app en la pestaña "Gastos"
2. Completar el formulario:
   - **Categoría**: Seleccionar o escribir nueva
   - **Monto**: Cantidad en soles
   - **Comentario**: Descripción opcional
3. Presionar "Guardar"

### 🎤 Registro por Voz
**Método 1: Desde la app**
1. Presionar botón "Por Voz"
2. Dictar en formato: "categoría, monto, comentario"
3. Ejemplo: "Comida, veinticinco, almuerzo en la universidad"

**Método 2: Acceso rápido**
1. Doble toque rápido en botones de volumen
2. Dictar el gasto cuando aparezca la interfaz
3. Se guarda automáticamente

### 📊 Ver Estadísticas
- **Totales**: Visualizar gasto total y mensual
- **Períodos**: Cambiar entre semana, mes, año
- **Categorías**: Ver distribución por categorías
- **Recientes**: Lista de últimos gastos

### ⚙️ Configuración
- **Reconocimiento de voz**: Activar/desactivar
- **Moneda**: Cambiar entre Soles, Dólares, Euros
- **Exportar datos**: Generar archivo CSV
- **Limpiar datos**: Eliminar todos los gastos

## 🔧 Configuración de Desarrollo

### 📋 Requisitos
- Android Studio Arctic Fox o superior
- SDK mínimo: API 26 (Android 8.0)
- SDK objetivo: API 36 (Android 15)
- Kotlin 2.0.21

### 🚀 Instalación
1. Clonar el repositorio
2. Abrir en Android Studio
3. Sync del proyecto con Gradle
4. Ejecutar en dispositivo/emulador

### 🎤 Configuración de Vosk
La app descarga automáticamente el modelo de voz en español la primera vez que se usa. Requiere:
- Conexión a internet (solo primera vez)
- Espacio de almacenamiento (~50MB para el modelo)
- Permisos de micrófono

## 🔒 Permisos Requeridos

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 🎨 Características de UX

### 🎯 Diseño Intuitivo
- **Material Design 3**: Interfaz moderna y familiar
- **Navegación clara**: Bottom navigation con iconos descriptivos
- **Feedback visual**: Animaciones y estados de carga
- **Accesibilidad**: Soporte para lectores de pantalla

### 🚀 Rendimiento
- **Base de datos local**: Acceso instantáneo sin internet
- **Reconocimiento offline**: No requiere conexión después de la configuración inicial
- **Arquitectura eficiente**: MVVM con LiveData para actualizaciones reactivas

## 🔮 Funcionalidades Futuras

- [ ] Sincronización en la nube
- [ ] Presupuestos y metas de gasto
- [ ] Gráficos avanzados y reportes
- [ ] Recordatorios de gastos
- [ ] Múltiples cuentas/carteras
- [ ] Exportación a diferentes formatos
- [ ] Modo oscuro
- [ ] Widget para pantalla de inicio

## 🐛 Solución de Problemas

### Reconocimiento de Voz No Funciona
- Verificar permisos de micrófono
- Comprobar que el modelo Vosk se descargó correctamente
- Reiniciar la aplicación

### Base de Datos Corrupta
- Usar la opción "Limpiar Datos" en Perfil
- Reinstalar la aplicación si persiste el problema

### Rendimiento Lento
- Limpiar datos antiguos
- Verificar espacio de almacenamiento disponible

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

---

**Desarrollado con ❤️ usando Android Studio y Kotlin**
