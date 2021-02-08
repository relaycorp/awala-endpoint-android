package tech.relaycorp.relaydroid.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

object TestAndroidProvider {
    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
}
