package com.example.qrtocsv

import android.content.Context
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class CSVManager(private val context: Context) {

    private val baseFileName = "GREIF"
    private val separator = ";" // Разделитель для CSV
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    // Заголовки столбцов
    private val headers = listOf(
        "Дата сканирования",
        "Дата отгрузки",
        "Описание товара",
        "Код изделия",
        "Номер плавки",
        "ID рулона",
        "Толщина (мм)",
        "Ширина (мм)",
        "Длина (м)",
        "Марка стали",
        "Стандарт",
        "Масса нетто (кг)",
        "Масса брутто (кг)",
        "ИДН",
        "Цех отправитель",
        "Бригада",
        "Грузополучатель"
    )

    // Основной файл CSV
    private var csvFile: File? = null

    fun getCSVFilePath(): String {
        ensureFileExists()
        return csvFile?.absolutePath ?: ""
    }

    /**
     * Проверяет, есть ли уже такая запись в CSV
     */
    fun isDuplicate(qrData: String): Boolean {
        return try {
            ensureFileExists()
            val existingRecords = readAllRecords()
            val parsedData = parseQRData(qrData)

            // Ищем совпадения по основным полям
            for (record in existingRecords) {
                if (isSameRecord(record, parsedData)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Сравнивает две записи на идентичность
     */
    private fun isSameRecord(record: Map<String, String>, newData: Map<String, String>): Boolean {
        // Сравниваем по ключевым полям
        val keyFields = listOf("Код изделия", "Номер плавки", "Дата отгрузки", "Описание товара")

        for (field in keyFields) {
            val existingValue = record[field] ?: ""
            val newValue = newData[field] ?: ""

            // Если оба значения не пустые и не совпадают - это разные записи
            if (existingValue.isNotBlank() && newValue.isNotBlank() &&
                existingValue.trim() != newValue.trim()) {
                return false
            }
        }

        // Если все ключевые поля совпадают или пустые, считаем это дубликатом
        return true
    }

    /**
     * Основной метод для сохранения данных QR-кода с проверкой дубликатов
     */
    fun saveQRCodeData(qrData: String): Boolean {
        return try {
            ensureFileExists()

            // Проверяем дубликат
            if (isDuplicate(qrData)) {
                return false // Запись уже существует
            }

            val timestamp = dateFormat.format(Date())
            val csvLine = buildCSVLine(timestamp, qrData)
            appendToFile(csvLine)

            // После записи переименовываем файл с текущей датой
            renameFileToCurrentDate()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun ensureFileExists() {
        try {
            // Ищем существующий файл GREIF_*.csv
            val downloadsDir = getDownloadsDir()
            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                val files = downloadsDir.listFiles { file ->
                    file.isFile && file.name.startsWith(baseFileName) && file.name.endsWith(".csv")
                }

                if (files != null && files.isNotEmpty()) {
                    // Находим самый новый файл по дате изменения
                    val latestFile = files.maxByOrNull { it.lastModified() }
                    csvFile = latestFile

                    // Переименовываем файл с текущей датой последнего изменения
                    if (csvFile != null) {
                        val newName = generateFileName()
                        val newFile = File(csvFile!!.parentFile, newName)
                        if (csvFile!!.renameTo(newFile)) {
                            csvFile = newFile
                        }
                    }
                }
            }

            // Если файл не найден, создаем новый
            if (csvFile == null) {
                val fileName = generateFileName()
                csvFile = File(getDownloadsDir(), fileName).apply {
                    parentFile?.mkdirs()
                    if (!exists()) {
                        createNewFile()
                        writeHeaders()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateFileName(): String {
        // Если файл существует, используем дату его последнего изменения
        // Иначе используем текущую дату
        val date = if (csvFile != null && csvFile!!.exists()) {
            Date(csvFile!!.lastModified())
        } else {
            Date()
        }
        val dateStr = fileDateFormat.format(date)
        return "${baseFileName}_$dateStr.csv"
    }

    private fun getDownloadsDir(): File {
        val dir = context.getExternalFilesDir(null)

        // Если dir null, используем внутреннее хранилище
        return dir ?: context.filesDir
    }

    private fun writeHeaders() {
        csvFile?.let { file ->
            FileOutputStream(file, false).use { fos ->
                // UTF-8 BOM для Excel
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                // Заголовки
                val headerLine = headers.joinToString(separator)
                fos.write("$headerLine\n".toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun renameFileToCurrentDate() {
        try {
            csvFile?.let { currentFile ->
                if (currentFile.exists()) {
                    val newName = generateFileName()
                    val newFile = File(currentFile.parentFile, newName)

                    // Переименовываем только если имя изменилось
                    if (currentFile.name != newName) {
                        if (currentFile.renameTo(newFile)) {
                            csvFile = newFile
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Строит строку CSV из данных
     */
    private fun buildCSVLine(timestamp: String, qrData: String): String {
        val values = mutableListOf<String>()

        // 1. Дата сканирования
        values.add(timestamp)

        // Парсим QR-данные в отдельные значения
        val parsedData = parseQRData(qrData)

        // Заполняем данные в ТОЧНОМ порядке заголовков
        values.add(parsedData.getOrElse("Дата отгрузки") { "" })
        values.add(parsedData.getOrElse("Описание товара") { "" })
        values.add(parsedData.getOrElse("Код изделия") { "" })
        values.add(parsedData.getOrElse("Номер плавки") { "" })
        values.add(parsedData.getOrElse("ID рулона") { "" })
        values.add(parsedData.getOrElse("Толщина (мм)") { "" })
        values.add(parsedData.getOrElse("Ширина (мм)") { "" })
        values.add(parsedData.getOrElse("Длина (м)") { "" })
        values.add(parsedData.getOrElse("Марка стали") { "" })
        values.add(parsedData.getOrElse("Стандарт") { "" })
        values.add(parsedData.getOrElse("Масса нетто (кг)") { "" })
        values.add(parsedData.getOrElse("Масса брутто (кг)") { "" })
        values.add(parsedData.getOrElse("ИДН") { "" })
        values.add(parsedData.getOrElse("Цех отправитель") { "" })
        values.add(parsedData.getOrElse("Бригада") { "" })
        values.add(parsedData.getOrElse("Грузополучатель") { "" })

        // Проверяем, что у нас ровно 17 значений (по количеству заголовков)
        while (values.size < 17) {
            values.add("")
        }

        return values.joinToString(separator) { escapeCSV(it) } + "\n"
    }

    /**
     * Парсит QR-данные в Map с ключами
     */
    private fun parseQRData(qrData: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = qrData.split("\n", "\r\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("http://") || it.startsWith("https://") }

        if (lines.isEmpty()) return result

        // Парсим первую строку как особый случай
        parseFirstLine(lines[0], result)

        // Парсим все строки, включая первую
        for (i in lines.indices) {
            parseLine(lines[i], result)
        }

        // Генерируем поле "ID рулона" на основе кода изделия и номера плавки
        generateLNField(result)
        return result
    }

    /**
     * Очищает номер плавки от всех букв, оставляя только цифры
     */
    private fun cleanMeltNumber(melt: String): String {
        return melt.replace("[^0-9]".toRegex(), "")
    }

    /**
     * Генерирует поле "ID рулона" на основе кода изделия и номера плавки
     */
    private fun generateLNField(result: MutableMap<String, String>) {
        val code = result["Код изделия"] ?: ""
        var melt = result["Номер плавки"] ?: ""

        // Очищаем номер плавки от букв
        melt = cleanMeltNumber(melt)
        // Сохраняем очищенное значение обратно в результат
        result["Номер плавки"] = melt

        if (code.isNotBlank() && melt.isNotBlank()) {
            // Формат: "360080-301592-001"
            // Убираем пробелы из кода изделия: "301 592 001" → "301592001"
            val cleanCode = code.replace(" ", "")
            val cleanMelt = melt.replace(" ", "")

            // Если код изделия имеет формат "301592001", берем первые 6 цифр и последние 3
            if (cleanCode.length == 9) {
                val secondPart = cleanCode.take(6) // "301592"
                val thirdPart = cleanCode.substring(6) // "001"
                result["ID рулона"] = "$cleanMelt-$secondPart-$thirdPart"
            } else if (cleanCode.length >= 6) {
                // Альтернативный формат: берем первые 6 символов
                val secondPart = cleanCode.take(6)
                val thirdPart = if (cleanCode.length > 6) cleanCode.substring(6) else ""
                result["ID рулона"] = if (thirdPart.isNotBlank()) {
                    "$cleanMelt-$secondPart-$thirdPart"
                } else {
                    "$cleanMelt-$secondPart"
                }
            } else {
                // Простой формат
                result["ID рулона"] = "$cleanMelt-$cleanCode"
            }
        } else {
            // Если не можем сгенерировать, оставляем пустым
            result["ID рулона"] = ""
        }
    }

    /**
     * Парсит строку QR-кода
     */
    private fun parseLine(line: String, result: MutableMap<String, String>) {
        // Обрабатываем первую строку (дата и описание)
        if (result.isEmpty() && line.contains(",")) {
            parseFirstLine(line, result)
            return
        }

        // Проверяем, что строка содержит двоеточие
        if (line.contains(":")) {
            val keyValue = line.split(":", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0].trim()
                val value = keyValue[1].trim()

                // Сопоставляем ключи с нашими заголовками
                when {
                    key.contains("Номер плавки", ignoreCase = true) -> {
                        val cleanValue = cleanMeltNumber(value)
                        result["Номер плавки"] = cleanValue
                    }
                    key.contains("Толщина", ignoreCase = true) ->
                        result["Толщина (мм)"] = value
                    key.contains("Ширина", ignoreCase = true) ->
                        result["Ширина (мм)"] = value
                    key.contains("Длина", ignoreCase = true) ->
                        result["Длина (м)"] = value
                    key.contains("Марка", ignoreCase = true) ->
                        result["Марка стали"] = value
                    key.contains("Стандарт", ignoreCase = true) ->
                        result["Стандарт"] = value
                    key.contains("Масса нетто", ignoreCase = true) ->
                        result["Масса нетто (кг)"] = value
                    key.contains("Масса брутто", ignoreCase = true) ->
                        result["Масса брутто (кг)"] = value
                    key.contains("ИДН", ignoreCase = true) ->
                        result["ИДН"] = value
                    key.contains("Цех отправитель", ignoreCase = true) ->
                        result["Цех отправитель"] = value
                    key.contains("Бригада", ignoreCase = true) ->
                        result["Бригада"] = value
                    key.contains("Грузополучатель", ignoreCase = true) ->
                        result["Грузополучатель"] = value
                    key.startsWith(":") ->
                        result["Код изделия"] = value
                    else -> {
                        if (line.startsWith(":")) {
                            result["Код изделия"] = extractValueAfterColon(line)
                        }
                    }
                }
            }
        } else if (line.startsWith(":")) {
            result["Код изделия"] = extractValueAfterColon(line)
        } else if (line.contains("Описание товара")) {
            // Может быть строка "Описание товара : Рулон х/к отож согласно GP-2001"
            val value = extractValueAfterColon(line)
            if (value.isNotBlank() && !result.containsKey("Описание товара")) {
                result["Описание товара"] = value
            }
        } else {
            // Простая строка без двоеточия
            if (line.matches(Regex(".*\\d+.*"))) {
                if (!result.containsKey("Код изделия") && line.contains(Regex("\\d{6}\\s+\\d{3}"))) {
                    result["Код изделия"] = line
                } else if (!result.containsKey("Номер плавки") && line.matches(Regex("[A-Za-z]\\d+"))) {
                    val cleanValue = cleanMeltNumber(line)
                    result["Номер плавки"] = cleanValue
                }
            }
        }
    }

    /**
     * Парсит первую строку: "2026-01-17 07:38:09,Описание товара : Рулон х/к отож согласно GP-2001"
     */
    private fun parseFirstLine(line: String, result: MutableMap<String, String>) {
        // Проверяем, есть ли запятая
        if (line.contains(",")) {
            val parts = line.split(",", limit = 2)
            if (parts.isNotEmpty()) {
                // Часть до запятой - дата отгрузки
                var date = parts[0].trim()
                date = date.replace("\"", "") // Убираем кавычки
                result["Дата отгрузки"] = date
            }
            if (parts.size >= 2) {
                // Часть после запятой - описание товара
                var description = parts[1].trim()
                description = extractValueAfterColon(description) // Убираем "Описание товара :"
                description = description.replace("\"", "") // Убираем кавычки
                result["Описание товара"] = description
            }
        } else {
            // Если нет запятой, пробуем найти дату
            val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                result["Дата отгрузки"] = dateMatch.value
            }
        }
    }

    /**
     * Извлекает значение после двоеточия
     */
    private fun extractValueAfterColon(line: String): String {
        return if (line.contains(":")) {
            line.split(":", limit = 2)
                .getOrNull(1)
                ?.trim()
                ?: line.trim()
        } else {
            line.trim()
        }
    }

    /**
     * Экранирование для CSV
     */
    private fun escapeCSV(value: String): String {
        val trimmedValue = value.trim()
        return if (trimmedValue.contains(separator) || trimmedValue.contains("\"") ||
            trimmedValue.contains("\n") || trimmedValue.contains("\r")) {
            "\"${trimmedValue.replace("\"", "\"\"")}\""
        } else {
            trimmedValue
        }
    }

    private fun appendToFile(content: String) {
        csvFile?.let { file ->
            FileOutputStream(file, true).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * Очистка файла (удаление всех данных, оставляем только заголовки)
     */
    fun clearCSVFile(): Boolean {
        return try {
            csvFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
                file.parentFile?.mkdirs()
                file.createNewFile()

                // Создаем новый файл с правильным именем
                val newName = generateFileName()
                val newFile = File(file.parentFile, newName)
                if (file.renameTo(newFile)) {
                    csvFile = newFile
                }

                writeHeaders()

                // Переименовываем файл с текущей датой
                renameFileToCurrentDate()

                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Чтение всех записей из файла
     */
    fun readAllRecords(): List<Map<String, String>> {
        val records = mutableListOf<Map<String, String>>()

        try {
            ensureFileExists()
            csvFile?.let { file ->
                if (file.exists()) {
                    val lines = file.readLines(Charsets.UTF_8)

                    for (i in 1 until lines.size) {
                        val line = lines[i]
                        if (line.isNotBlank()) {
                            val record = parseCSVLine(line)
                            if (record.isNotEmpty()) {
                                records.add(record)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return records
    }

    private fun parseCSVLine(line: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val values = parseCSVValues(line)

        for ((index, header) in headers.withIndex()) {
            result[header] = values.getOrElse(index) { "" }
        }

        return result
    }

    private fun parseCSVValues(line: String): List<String> {
        val values = mutableListOf<String>()
        var inQuotes = false
        val currentValue = StringBuilder()

        for (i in line.indices) {
            val c = line[i]

            when {
                c == '"' -> {
                    if (!inQuotes) {
                        inQuotes = true
                    } else if (i + 1 < line.length && line[i + 1] == '"') {
                        currentValue.append('"')
                    } else {
                        inQuotes = false
                    }
                }
                c == separator[0] && !inQuotes -> {
                    values.add(currentValue.toString())
                    currentValue.clear()
                }
                else -> {
                    currentValue.append(c)
                }
            }
        }

        values.add(currentValue.toString())
        return values
    }
}