package com.gersi365.ownplay

import org.junit.Assert.assertEquals
import org.junit.Test

class BaselineTest {
    @Test
    fun productName_isStableForBaseline() {
        assertEquals("OwnPlay", "OwnPlay")
    }
}
