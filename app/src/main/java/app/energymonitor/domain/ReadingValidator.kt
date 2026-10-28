package app.energymonitor.domain

/** Перевірка форми додавання показника. Межі напруги/струму —
 *  орієнтовний діапазон побутової/промислової мережі, а не жорсткий стандарт. */
object ReadingValidator {

    private const val MIN_VOLTAGE = 100.0
    private const val MAX_VOLTAGE = 400.0
    private const val MAX_CURRENT = 200.0

    fun validate(
        label: String,
        existingLabels: List<String>,
        voltageText: String,
        currentText: String,
        powerFactorText: String
    ): ValidationResult {
        if (label.isBlank()) return ValidationResult(false, "Вкажіть назву точки обліку")

        if (existingLabels.any { it.equals(label.trim(), ignoreCase = true) }) {
            return ValidationResult(false, "Точка обліку з такою назвою вже додана")
        }

        val voltage = voltageText.replace(',', '.').toDoubleOrNull()
            ?: return ValidationResult(false, "Напруга має бути числом")
        val current = currentText.replace(',', '.').toDoubleOrNull()
            ?: return ValidationResult(false, "Струм має бути числом")
        val powerFactor = powerFactorText.replace(',', '.').toDoubleOrNull()
            ?: return ValidationResult(false, "cos(fi) має бути числом")

        if (voltage !in MIN_VOLTAGE..MAX_VOLTAGE) {
            return ValidationResult(false, "Напруга поза межами ${MIN_VOLTAGE.toInt()}–${MAX_VOLTAGE.toInt()} В")
        }
        if (current <= 0.0 || current > MAX_CURRENT) {
            return ValidationResult(false, "Струм поза межами 0–${MAX_CURRENT.toInt()} А")
        }
        if (powerFactor !in 0.0..1.0) {
            return ValidationResult(false, "cos(fi) має бути в межах від 0 до 1")
        }

        return ValidationResult(true, "Показник додано")
    }
}
