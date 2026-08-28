package com.example.outlookburstalert

/**
 * Lógica pura de detección de ráfaga: mantiene una lista de marcas de
 * tiempo (en milisegundos) de los correos detectados, descarta las que
 * ya salieron de la ventana de tiempo, y decide si se cumplió el umbral.
 *
 * No depende de ninguna API de Android — se puede probar con tests
 * unitarios normales (JUnit) sin necesitar un emulador ni un dispositivo
 * físico. Ver BurstDetectorTest.kt.
 *
 * También controla el "cooldown": una vez que se dispara una alerta, no
 * se vuelve a disparar otra hasta que pase cooldownMillis, para no
 * generar alarmas repetidas por la misma ráfaga.
 */
class BurstDetector(
    private val thresholdCount: Int,
    private val windowMillis: Long,
) {
    private val timestamps = mutableListOf<Long>()

    /**
     * Se llama cada vez que llega un nuevo correo. Devuelve true si con
     * este correo se cumplió el umbral de ráfaga Y no estamos en
     * cooldown — es decir, si hay que disparar la alarma ahora mismo.
     */
    @Synchronized
    fun registerEvent(now: Long = System.currentTimeMillis()): Boolean {
        timestamps.add(now)
        pruneOldTimestamps(now)

        val thresholdMet = timestamps.size >= thresholdCount
        if (!thresholdMet) {
            return false
        }

        // Se disparó la alarma: arrancamos de cero para la siguiente rafaga,
        // sin cooldown -- si llegan 5 correos mas ahorita mismo, vuelve a sonar.
        timestamps.clear()
        return true
    }

    /** Cuántos correos hay actualmente dentro de la ventana vigente. */
    @Synchronized
    fun currentCount(now: Long = System.currentTimeMillis()): Int {
        pruneOldTimestamps(now)
        return timestamps.size
    }

    @Synchronized
    fun reset() {
        timestamps.clear()
    }

    private fun pruneOldTimestamps(now: Long) {
        val cutoff = now - windowMillis
        timestamps.removeAll { it < cutoff }
    }
}