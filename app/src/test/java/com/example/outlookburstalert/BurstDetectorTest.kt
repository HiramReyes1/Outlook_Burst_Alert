package com.example.outlookburstalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstDetectorTest {

    @Test
    fun `no dispara si llegan menos correos que el umbral`() {
        val detector = BurstDetector(thresholdCount = 5, windowMillis = 60_000L)
        val base = 1_000_000L

        for (i in 0 until 4) {
            val fired = detector.registerEvent(base + i * 1000)
            assertFalse("no deberia disparar en el evento #$i", fired)
        }
        assertEquals(4, detector.currentCount(base + 4000))
    }

    @Test
    fun `dispara justo al llegar al quinto correo dentro de la ventana`() {
        val detector = BurstDetector(thresholdCount = 5, windowMillis = 60_000L)
        val base = 1_000_000L

        detector.registerEvent(base)
        detector.registerEvent(base + 5_000)
        detector.registerEvent(base + 10_000)
        detector.registerEvent(base + 15_000)
        val fired = detector.registerEvent(base + 20_000)

        assertTrue(fired)
    }

    @Test
    fun `correos fuera de la ventana no cuentan`() {
        val detector = BurstDetector(thresholdCount = 5, windowMillis = 60_000L)
        val base = 1_000_000L

        // 3 correos "viejos"
        detector.registerEvent(base)
        detector.registerEvent(base + 1_000)
        detector.registerEvent(base + 2_000)

        // 2 correos nuevos, 70s despues -- los primeros 3 ya prescribieron
        // de la ventana de 60s
        detector.registerEvent(base + 70_000)
        val fired = detector.registerEvent(base + 71_000)

        assertFalse("solo hay 2 correos vigentes, no deberia disparar", fired)
        assertEquals(2, detector.currentCount(base + 71_000))
    }

    @Test
    fun `vuelve a disparar de inmediato si llega otra rafaga completa justo despues`() {
        val detector = BurstDetector(thresholdCount = 2, windowMillis = 60_000L)
        val base = 1_000_000L

        detector.registerEvent(base)
        val firstFire = detector.registerEvent(base + 1_000)
        assertTrue("deberia disparar con los primeros 2 correos", firstFire)

        // Sin cooldown: 2 correos mas, inmediatamente despues, deberian
        // disparar otra vez sin esperar nada
        detector.registerEvent(base + 2_000)
        val secondFire = detector.registerEvent(base + 3_000)
        assertTrue("deberia poder disparar de nuevo sin cooldown", secondFire)
    }

}