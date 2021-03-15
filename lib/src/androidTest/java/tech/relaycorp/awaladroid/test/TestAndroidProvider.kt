package tech.relaycorp.awaladroid.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

internal object TestAndroidProvider {
    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
}
