package com.pau.busapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassageHelperTest {

    @Test
    fun computeEcart_returnsRetardAndAvanceCorrectly() {
        val ret = PassageHelper.computeEcart(
            Passage("14:37", "reel", false, false, PassageStatut.A_LHEURE, 0),
            listOf(14 * 60 + 30)
        )
        val ava = PassageHelper.computeEcart(
            Passage("14:23", "reel", false, false, PassageStatut.A_LHEURE, 0),
            listOf(14 * 60 + 30)
        )

        assertEquals(7, ret)
        assertEquals(-7, ava)
        assertEquals(PassageStatut.RETARD, PassageHelper.toStatut(ret))
        assertEquals(PassageStatut.AVANCE, PassageHelper.toStatut(ava))
    }

    @Test
    fun computeEcart_handlesMidnightWrap() {
        val ecart = PassageHelper.computeEcart(
            Passage("00:05", "reel", false, false, PassageStatut.A_LHEURE, 0),
            listOf(23 * 60 + 58)
        )

        assertEquals(7, ecart)
        assertEquals(PassageStatut.RETARD, PassageHelper.toStatut(ecart))
    }

    @Test
    fun computeEcart_returnsNullWhenTooFar() {
        val ecart = PassageHelper.computeEcart(
            Passage("10:00", "reel", false, false, PassageStatut.A_LHEURE, 0),
            listOf(11 * 60 + 30)
        )

        assertNull(ecart)
    }
}
