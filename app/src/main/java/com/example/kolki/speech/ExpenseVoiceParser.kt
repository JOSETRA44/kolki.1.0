package com.example.kolki.speech

import com.example.kolki.data.SimpleExpense
import java.util.regex.Pattern

class ExpenseVoiceParser {
    
    companion object {
        // Patrones para reconocer números en español
        private val numberWords = mapOf(
            "cero" to 0, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
            "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
            "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
            "quince" to 15, "dieciséis" to 16, "diecisiete" to 17, "dieciocho" to 18,
            "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintidós" to 22,
            "veintitrés" to 23, "veinticuatro" to 24, "veinticinco" to 25,
            "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
            "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
            "cien" to 100, "ciento" to 100
        )
        
        // Categorías comunes
        private val commonCategories = mapOf(
            "comida" to "Alimentación",
            "almuerzo" to "Alimentación",
            "desayuno" to "Alimentación",
            "cena" to "Alimentación",
            "comidas" to "Alimentación",
            "alimentos" to "Alimentación",
            "restaurante" to "Alimentación",
            "menú" to "Alimentación",
            "menu" to "Alimentación",
            "cafetería" to "Alimentación",
            "café" to "Alimentación",
            "cafe" to "Alimentación",
            "super" to "Alimentación",
            "bodega" to "Alimentación",
            "mercado" to "Alimentación",

            "transporte" to "Transporte",
            "taxi" to "Transporte",
            "bus" to "Transporte",
            "metro" to "Transporte",
            "gasolina" to "Transporte",
            "combustible" to "Transporte",
            "uber" to "Transporte",
            "colectivo" to "Transporte",
            "micro" to "Transporte",
            "combi" to "Transporte",
            "moto" to "Transporte",
            "estacionamiento" to "Transporte",
            "parking" to "Transporte",
            "peaje" to "Transporte",

            "entretenimiento" to "Entretenimiento",
            "cine" to "Entretenimiento",
            "juegos" to "Entretenimiento",
            "diversión" to "Entretenimiento",
            "ocio" to "Entretenimiento",
            "netflix" to "Entretenimiento",
            "spotify" to "Entretenimiento",
            "música" to "Entretenimiento",
            "musica" to "Entretenimiento",
            "concierto" to "Entretenimiento",
            "teatro" to "Entretenimiento",
            "bar" to "Entretenimiento",

            "salud" to "Salud",
            "medicina" to "Salud",
            "doctor" to "Salud",
            "médico" to "Salud",
            "farmacia" to "Salud",
            "hospital" to "Salud",
            "dentista" to "Salud",
            "odontólogo" to "Salud",
            "clinica" to "Salud",
            "clínica" to "Salud",

            "compras" to "Compras",
            "ropa" to "Compras",
            "zapatos" to "Compras",
            "supermercado" to "Compras",
            "ferretería" to "Compras",
            "ferreteria" to "Compras",
            "electrónica" to "Compras",
            "electronica" to "Compras",
            "tienda" to "Compras",

            "servicios" to "Servicios",
            "luz" to "Servicios",
            "agua" to "Servicios",
            "internet" to "Servicios",
            "teléfono" to "Servicios",
            "celular" to "Servicios",
            "telefonía" to "Servicios",
            "telefonia" to "Servicios",
            "cable" to "Servicios",
            "gas" to "Servicios",
            "plan" to "Servicios",

            "educación" to "Educación",
            "libros" to "Educación",
            "curso" to "Educación",
            "universidad" to "Educación",
            "colegio" to "Educación",
            "escuela" to "Educación",
            "taller" to "Educación",
            "matrícula" to "Educación",
            "matricula" to "Educación",

            "vivienda" to "Vivienda",
            "alquiler" to "Vivienda",
            "renta" to "Vivienda",
            "arriendo" to "Vivienda",

            "hogar" to "Hogar",
            "limpieza" to "Hogar",
            "cocina" to "Hogar",

            "otros" to "Otros"
        )
    }
    
    data class ParseResult(
        val success: Boolean,
        val expense: SimpleExpense? = null,
        val error: String? = null
    )
    
