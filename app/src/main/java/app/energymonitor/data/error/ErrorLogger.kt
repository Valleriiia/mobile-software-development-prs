package app.energymonitor.data.error

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, WARN, ERROR }

/** Куди потрапляють записи про збої: у Logcat, у файл або в обидва місця одразу. */
interface ErrorLogger {
    fun log(level: LogLevel, message: String)
    fun readAll(): List<String>
    fun clear()
}

object NoOpErrorLogger : ErrorLogger {
    override fun log(level: LogLevel, message: String) = Unit
    override fun readAll(): List<String> = emptyList()
    override fun clear() = Unit
}

/** Пише в Logcat — зручно під час розробки з підключеним Android Studio. */
class LogcatErrorLogger(private val tag: String = "EnergyMonitor") : ErrorLogger {
    override fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.INFO -> Log.d(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
    }

    override fun readAll(): List<String> = emptyList()
    override fun clear() = Unit
}

/**
 * Дописує рядки у текстовий файл, щоб журнал переживав перезапуск застосунку
 * і його можна було переглянути на екрані. Файл не росте безмежно: коли він
 * перевищує maxBytes, лишається лише останні maxLines рядків.
 */
class FileErrorLogger(
    private val file: File,
    private val maxBytes: Long = 64 * 1024,
    private val maxLines: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis
) : ErrorLogger {

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    override fun log(level: LogLevel, message: String) {
        val line = "${format.format(Date(clock()))} [${level.name}] ${message.replace('\n', ' ')}"
        file.appendText(line + "\n")
        if (file.length() > maxBytes) trim()
    }

    @Synchronized
    override fun readAll(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotBlank() }.reversed() else emptyList()

    @Synchronized
    override fun clear() {
        if (file.exists()) file.writeText("")
    }

    private fun trim() {
        val kept = file.readLines().takeLast(maxLines)
        file.writeText(kept.joinToString("\n", postfix = "\n"))
    }
}

/** Об'єднує кілька журналів; читання йде з першого, що має що показати. */
class CompositeErrorLogger(private vararg val loggers: ErrorLogger) : ErrorLogger {
    override fun log(level: LogLevel, message: String) = loggers.forEach { it.log(level, message) }
    override fun readAll(): List<String> = loggers.map { it.readAll() }.firstOrNull { it.isNotEmpty() } ?: emptyList()
    override fun clear() = loggers.forEach { it.clear() }
}
