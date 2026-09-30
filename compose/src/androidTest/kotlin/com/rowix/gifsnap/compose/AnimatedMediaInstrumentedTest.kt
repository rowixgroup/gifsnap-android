package com.rowix.gifsnap.compose

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.rowix.gifsnap.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AnimatedMediaInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun decoderActuallyAdvancesGifAndAnimatedWebpFrames() = runBlocking {
        val context = instrumentation.targetContext
        val loader = createGifSnapImageLoader(context)
        try {
            for (extension in listOf("gif", "webp")) {
                val bytes = instrumentation.context.assets.open("animated.$extension").use { it.readBytes() }
                val result = loader.execute(ImageRequest.Builder(context).data(bytes).build())
                assertTrue("$extension decoder failed: $result", result is SuccessResult)
                val drawable = (result as SuccessResult).image.asDrawable(context.resources)
                assertTrue("$extension must be animated", drawable is Animatable)
                val colors = mutableSetOf<Int>()
                val latch = CountDownLatch(1)
                val handler = Handler(Looper.getMainLooper())
                lateinit var render: Runnable
                instrumentation.runOnMainSync {
                    drawable.setBounds(0, 0, 48, 48)
                    drawable.callback = object : Drawable.Callback {
                        override fun invalidateDrawable(who: Drawable) { handler.post(render) }
                        override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) { handler.postAtTime(what, whenMillis) }
                        override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what) }
                    }
                    render = Runnable {
                        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                        drawable.draw(Canvas(bitmap)); colors.add(bitmap.getPixel(24, 24)); bitmap.recycle()
                        if (colors.size >= 3) latch.countDown()
                    }
                    (drawable as Animatable).start(); render.run()
                }
                val advanced = latch.await(6, TimeUnit.SECONDS)
                instrumentation.runOnMainSync { (drawable as Animatable).stop(); drawable.callback = null; handler.removeCallbacks(render) }
                assertTrue("$extension did not advance across three colors: $colors", advanced)
            }
        } finally { loader.shutdown() }
    }

}