    fun parseExpenseFromVoice(voiceText: String): SimpleExpense? {
        try {
            val cleanText = voiceText.lowercase().trim()
            
            // 1) Intento 1: patrón separado por comas: "categoría, monto, comentario(opcional)"
            val parts = cleanText.split(",").map { it.trim() }
            if (parts.size >= 2) {
                val categoryPart = parts[0].trim()
                val category = normalizeCategory(categoryPart)
                val amountPart = parts[1].trim()
                val amount = extractAmount(amountPart)
                if (amount != null) {
                    val comment = if (parts.size > 2) parts.drop(2).joinToString(", ").trim() else null
                    return SimpleExpense(
                        category = category,
                        amount = amount,
                        comment = comment
                    )
                }
                // si no encontró monto, continuamos al fallback
            }

            // 2) Regla simple sin comas: primer token = categoría, segundo token = monto, resto = comentario (opcional)
            //    Ej: "comida 25 menu del dia"
            val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 2) {
                val categorySimple = normalizeCategory(words[0])
                val amountFromSecond = extractAmount(words[1]
                    .replace("soles", "")
                    .replace("s/", "")
                    .replace("$", "")
                )
                if (amountFromSecond != null) {
                    val commentSimple = if (words.size > 2) words.drop(2).joinToString(" ").trim().ifBlank { null } else null
                    return SimpleExpense(
                        category = categorySimple,
                        amount = amountFromSecond,
                        comment = commentSimple
                    )
                }
                // si no pudimos extraer monto del segundo token, seguimos al fallback general
            }

            // 3) Fallback general: texto sin comas. Buscar el primer número como monto.
            //    Ejemplos: "comida 25 menu del dia", "supermercado 45", "taxi 12.50 aeropuerto"
            if (words.isEmpty()) return null

            // Encontrar índice del primer token que contenga un número (o que combine a número por extractAmount)
            var amountIndex = -1
            var foundAmount: Double? = null
            for (i in words.indices) {
                val candidate = words[i]
                val amount = extractAmount(candidate)
                    ?: extractAmount(candidate.replace("soles", "").replace("s/", "").replace("$", ""))
                if (amount != null) {
                    amountIndex = i
                    foundAmount = amount
                    break
                }
            }

            if (amountIndex == -1 || foundAmount == null) {
                // Intento adicional: extraer monto de toda la cadena si viene pegado a palabras
                foundAmount = extractAmount(cleanText)
                if (foundAmount == null) return null
                // Si no tenemos posición, supondremos categoría como primera palabra
                amountIndex = words.indexOfFirst { it.contains(Regex("\\d")) }.let { if (it >= 0) it else 1 }
            }

            val before = if (amountIndex > 0) words.subList(0, amountIndex).joinToString(" ") else ""
            val after = if (amountIndex + 1 < words.size) words.subList(amountIndex + 1, words.size).joinToString(" ") else ""

            val category = normalizeCategory(before.ifBlank { "Otros" })
            val comment = after.ifBlank { null }

            return SimpleExpense(
                category = category,
                amount = foundAmount,
                comment = comment
            )
            
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun normalizeCategory(categoryText: String): String {
        val normalized = categoryText.lowercase().trim()
        
        // Buscar en categorías comunes
        commonCategories.forEach { (key, value) ->
            if (normalized.contains(key)) {
                return value
            }
        }
        
        // Si no se encuentra, capitalizar la primera letra
        return categoryText.replaceFirstChar { it.uppercase() }
    }
    
    private fun extractAmount(amountText: String): Double? {
        var normalized = amountText.lowercase().trim()

        // Quitar prefijos/sufijos de moneda comunes y variantes (s/. , s/ , $ , soles , sol)
        normalized = normalized
            .replace(Regex("(?i)s\\s*/\\.?") , " ")
            .replace(Regex("(?i)s\\s*/") , " ")
            .replace(Regex("\\$"), " ")
            .replace(Regex("(?i)\\$"), " ")
            .replace(Regex("(?i)soles?"), " ")
            .replace(Regex("(?i)pen"), " ")
            .trim()

        // Patrón tipo "12 con 50"
        val conPattern = Pattern.compile("(\\d+)\\s+con\\s+(\\d{1,2})")
        val conMatcher = conPattern.matcher(normalized)
        if (conMatcher.find()) {
            val entero = conMatcher.group(1).toDoubleOrNull()
            val cent = conMatcher.group(2).toIntOrNull()
            if (entero != null && cent != null) {
                return entero + (cent / 100.0)
            }
        }

        // Buscar números con separadores de miles y decimales (coma o punto)
        // Capturar el primer número plausible
        val numPattern = Pattern.compile("(?<![a-z0-9])([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})|[0-9]+(?:[.,][0-9]{1,2}))(?![a-z0-9])")
        val numMatcher = numPattern.matcher(normalized)
        if (numMatcher.find()) {
            var candidate = numMatcher.group(1)
            // Normalizar: si hay ambos separadores, asumir último como decimal
            if (candidate.contains('.') && candidate.contains(',')) {
                // Si formato es 1.234,56 => quitar puntos miles y cambiar coma por punto
                candidate = candidate.replace(".", "").replace(",", ".")
            } else {
                // Si solo hay comas, tratar como decimal
                if (candidate.contains(',')) candidate = candidate.replace(",", ".")
            }
            return candidate.toDoubleOrNull()
        }

        // Buscar números enteros simples
        val intPattern = Pattern.compile("(\\d+)")
        val intMatcher = intPattern.matcher(normalized)
        if (intMatcher.find()) {
            return intMatcher.group(1).toDoubleOrNull()
        }

        // Buscar números en palabras básicas
        var total = 0.0
        val words = normalized.split(" ")
        for (word in words) {
            val cleanWord = word.replace(Regex("[^a-záéíóúñü]"), "")
            numberWords[cleanWord]?.let { value ->
                total += value
            }
        }
        return if (total > 0) total else null
    }
}
