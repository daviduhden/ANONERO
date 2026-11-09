package io.anonero

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test. The debug builds carry the anon/nero
        // applicationId suffix, so the base package alone is not a valid assertion.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            appContext.packageName in setOf(
                "io.anonero",
                "io.anonero.debug",
                "io.anonero.anon",
                "io.anonero.anon.debug",
                "io.anonero.nero",
                "io.anonero.nero.debug",
            )
        )
    }
}