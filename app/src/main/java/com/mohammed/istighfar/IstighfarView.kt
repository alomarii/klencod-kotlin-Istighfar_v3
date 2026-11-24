package com.mohammed.istighfar

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import java.util.Calendar
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * برنامج الاستغفار
 * برمجة وتطوير محمد العمري 730215825
 */
class IstighfarView(context: Context) : View(context) {

    private class Look(
        val label: String, val sub: String, val phrase: String, val stages: Array<String>,
        val verses: Array<Array<String>>, val accent: Int,
        val barA: Int, val barB: Int, val dropA: Int, val dropB: Int,
        val pondA: Int, val pondB: Int, val tint: Int, val tintA: Int
    )

    private class Drop(var y: Float, var vy: Float, val start: Long)
    private class Ripple(val start: Long, val small: Boolean)
    private class Jet(val start: Long)
    private class Wave(val start: Long)
    private class Touch(val x: Float, val y: Float, val start: Long)
    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float, val grav: Float
    )

    companion object {
        val MAX_OPTIONS = intArrayOf(33, 100, 500, 1000)
        const val DEVELOPER = "برمجة وتطوير محمد العمري 730215825"
        const val DROP_VOL = 0.30f
        const val CHIME_VOL = 0.22f
        const val BELL_VOL = 0.26f
        const val BIRD_VOL = 0.07f

        // hit ids
        const val H_FORMULA = 1
        const val H_SOUND = 2
        const val H_NIGHT = 3
        const val H_STATS = 4
        const val H_SETTINGS = 5
        const val H_VOLPOP = 6
        const val H_VOLTRACK = 7
        const val H_SET_VOL = 8
        const val H_F0 = 10
        const val H_LIM0 = 20
        const val H_TH0 = 30
        const val H_REM_T0 = 40
        const val H_REM_S0 = 50
        const val H_DIM = 100
        const val H_SHEET = 101
        const val H_CLOSE = 102

        fun countKey(f: Int) = if (f == 0) "count" else "count$f"
    }

    private val prefs = context.getSharedPreferences("istighfar", Context.MODE_PRIVATE)
    private var maxCount = prefs.getInt("max", 100).let { if (MAX_OPTIONS.contains(it)) it else 100 }
    private var formula = prefs.getInt("formula", 0).coerceIn(0, 2)
    private var count = prefs.getInt(countKey(formula), 0).coerceIn(0, maxCount)
    private var volume = prefs.getFloat("vol", if (prefs.getBoolean("muted", false)) 0f else 0.62f).coerceIn(0f, 1f)
    private val muted: Boolean get() = volume < 0.01f
    private val gain: Float get() = volume * 1.6f
    private var themeMode = prefs.getInt("theme", 0).coerceIn(0, 2)   // 0 auto, 1 day, 2 night
    private var night = 0f
    private var autoNight = false
    private var themeAutoCheck = 0L
    private var shown = count * 100f / maxCount
    private var last = SystemClock.uptimeMillis()
    private var pulse = 0f
    private var born = SystemClock.uptimeMillis()
    private val introMs = 4600L
    private var introChime = false
    private var flashAt = 0L

    // adaptive quality (lowers effect density on slow phones)
    private var q = 1f
    private var avgDt = 0.0167f
    private var frameCnt = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var vignette: RadialGradient? = null

    // ---------------- tilt parallax
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var tiltT = 0f
    private var tilt = 0f
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            tiltT = (-e.values[0] / 6f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ---------------- scenery (fixed seed)
    private val rnd = Random(7)
    private val grassN = 220
    private val grassX = FloatArray(grassN) { rnd.nextFloat() }
    private val grassY = FloatArray(grassN) { rnd.nextFloat() }
    private val grassT = FloatArray(grassN) { if (it < grassN * 0.12f) 0f else 1f + rnd.nextFloat() * 37f }
    private val crackN = 36
    private val crackX = FloatArray(crackN) { rnd.nextFloat() }
    private val crackY = FloatArray(crackN) { rnd.nextFloat() }
    private val crackL = FloatArray(crackN) { 0.05f + rnd.nextFloat() * 0.12f }
    private val patchN = 16
    private val patchX = FloatArray(patchN) { rnd.nextFloat() }
    private val patchY = FloatArray(patchN) { rnd.nextFloat() }
    private val patchS = FloatArray(patchN) { 0.08f + rnd.nextFloat() * 0.16f }
    private val flowerN = 90
    private val flowerX = FloatArray(flowerN) { rnd.nextFloat() }
    private val flowerY = FloatArray(flowerN) { rnd.nextFloat() }
    private val flowerC = IntArray(flowerN) { rnd.nextInt(5) }
    private val flowerT = FloatArray(flowerN) { 48f + rnd.nextFloat() * 45f }
    private val rainN = 120
    private val rainX = FloatArray(rainN) { rnd.nextFloat() }
    private val rainP = FloatArray(rainN) { rnd.nextFloat() }
    private val rainS = FloatArray(rainN) { 0.9f + rnd.nextFloat() * 0.8f }
    private val dustN = 26
    private val dustY = FloatArray(dustN) { rnd.nextFloat() }
    private val dustP = FloatArray(dustN) { rnd.nextFloat() }
    private val dustS = FloatArray(dustN) { 0.6f + rnd.nextFloat() * 1.2f }
    private val moteN = 54
    private val moteX = FloatArray(moteN) { rnd.nextFloat() }
    private val moteY = FloatArray(moteN) { rnd.nextFloat() }
    private val moteP = FloatArray(moteN) { rnd.nextFloat() }
    private val leafPh = FloatArray(8) { rnd.nextFloat() }
    private val flowerColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFF4C430.toInt(), 0xFFF08A3C.toInt(),
        0xFFEE7B96.toInt(), 0xFFB197E0.toInt()
    )
    private val leafColors = intArrayOf(0xFF7BC558.toInt(), 0xFFA6D45A.toInt(), 0xFFE2C34A.toInt())

    // trees: x, baseY, height
    private val trees = arrayOf(
        floatArrayOf(0.08f, 0.50f, 0.17f),
        floatArrayOf(0.25f, 0.45f, 0.10f),
        floatArrayOf(0.40f, 0.43f, 0.06f),
        floatArrayOf(0.65f, 0.43f, 0.06f),
        floatArrayOf(0.80f, 0.455f, 0.11f),
        floatArrayOf(0.95f, 0.50f, 0.17f)
    )

    private val riverSeg = Path()
    private val riverPos = FloatArray(2)
    // five rivers: three appear at the "أنهار" stage, two more at the final stage
    private val riverPaths = Array(5) { Path() }
    private val riverPms = Array(5) { PathMeasure() }
    private val riverP = FloatArray(5)
    private val riverDelay = LongArray(5)
    private val riverWas = BooleanArray(5)
    private val riverSpd = floatArrayOf(0.34f, 0.30f, 0.38f, 0.28f, 0.32f)
    private val riverW = floatArrayOf(1f, 0.82f, 0.90f, 0.62f, 0.66f)
    private val floaters = ArrayList<Touch>()
    private val lightN = 24
    private val lightX = FloatArray(lightN) { rnd.nextFloat() }
    private val lightP = FloatArray(lightN) { rnd.nextFloat() }
    private val starN = 90
    private val starX = FloatArray(starN) { rnd.nextFloat() }
    private val starY = FloatArray(starN) { rnd.nextFloat() }
    private val starS = FloatArray(starN) { rnd.nextFloat() }

    private val drops = ArrayList<Drop>()
    private val ripples = ArrayList<Ripple>()
    private val jets = ArrayList<Jet>()
    private val waves = ArrayList<Wave>()
    private val touches = ArrayList<Touch>()
    private val particles = ArrayList<Particle>() // screen-space sparkles
    private val splash = ArrayList<Particle>()    // pond-space droplets
    private val prnd = Random()
    private val nodeAt = LongArray(4)

    private val looks = arrayOf(
        Look(
            "أستغفر الله", "قولها مع كل نقطة", "أستغفر الله",
            arrayOf("مطر", "جنات", "أنهار", "مغفرة"),
            arrayOf(
                arrayOf("يُرْسِلِ السَّمَاءَ عَلَيْكُم مِّدْرَارًا", "[نوح: 11]"),
                arrayOf("وَيَجْعَل لَّكُمْ جَنَّاتٍ", "[نوح: 12]"),
                arrayOf("وَيَجْعَل لَّكُمْ أَنْهَارًا", "[نوح: 12]"),
                arrayOf("اسْتَغْفِرُوا رَبَّكُمْ إِنَّهُ كَانَ غَفَّارًا", "[نوح: 10]")
            ),
            0xFFC8901A.toInt(), 0xFF7CC4F5.toInt(), 0xFF3F86CE.toInt(), 0xFFBEE8FF.toInt(), 0xFF2F8FDB.toInt(),
            0xFF97D8F7.toInt(), 0xFF2E83CB.toInt(), 0xFFFFFFFF.toInt(), 0
        ),
        Look(
            "أستغفر الله العظيم وأتوب إليه", "توبةٌ ورجوعٌ إلى الله", "أستغفر الله العظيم وأتوب إليه",
            arrayOf("توبة", "رجوع", "سكينة", "قبول"),
            arrayOf(
                arrayOf("وَاسْتَغْفِرُوا اللَّهَ إِنَّ اللَّهَ غَفُورٌ رَّحِيمٌ", "[المزمل: 20]"),
                arrayOf("إِنَّ اللَّهَ يُحِبُّ التَّوَّابِينَ وَيُحِبُّ الْمُتَطَهِّرِينَ", "[البقرة: 222]"),
                arrayOf("وَهُوَ الَّذِي يَقْبَلُ التَّوْبَةَ عَنْ عِبَادِهِ", "[الشورى: 25]"),
                arrayOf("قُلْ يَا عِبَادِيَ الَّذِينَ أَسْرَفُوا عَلَىٰ أَنفُسِهِمْ لَا تَقْنَطُوا مِن رَّحْمَةِ اللَّهِ", "[الزمر: 53]")
            ),
            0xFF1F9A72.toInt(), 0xFF7EE8BE.toInt(), 0xFF1E9E6E.toInt(), 0xFFCFF8E8.toInt(), 0xFF1FA878.toInt(),
            0xFF9AEBD0.toInt(), 0xFF1B8F72.toInt(), 0xFF3CD9A0.toInt(), 22
        ),
        Look(
            "سيد الاستغفار", "اقرأه بيقينٍ وحضور قلب",
            "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
            arrayOf("إقرار", "اعتراف", "رجاء", "يقين"),
            arrayOf(
                arrayOf("رَبَّنَا ظَلَمْنَا أَنفُسَنَا وَإِن لَّمْ تَغْفِرْ لَنَا وَتَرْحَمْنَا لَنَكُونَنَّ مِنَ الْخَاسِرِينَ", "[الأعراف: 23]"),
                arrayOf("رَبِّ إِنِّي ظَلَمْتُ نَفْسِي فَاغْفِرْ لِي فَغَفَرَ لَهُ", "[القصص: 16]"),
                arrayOf("وَرَحْمَتِي وَسِعَتْ كُلَّ شَيْءٍ", "[الأعراف: 156]"),
                arrayOf("إِنَّ اللَّهَ يَغْفِرُ الذُّنُوبَ جَمِيعًا", "[الزمر: 53]")
            ),
            0xFF7A52DC.toInt(), 0xFFD0B4FF.toInt(), 0xFF6B49D6.toInt(), 0xFFE6DAFF.toInt(), 0xFF7B5BE0.toInt(),
            0xFFCFBCF8.toInt(), 0xFF5A3DB8.toInt(), 0xFF9A72F0.toInt(), 28
        )
    )

    // smoothly animated look of the active formula
    private var cAccent = looks[formula].accent
    private var cBarA = looks[formula].barA
    private var cBarB = looks[formula].barB
    private var cDropA = looks[formula].dropA
    private var cDropB = looks[formula].dropB
    private var cPondA = looks[formula].pondA
    private var cPondB = looks[formula].pondB
    private var cTint = looks[formula].tint
    private var cTintA = looks[formula].tintA.toFloat()

    // panels
    private var panel = 0          // 0 none, 1 settings, 2 stats
    private var panelDraw = 1
    private var sheetA = 0f
    private var panelOpenedAt = 0L
    private var panelExtra = if (formula == 2) 1f else 0f
    private var volOpen = false
    private var volOpenAt = 0L
    private var dragId = 0

    // statistics
    private val daily = HashMap<Int, Int>()
    private var totalAll = prefs.getLong("total", 0L)
    private var statToday = 0
    private var statWeek = 0
    private var statStreak = 0
    private val barVals = IntArray(7)
    private val barLbl = Array(7) { "" }
    private val dayNames = arrayOf("", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

    init {
        loadDaily()
        refreshStats()
        Reminders.rescheduleAll(context)
    }

    private var cardV: Array<String>? = null
    private var cardStart = 0L

    // ---------------- sound
    private val sounds: SoundPool = SoundPool.Builder()
        .setMaxStreams(14)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val dropId = sounds.load(context, R.raw.drop, 1) // the only drop sound
    private val birdIds = intArrayOf(
        sounds.load(context, R.raw.bird1, 1), sounds.load(context, R.raw.bird2, 1)
    )
    private val chimeId = sounds.load(context, R.raw.chime, 1)
    private val bellId = sounds.load(context, R.raw.bell, 1)
    private val windLoop = sounds.load(context, R.raw.wind, 1)
    private val rainLoop = sounds.load(context, R.raw.rain, 1)
    private val streamLoop = sounds.load(context, R.raw.stream, 1)
    private val cricketLoop = sounds.load(context, R.raw.cricket, 1)
    private var cricketId = 0
    private var windId = 0
    private var rainId = 0
    private var streamId = 0
    private var ambCheck = 0L
    private var nextBird = SystemClock.uptimeMillis() + 4000

    private val resetRun = Runnable {
        count = 0
        cardV = null
        drops.clear()
        save()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun save() {
        prefs.edit()
            .putInt(countKey(formula), count).putInt("max", maxCount).putInt("formula", formula)
            .putFloat("vol", volume).putInt("theme", themeMode)
            .putString("daily", dailyString()).putLong("total", totalAll)
            .apply()
    }

    fun start() {
        accel?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        sounds.autoResume()
    }

    fun stop() {
        sensors.unregisterListener(sensorListener)
        sounds.autoPause()
    }

    fun release() {
        sounds.release()
    }

    private fun play(id: Int, rate: Float = 1f, vol: Float = 1f) {
        if (!muted) {
            val v = min(1f, vol * gain)
            sounds.play(id, v, v, 1, 0, rate)
        }
    }

    private fun ambience(now: Long, s: Float, rainAmt: Float, rv: Float) {
        if (now - ambCheck > 800) {
            ambCheck = now
            if (windId == 0) windId = sounds.play(windLoop, 0f, 0f, 1, -1, 1f)
            if (rainId == 0) rainId = sounds.play(rainLoop, 0f, 0f, 1, -1, 1f)
            if (streamId == 0) streamId = sounds.play(streamLoop, 0f, 0f, 1, -1, 1f)
            if (cricketId == 0) cricketId = sounds.play(cricketLoop, 0f, 0f, 1, -1, 1f)
        }
        val m = gain
        // everything is kept very quiet so it never disturbs the person praying
        if (windId != 0) {
            val v = min(1f, (1f - sm(s, 0f, 35f)) * 0.10f * m)
            sounds.setVolume(windId, v, v)
        }
        if (rainId != 0) {
            val v = min(1f, rainAmt * 0.16f * m)
            sounds.setVolume(rainId, v, v)
        }
        if (streamId != 0) {
            val v = min(1f, rv * 0.13f * m)
            sounds.setVolume(streamId, v, v)
        }
        if (cricketId != 0) {
            val v = min(1f, night * 0.10f * m)
            sounds.setVolume(cricketId, v, v)
        }
        if (s > 50f && !muted && now > nextBird && night < 0.5f) {
            play(birdIds[prnd.nextInt(birdIds.size)], 1f, BIRD_VOL)
            nextBird = now + 9000 + prnd.nextInt(12000)
        }
    }

    // ---------------- helpers
    private fun cl(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun sm(x: Float, a: Float, b: Float): Float {
        val t = cl((x - a) / (b - a))
        return t * t * (3f - 2f * t)
    }

    /** ease-out-back: grows with a little overshoot, like a plant sprouting */
    private fun back(x: Float): Float {
        val k = 1.70158f
        val u = x - 1f
        return 1f + (k + 1f) * u * u * u + k * u * u
    }

    private fun grow(s: Float, thr: Float, span: Float): Float = back(cl((s - thr) / span))

    private fun lerpC(a: Int, b: Int, t: Float): Int {
        val f = cl(t)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }

    private fun mix3(a: Int, b: Int, c: Int, s: Float): Int =
        lerpC(lerpC(a, b, sm(s, 0f, 25f)), c, sm(s, 40f, 50f))

    private fun fill(color: Int, alpha: Int = 255) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = alpha.coerceIn(0, 255)
    }

    private fun stroke(color: Int, width: Float, alpha: Int = 255) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width
        p.color = color
        p.alpha = alpha.coerceIn(0, 255)
    }

    private fun vGrad(c: Canvas, l: Float, t: Float, r: Float, b: Float, c0: Int, c1: Int) {
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(l, t, l, b, c0, c1, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, gp)
        gp.shader = null
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, r8: Int, g8: Int, b8: Int, a: Int) {
        if (a <= 0) return
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(
            x, y, r, Color.argb(a.coerceIn(0, 255), r8, g8, b8), Color.argb(0, r8, g8, b8), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, gp)
        gp.shader = null
    }

    private fun arabic(n: Int): String =
        n.toString().map { '٠' + (it - '0') }.joinToString("")

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, alpha: Int = 255
    ) {
        tp.textSize = size
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.color = color
        tp.alpha = alpha.coerceIn(0, 255)
        tp.textAlign = align
        c.drawText(s, x, y, tp)
    }

    private fun fit(s: String, maxW: Float, size: Float): Float {
        tp.textSize = size
        tp.typeface = Typeface.DEFAULT_BOLD
        val m = tp.measureText(s)
        return if (m > maxW) size * maxW / m else size
    }

    private fun milestone(k: Int): Int = (maxCount * k / 4.0).roundToInt()

    // ---------------- layout
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        fun rv(i: Int, x0: Float, y0: Float, a: FloatArray, b: FloatArray) {
            val r = riverPaths[i]
            r.reset()
            r.moveTo(x0 * fw, y0 * fh)
            r.cubicTo(a[0] * fw, a[1] * fh, a[2] * fw, a[3] * fh, a[4] * fw, a[5] * fh)
            r.cubicTo(b[0] * fw, b[1] * fh, b[2] * fw, b[3] * fh, b[4] * fw, b[5] * fh)
            riverPms[i].setPath(r, false)
        }
        rv(0, 0.50f, 0.56f, floatArrayOf(0.36f, 0.63f, 0.50f, 0.71f, 0.40f, 0.79f), floatArrayOf(0.33f, 0.85f, 0.26f, 0.90f, 0.18f, 1.00f))
        rv(1, 0.50f, 0.56f, floatArrayOf(0.62f, 0.63f, 0.50f, 0.72f, 0.60f, 0.80f), floatArrayOf(0.67f, 0.86f, 0.76f, 0.91f, 0.84f, 1.00f))
        rv(2, 0.50f, 0.57f, floatArrayOf(0.47f, 0.66f, 0.55f, 0.76f, 0.50f, 0.86f), floatArrayOf(0.47f, 0.92f, 0.52f, 0.96f, 0.50f, 1.02f))
        rv(3, 0.34f, 0.535f, floatArrayOf(0.24f, 0.56f, 0.14f, 0.58f, 0.07f, 0.64f), floatArrayOf(0.03f, 0.67f, -0.01f, 0.70f, -0.06f, 0.72f))
        rv(4, 0.66f, 0.535f, floatArrayOf(0.76f, 0.56f, 0.86f, 0.58f, 0.93f, 0.64f), floatArrayOf(0.97f, 0.67f, 1.01f, 0.70f, 1.06f, 0.72f))
        vignette = RadialGradient(
            fw / 2f, fh / 2f, fh * 0.75f,
            Color.argb(0, 0, 0, 0), Color.argb(80, 0, 0, 0), Shader.TileMode.CLAMP
        )
    }

    // ---------------- input
    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        touches.add(Touch(x, y, now))
        if (count >= maxCount) {
            count = 0
            cardV = null
            save()
            return
        }
        count++
        recordStat()
        save()
        pulse = 1f
        floaters.add(Touch(x, y, now))
        drops.add(Drop(panelBottomY() + height * 0.006f, height * 0.12f, now))
        for (k in 1..4) {
            if (count == milestone(k)) {
                val vv = looks[formula].verses[k - 1]
                cardV = arrayOf(vv[0], if (k == 4) vv[1] + "  •  اضغط للبدء من جديد" else vv[1])
                cardStart = now
                nodeAt[k - 1] = now
                waves.add(Wave(now))
                play(if (k == 4) bellId else chimeId, 1f, if (k == 4) BELL_VOL else CHIME_VOL)
                flashAt = now
                burst(width * 0.5f, panelBottomY() + height * 0.05f, if (k == 4) 46 else 26)
                break
            }
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun cycleMax() {
        val i = MAX_OPTIONS.indexOf(maxCount)
        maxCount = MAX_OPTIONS[(i + 1) % MAX_OPTIONS.size]
        if (count >= maxCount) count = 0
        cardV = null
        save()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun burst(x: Float, y: Float, n: Int) {
        for (i in 0 until n) {
            val a = prnd.nextFloat() * 2f * PI.toFloat()
            val sp = width * (0.1f + prnd.nextFloat() * 0.35f)
            val col = if (prnd.nextBoolean()) 0xFFFFD968.toInt() else 0xFFFFF3C4.toInt()
            particles.add(
                Particle(
                    x, y, cos(a) * sp, sin(a) * sp, 0f, 0.9f + prnd.nextFloat() * 0.8f,
                    col, width * (0.005f + prnd.nextFloat() * 0.007f), height * 0.15f
                )
            )
        }
    }

    private fun stepParticles(c: Canvas, list: ArrayList<Particle>, dt: Float) {
        val it2 = list.iterator()
        while (it2.hasNext()) {
            val q2 = it2.next()
            q2.life += dt
            if (q2.life >= q2.maxLife) {
                it2.remove()
                continue
            }
            q2.vy += q2.grav * dt
            q2.x += q2.vx * dt
            q2.y += q2.vy * dt
            val f = 1f - q2.life / q2.maxLife
            fill(q2.color, (255 * f).toInt())
            c.drawCircle(q2.x, q2.y, q2.size * (0.4f + 0.6f * f), p)
        }
    }

    // ---------------- drawing
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val raw = (now - last) / 1000f
        if (raw < 0.25f) avgDt = avgDt * 0.95f + raw * 0.05f
        frameCnt++
        if (frameCnt % 90 == 0) {
            if (avgDt > 0.026f) q = max(0.5f, q - 0.15f) else if (avgDt < 0.02f) q = min(1f, q + 0.1f)
        }
        val dt = raw.coerceIn(0f, 0.05f)
        last = now
        val t = now / 1000f
        val target = count * 100f / maxCount
        shown += (target - shown) * min(1f, dt * 2.5f)
        val s = shown
        pulse = max(0f, pulse - dt * 4f)
        tilt += (tiltT - tilt) * min(1f, dt * 4f)
        val cam = tilt * 0.6f + sin(t * 0.23f) * 0.18f
        updateLook(dt, now)
        val nt = night

        val gy = 0.41f * h
        val cx = 0.5f * w
        val cy = 0.525f * h
        val rx = 0.235f * w
        val ry = 0.042f * h
        val fgDx = cam * w * 0.012f

        val rainAmt = sm(s, 20f, 26f) * (1f - sm(s, 42f, 48f))
        val clear = sm(s, 40f, 50f)
        val doneAmt = sm(s, 92f, 100f)
        val desert = 1f - sm(s, 0f, 18f)

        val introAge = now - born
        val introDone = introAge >= introMs - 600
        updateRivers(now, dt, introDone)
        val rv = min(1f, (riverP[0] + riverP[1] + riverP[2] + riverP[3] + riverP[4]) / 3f)
        if (introAge > introMs * 0.55f) ambience(now, s, rainAmt, rv)

        c.save()
        val zoom = 1f + 0.012f * pulse * pulse
        c.scale(zoom, zoom, w * 0.5f, h * 0.45f)

        // ===== sky
        val skyTop = lerpC(mix3(0xFFC2A98A.toInt(), 0xFF6B7482.toInt(), 0xFF5AA7E2.toInt(), s), 0xFF060B26.toInt(), nt)
        val skyBot = lerpC(mix3(0xFFE9D3AB.toInt(), 0xFF9DA6B1.toInt(), 0xFFCBE9F8.toInt(), s), 0xFF22356B.toInt(), nt)
        vGrad(c, 0f, 0f, w, gy + 0.03f * h, skyTop, skyBot)
        glow(c, 0.4f * w, gy - 0.02f * h, 0.8f * w, 255, 243, 200, ((150 * clear + 50) * (1f - 0.85f * nt)).toInt())
        if (nt > 0.01f) drawNightSky(c, w, h, t, nt, cam)

        // ===== sun and light shafts
        val sunA = sm(s, 45f, 58f) * (1f - nt)
        if (sunA > 0.01f) {
            val sx = 0.88f * w + cam * w * 0.004f
            val sy = 0.12f * h
            glow(c, sx, sy, w * 0.30f, 255, 244, 190, (210 * sunA).toInt())
            fill(0xFFFFF6C8.toInt(), (255 * sunA).toInt())
            c.drawCircle(sx, sy, w * 0.045f, p)
            fill(0xFFFFFFFF.toInt(), (200 * sunA).toInt())
            c.drawCircle(sx, sy, w * 0.03f, p)
            if (q > 0.7f) {
                val shaftA = 20f * sunA + 34f * doneAmt
                for (k in 0 until 8) {
                    val a0 = (0.55 + k * 0.17 + sin(t * 0.4 + k) * 0.03).toFloat() * PI.toFloat() * 0.9f
                    val a1 = a0 + 0.055f
                    val len = h * 0.75f
                    path.reset()
                    path.moveTo(sx, sy)
                    path.lineTo(sx + cos(a0) * len, sy + sin(a0) * len)
                    path.lineTo(sx + cos(a1) * len, sy + sin(a1) * len)
                    path.close()
                    fill(0xFFFFF3C0.toInt(), (shaftA * (0.6f + 0.4f * sin(t * 1.5f + k))).toInt())
                    c.drawPath(path, p)
                }
            }
        }

        // ===== rainbow
        if (doneAmt * (1f - nt) > 0.01f) {
            val rc = intArrayOf(
                0xFFE74C3C.toInt(), 0xFFF39C12.toInt(), 0xFFF1E05A.toInt(),
                0xFF5FBF5F.toInt(), 0xFF4A9BE0.toInt(), 0xFF8E5BD0.toInt()
            )
            for (k in rc.indices) {
                val r = w * (0.36f - k * 0.016f)
                rect.set(cx - r, 0.47f * h - r, cx + r, 0.47f * h + r)
                stroke(rc[k], w * 0.018f, (105 * doneAmt * (1f - nt)).toInt())
                c.drawArc(rect, 180f, 180f, false, p)
            }
        }

        // ===== clouds (continuously drifting)
        val cloudTop = lerpC(lerpC(0xFFFFFFFF.toInt(), 0xFF7E8590.toInt(), rainAmt * 0.85f), 0xFF4A5680.toInt(), nt * 0.85f)
        val cloudBot = lerpC(lerpC(0xFFDCE6F0.toInt(), 0xFF5C626C.toInt(), rainAmt * 0.9f), 0xFF2A3358.toInt(), nt * 0.9f)
        val cbase = floatArrayOf(0.15f, 0.45f, 0.78f, 0.98f)
        val cspd = floatArrayOf(0.006f, 0.009f, 0.004f, 0.007f)
        val cy2 = floatArrayOf(0.335f, 0.322f, 0.355f, 0.34f)
        val crr = floatArrayOf(0.07f, 0.10f, 0.06f, 0.075f)
        for (k in 0..3) {
            val xx = (((cbase[k] + t * cspd[k]) % 1.5f) - 0.25f) * w + cam * w * 0.006f
            cloud(c, xx, cy2[k] * h, crr[k] * w, cloudTop, cloudBot)
        }

        // ===== mountains with parallax
        val farTop = lerpC(mix3(0xFFC7A68A.toInt(), 0xFF8C93A8.toInt(), 0xFFA6B3D6.toInt(), s), 0xFF2B3563.toInt(), nt)
        val farBot = lerpC(mix3(0xFFE0C7A2.toInt(), 0xFFA9B0BC.toInt(), 0xFFC9D8EC.toInt(), s), 0xFF3C4A80.toInt(), nt)
        c.save()
        c.translate(cam * w * 0.010f, 0f)
        path.reset()
        path.moveTo(-0.1f * w, 0.43f * h)
        path.lineTo(-0.1f * w, 0.375f * h)
        path.lineTo(0.12f * w, 0.335f * h)
        path.lineTo(0.28f * w, 0.372f * h)
        path.lineTo(0.47f * w, 0.325f * h)
        path.lineTo(0.62f * w, 0.368f * h)
        path.lineTo(0.80f * w, 0.33f * h)
        path.lineTo(1.1f * w, 0.372f * h)
        path.lineTo(1.1f * w, 0.43f * h)
        path.close()
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(0f, 0.32f * h, 0f, 0.43f * h, farTop, farBot, Shader.TileMode.CLAMP)
        c.drawPath(path, gp)
        gp.shader = null
        c.restore()

        val nearTop = lerpC(mix3(0xFFAB846A.toInt(), 0xFF747C97.toInt(), 0xFF8190BC.toInt(), s), 0xFF1D2650.toInt(), nt)
        val nearBot = lerpC(mix3(0xFFCDAA84.toInt(), 0xFF98A0AE.toInt(), 0xFFAFC0DA.toInt(), s), 0xFF2D3865.toInt(), nt)
        c.save()
        c.translate(cam * w * 0.020f, 0f)
        path.reset()
        path.moveTo(-0.1f * w, 0.43f * h)
        path.lineTo(-0.1f * w, 0.40f * h)
        path.lineTo(0.18f * w, 0.375f * h)
        path.lineTo(0.38f * w, 0.405f * h)
        path.lineTo(0.55f * w, 0.382f * h)
        path.lineTo(0.75f * w, 0.408f * h)
        path.lineTo(0.92f * w, 0.38f * h)
        path.lineTo(1.1f * w, 0.40f * h)
        path.lineTo(1.1f * w, 0.43f * h)
        path.close()
        gp.shader = LinearGradient(0f, 0.37f * h, 0f, 0.43f * h, nearTop, nearBot, Shader.TileMode.CLAMP)
        c.drawPath(path, gp)
        gp.shader = null
        c.restore()

        // ===== ground
        val gFar = lerpC(lerpC(0xFFE3C390.toInt(), 0xFFA6D170.toInt(), sm(s, 0f, 35f)), 0xFF1B3A4E.toInt(), nt * 0.75f)
        val gNear = lerpC(lerpC(0xFFC79A60.toInt(), 0xFF5CA042.toInt(), sm(s, 0f, 35f)), 0xFF12293D.toInt(), nt * 0.75f)
        vGrad(c, 0f, gy, w, h, gFar, gNear)
        for (i in 0 until patchN) {
            val x = patchX[i] * w
            val y = gy + 0.06f * h + patchY[i] * (h - gy - 0.12f * h)
            val rr = patchS[i] * w
            rect.set(x - rr, y - rr * 0.25f, x + rr, y + rr * 0.25f)
            fill(lerpC(0xFFA97C48.toInt(), 0xFF3F8A33.toInt(), sm(s, 0f, 35f)), 38)
            c.drawOval(rect, p)
        }
        if (rainAmt > 0.02f) {
            fill(0xFF1D3A52.toInt(), (46 * rainAmt).toInt())
            c.drawRect(0f, gy, w, h, p)
        }
        val haloA = (255 * (1f - sm(s, 5f, 35f))).toInt()
        if (haloA > 0) {
            gp.style = Paint.Style.FILL
            gp.shader = RadialGradient(
                cx, cy, rx * 1.6f,
                Color.argb(haloA, 125, 201, 78), Color.argb(0, 125, 201, 78), Shader.TileMode.CLAMP
            )
            rect.set(cx - rx * 1.6f, cy - ry * 3.2f, cx + rx * 1.6f, cy + ry * 3.4f)
            c.drawOval(rect, gp)
            gp.shader = null
        }
        val crackA = (170 * (1f - sm(s, 3f, 30f))).toInt()
        if (crackA > 0) {
            for (i in 0 until crackN) {
                val x = crackX[i] * w
                val y = gy + 0.04f * h + crackY[i] * (h - gy - 0.1f * h)
                val l = crackL[i] * w
                stroke(0xFF8A6838.toInt(), w * 0.0045f, crackA)
                c.drawLine(x, y, x + l, y + l * 0.12f, p)
                c.drawLine(x + l * 0.5f, y + l * 0.06f, x + l * 0.62f, y + l * 0.22f, p)
                stroke(0xFFF0D8A8.toInt(), w * 0.002f, crackA / 2)
                c.drawLine(x, y + w * 0.004f, x + l, y + l * 0.12f + w * 0.004f, p)
            }
        }

        // ===== desert life: blowing dust and a tumbleweed
        if (desert > 0.02f) {
            val dn = (dustN * q).toInt()
            for (i in 0 until dn) {
                val x = (((t * 0.05f * dustS[i] + dustP[i]) % 1.3f) - 0.15f) * w
                val y = gy + 0.05f * h + dustY[i] * (h - gy - 0.1f * h) + sin(t * 1.3f + i) * h * 0.004f
                stroke(0xFFFFF0D0.toInt(), w * 0.003f, (70 * desert).toInt())
                c.drawLine(x, y, x + w * 0.07f * dustS[i], y - h * 0.002f, p)
            }
            val tx = (((t * 0.06f) % 1.4f) - 0.2f) * w
            val rr = w * 0.05f
            val ty = h * 0.66f - abs(sin(t * 3.2f)) * h * 0.012f
            val rot = tx / rr
            rect.set(tx - rr, h * 0.66f + rr * 0.55f, tx + rr, h * 0.66f + rr * 0.75f)
            fill(0xFF000000.toInt(), (50 * desert).toInt())
            c.drawOval(rect, p)
            stroke(0xFF8B6A3E.toInt(), w * 0.003f, (210 * desert).toInt())
            c.drawCircle(tx, ty, rr, p)
            c.drawCircle(tx, ty, rr * 0.55f, p)
            for (k in 0 until 6) {
                val a = rot + k * PI.toFloat() / 6f
                c.drawLine(tx - cos(a) * rr, ty - sin(a) * rr, tx + cos(a) * rr, ty + sin(a) * rr, p)
            }
        }

        // ===== mist after the rain
        val mistA = sm(s, 22f, 36f) * (1f - sm(s, 55f, 78f))
        if (mistA > 0.02f) {
            glow(c, cx, cy + ry * 1.2f, rx * 2.1f, 235, 245, 250, (85 * mistA).toInt())
        }

        // ===== foreground group A (grass, river, pond) with parallax
        c.save()
        c.translate(fgDx, 0f)

        // grass (each blade sprouts at its own moment, then sways)
        val gcol1 = lerpC(0xFFA08752.toInt(), 0xFF3F8B34.toInt(), sm(s, 10f, 40f))
        val gcol2 = lerpC(0xFFB59B62.toInt(), 0xFF68B34B.toInt(), sm(s, 10f, 40f))
        val gl = (grassN * q).toInt()
        for (i in 0 until gl) {
            val gr = if (grassT[i] <= 0f) 1f else grow(s, grassT[i], 8f)
            if (gr < 0.03f) continue
            val y = gy + 0.02f * h + grassY[i] * (h - gy - 0.04f * h)
            val x = grassX[i] * w
            val sc = 0.5f + (y - gy) / (h - gy)
            val g = w * 0.015f * sc * gr
            val sw = sin(t * 1.7f + x * 0.02f) * g * 0.3f
            stroke(if (i % 2 == 0) gcol1 else gcol2, w * 0.0032f * sc)
            c.drawLine(x, y, x - g * 0.55f + sw, y - g, p)
            c.drawLine(x, y, x + sw * 1.2f, y - g * 1.25f, p)
            c.drawLine(x, y, x + g * 0.55f + sw, y - g * 0.95f, p)
        }

        // rivers flow out of the pond one after another when the stage is reached
        for (ri in 4 downTo 0) drawRiver(c, ri, w, t)

        // pond
        rect.set(cx - rx * 1.04f, cy - ry * 0.80f, cx + rx * 1.04f, cy + ry * 1.45f)
        fill(0xFF000000.toInt(), 55)
        c.drawOval(rect, p)
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        fill(0xFF8C7F73.toInt())
        c.drawOval(rect, p)
        for (k in 0 until 46) {
            val a = (2.0 * PI * k / 46.0).toFloat()
            if (sin(a) <= 0f) stone(c, cx + cos(a) * rx * 0.95f, cy + sin(a) * ry * 0.93f, w, k)
        }
        rect.set(cx - rx * 0.88f, cy - ry * 0.80f, cx + rx * 0.88f, cy + ry * 0.80f)
        fill(0xFF4A3828.toInt())
        c.drawOval(rect, p)
        rect.set(cx - rx * 0.78f, cy - ry * 0.68f, cx + rx * 0.78f, cy + ry * 0.68f)
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(
            cx - rx * 0.1f, cy - ry * 0.2f, rx * 0.85f,
            cPondA, cPondB, Shader.TileMode.CLAMP
        )
        c.drawOval(rect, gp)
        gp.shader = null
        // tree reflections on the rim of the water
        val refl = sm(s, 35f, 60f)
        if (refl > 0.02f) {
            rect.set(cx - rx * 0.62f, cy - ry * 0.62f, cx - rx * 0.18f, cy - ry * 0.18f)
            fill(0xFF2F7A35.toInt(), (70 * refl).toInt())
            c.drawOval(rect, p)
            rect.set(cx + rx * 0.22f, cy - ry * 0.62f, cx + rx * 0.62f, cy - ry * 0.2f)
            c.drawOval(rect, p)
        }
        rect.set(cx - rx * 0.62f, cy - ry * 0.55f, cx + rx * 0.15f, cy - ry * 0.08f)
        fill(0xFFFFFFFF.toInt(), 60)
        c.drawOval(rect, p)
        for (k in 0..3) {
            val sx = cx + sin(t * 0.7f + k * 1.7f) * rx * 0.45f
            val sy = cy + (k - 1.5f) * ry * 0.28f
            stroke(0xFFFFFFFF.toInt(), w * 0.003f, 90)
            c.drawLine(sx - rx * 0.12f, sy, sx + rx * 0.12f, sy, p)
        }
        for (k in 0..2) {
            val f = ((t * 0.25f + k / 3f) % 1f)
            rect.set(cx - rx * 0.6f * f, cy - ry * 0.55f * f, cx + rx * 0.6f * f, cy + ry * 0.55f * f)
            stroke(0xFFFFFFFF.toInt(), w * 0.0025f, (110 * (1f - f)).toInt())
            c.drawOval(rect, p)
        }
        // rain falling on the pond
        if (rainAmt > 0.05f) {
            for (i in 0 until 10) {
                val ph = (t * 1.7f + rainP[(i * 7) % rainN]) % 1f
                val x = cx + (rainX[(i * 11) % rainN] - 0.5f) * rx * 1.4f
                val y = cy + (rainP[(i * 13) % rainN] - 0.5f) * ry * 1.1f
                rect.set(x - rx * 0.07f * ph, y - ry * 0.12f * ph, x + rx * 0.07f * ph, y + ry * 0.12f * ph)
                stroke(0xFFFFFFFF.toInt(), w * 0.002f, (170 * (1f - ph) * rainAmt).toInt())
                c.drawOval(rect, p)
            }
        }

        // impact ripples (shaded rings) and central jets
        var bob = 0f
        ripples.removeAll { now - it.start > 1400 }
        for (r in ripples) {
            val age = (now - r.start) / 1400f
            val k = if (r.small) 0.6f else 1f
            bob += sin(age * 22f) * (1f - age) * w * 0.0035f * k
            for (j in 0..3) {
                val f = age - j * 0.14f
                if (f <= 0f) continue
                val ff = 0.08f + f * 0.92f
                val a = (1f - age) * (1f - j * 0.22f)
                rect.set(cx - rx * 0.74f * ff * k, cy - ry * 0.66f * ff * k, cx + rx * 0.74f * ff * k, cy + ry * 0.66f * ff * k)
                rect.offset(0f, w * 0.0025f)
                stroke(0xFF1B5E9C.toInt(), w * 0.0055f, (80 * a).toInt())
                c.drawOval(rect, p)
                rect.offset(0f, -w * 0.0025f)
                stroke(0xFFFFFFFF.toInt(), w * 0.0035f, (235 * a).toInt())
                c.drawOval(rect, p)
            }
        }
        val jit = jets.iterator()
        while (jit.hasNext()) {
            val j = jit.next()
            val age = (now - j.start) / 1000f
            if (age >= 0.2f) {
                jit.remove()
                ripples.add(Ripple(now, true))
                continue
            }
            val g = h * 3f
            val v = g * 0.1f
            val dy = v * age - 0.5f * g * age * age
            val topY = cy - dy
            val wd = w * 0.010f * (1f - 0.6f * age / 0.2f)
            if (dy > 2f) {
                stroke(0xFFCDEBFB.toInt(), wd, 230)
                c.drawLine(cx, cy, cx, topY + w * 0.004f, p)
            }
            fill(0xFFE4F5FF.toInt())
            c.drawCircle(cx, topY, w * 0.0075f, p)
        }

        // lily pads bob on the ripples
        val lily = sm(s, 60f, 80f)
        if (lily > 0.02f) {
            val lx = floatArrayOf(-0.45f, 0.42f, 0.05f)
            val ly = floatArrayOf(0.15f, -0.2f, 0.38f)
            for (k in 0..2) {
                val px = cx + lx[k] * rx
                val py = cy + ly[k] * ry + bob * (0.6f + k * 0.2f)
                rect.set(px - rx * 0.10f * lily, py - ry * 0.16f * lily, px + rx * 0.10f * lily, py + ry * 0.16f * lily)
                fill(0xFF3E9A47.toInt())
                c.drawOval(rect, p)
                fill(0xFF67BF5E.toInt(), 200)
                rect.inset(rect.width() * 0.18f, rect.height() * 0.18f)
                c.drawOval(rect, p)
            }
            val fyy = cy + ly[1] * ry - ry * 0.05f + bob * 0.8f
            fill(0xFFF7A8C8.toInt(), (255 * lily).toInt())
            c.drawCircle(cx + lx[1] * rx, fyy, w * 0.014f * lily, p)
            fill(0xFFFFE08A.toInt(), (255 * lily).toInt())
            c.drawCircle(cx + lx[1] * rx, fyy, w * 0.005f * lily, p)
        }
        // fish
        val fish = sm(s, 85f, 100f)
        if (fish > 0.02f) {
            for (k in 0..1) {
                val spd = 0.7f + k * 0.25f
                val a = t * spd + k * 3f
                val fx = cx + cos(a) * rx * 0.5f
                val fy = cy + sin(a) * ry * 0.42f
                val sgn = if (-sin(a) >= 0f) 1f else -1f
                rect.set(fx - w * 0.022f, fy - w * 0.008f, fx + w * 0.022f, fy + w * 0.008f)
                fill(0xFFF28C38.toInt(), (230 * fish).toInt())
                c.drawOval(rect, p)
                path.reset()
                path.moveTo(fx - sgn * w * 0.018f, fy)
                path.lineTo(fx - sgn * w * 0.036f, fy - w * 0.010f + sin(t * 9f) * w * 0.003f)
                path.lineTo(fx - sgn * w * 0.036f, fy + w * 0.010f + sin(t * 9f) * w * 0.003f)
                path.close()
                c.drawPath(path, p)
            }
        }
        // sparkling glints on water
        if (clear > 0.3f && q > 0.7f) {
            val gx = floatArrayOf(-0.5f, -0.2f, 0.1f, 0.35f, 0.55f)
            val gyy = floatArrayOf(0.1f, -0.3f, 0.25f, -0.15f, 0.2f)
            for (k in 0..4) {
                val tw = max(0f, sin(t * 2.1f + k * 1.9f)).pow(3)
                glint(c, cx + gx[k] * rx * 0.7f, cy + gyy[k] * ry * 0.7f, w * 0.014f, (230 * tw * clear).toInt())
            }
            for (ri in 0..4) {
                if (riverP[ri] < 0.3f) continue
                for (k in 0..3) {
                    val f = (k * 0.25f + t * 0.04f + ri * 0.11f) % 1f
                    riverPms[ri].getPosTan(riverPms[ri].length * easeRiver(riverP[ri]) * f, riverPos, null)
                    val tw = max(0f, sin(t * 2.6f + k * 1.3f + ri)).pow(3)
                    glint(c, riverPos[0], riverPos[1], w * 0.012f, (230 * tw * clear).toInt())
                }
            }
        }
        for (k in 0 until 46) {
            val a = (2.0 * PI * k / 46.0).toFloat()
            if (sin(a) > 0f) stone(c, cx + cos(a) * rx * 0.95f, cy + sin(a) * ry * 0.93f, w, k)
        }
        c.restore()

        // ===== trees (each depth layer has its own parallax)
        val fruit = sm(s, 75f, 95f)
        for (tr in trees) {
            c.save()
            c.translate(cam * w * (0.010f + tr[2] * 0.15f), 0f)
            tree(c, tr[0] * w, tr[1] * h, tr[2] * h, s, fruit, w, t)
            c.restore()
        }

        // ===== flowers bloom one by one
        c.save()
        c.translate(fgDx, 0f)
        val fn = (flowerN * q).toInt()
        for (i in 0 until fn) {
            val gr = grow(s, flowerT[i], 7f)
            if (gr < 0.03f) continue
            val y = gy + 0.05f * h + flowerY[i] * (h - gy - 0.1f * h)
            val x = flowerX[i] * w
            val sc = 0.5f + (y - gy) / (h - gy)
            val r = w * 0.007f * sc * gr
            val sw = sin(t * 1.5f + x * 0.03f) * r * 0.4f
            stroke(0xFF3F8B34.toInt(), w * 0.002f * sc)
            c.drawLine(x, y + r * 2.5f, x + sw, y, p)
            fill(flowerColors[flowerC[i]])
            for (a in 0 until 5) {
                val ang = (a * 2 * PI / 5).toFloat() + t * 0.1f
                c.drawCircle(x + sw + cos(ang) * r, y + sin(ang) * r, r * 0.85f, p)
            }
            fill(0xFFF7D84A.toInt())
            c.drawCircle(x + sw, y, r * 0.7f, p)
        }
        c.restore()

        // ===== birds, butterflies, falling leaves, light motes
        val birds = sm(s, 50f, 60f)
        if (birds > 0.01f) {
            for (k in 0..2) {
                val bx = ((t * 0.03f * (1f + k * 0.2f) + k * 0.31f) % 1.2f - 0.1f) * w
                val by = h * (0.305f + k * 0.012f) + sin(t * 1.3f + k) * h * 0.006f
                val fl = sin(t * 9f + k * 2f) * w * 0.008f
                stroke(0xFF3C4350.toInt(), w * 0.003f, (200 * birds).toInt())
                path.reset()
                path.moveTo(bx - w * 0.016f, by - fl)
                path.quadTo(bx - w * 0.006f, by - w * 0.008f, bx, by)
                path.quadTo(bx + w * 0.006f, by - w * 0.008f, bx + w * 0.016f, by - fl)
                c.drawPath(path, p)
            }
        }
        val butter = sm(s, 70f, 85f)
        if (butter > 0.01f) {
            for (k in 0..4) {
                val bx = (0.15f + k * 0.18f) * w + sin(t * (0.6f + k * 0.13f) + k) * w * 0.07f
                val by = h * (0.60f + k * 0.055f) + cos(t * (0.8f + k * 0.1f) + k * 2f) * h * 0.02f
                val flap = abs(sin(t * 11f + k * 1.7f))
                val ww = w * 0.016f * (0.25f + 0.75f * flap)
                fill(flowerColors[(k + 1) % 5], (240 * butter).toInt())
                rect.set(bx - ww - w * 0.002f, by - w * 0.012f, bx - w * 0.002f, by + w * 0.004f)
                c.drawOval(rect, p)
                rect.set(bx + w * 0.002f, by - w * 0.012f, bx + ww + w * 0.002f, by + w * 0.004f)
                c.drawOval(rect, p)
                rect.set(bx - ww * 0.7f - w * 0.002f, by, bx - w * 0.002f, by + w * 0.011f)
                c.drawOval(rect, p)
                rect.set(bx + w * 0.002f, by, bx + ww * 0.7f + w * 0.002f, by + w * 0.011f)
                c.drawOval(rect, p)
                stroke(0xFF3A2A20.toInt(), w * 0.0025f)
                c.drawLine(bx, by - w * 0.010f, bx, by + w * 0.010f, p)
            }
        }
        val leafAmt = sm(s, 65f, 80f)
        if (leafAmt > 0.02f) {
            for (i in 0 until 8) {
                val tr = trees[i % 6]
                val ph = (t * 0.05f + leafPh[i]) % 1f
                val lx = tr[0] * w + sin(ph * 9f + i) * w * 0.05f + ph * w * 0.06f
                val ly = (tr[1] - tr[2] * 0.9f) * h + ph * h * 0.2f
                val al = (sin(ph * PI.toFloat()) * 230 * leafAmt).toInt()
                c.save()
                c.rotate(ph * 720f + i * 40f, lx, ly)
                rect.set(lx - w * 0.008f, ly - w * 0.0035f, lx + w * 0.008f, ly + w * 0.0035f)
                fill(leafColors[i % 3], al)
                c.drawOval(rect, p)
                c.restore()
            }
        }
        val moteA = sm(s, 52f, 70f)
        if (moteA > 0.02f) {
            val mn = (moteN * q).toInt()
            for (i in 0 until mn) {
                val x = moteX[i] * w + sin(t * 0.4f + moteP[i] * 6f) * w * 0.03f
                val y = 0.30f * h + moteY[i] * 0.55f * h + cos(t * 0.3f + moteP[i] * 5f) * h * 0.01f
                val tw = 0.4f + 0.6f * sin(t * 1.2f + moteP[i] * 10f)
                fill(0xFFFFF4C8.toInt(), (110 * moteA * max(0f, tw)).toInt())
                c.drawCircle(x, y, w * 0.0035f, p)
            }
        }

        if (nt > 0.01f) drawNightOverlay(c, w, h, t, nt, cx + fgDx, cy, rx, ry)

        // ===== soft drifting lights (always alive, calm and hypnotic)
        val ln = (lightN * q).toInt()
        for (i in 0 until ln) {
            val ph = (t * (0.025f + lightP[i] * 0.03f) + lightP[i]) % 1f
            val lx = (lightX[i] + sin(t * 0.5f + lightP[i] * 9f) * 0.04f) * w
            val ly = h * (0.96f - ph * 0.66f)
            val la = sin(ph * PI.toFloat())
            val tw = 0.6f + 0.4f * sin(t * 2.2f + lightP[i] * 20f)
            fill(0xFFFFE9A8.toInt(), (42 * la * tw * (1f + 1.6f * nt)).toInt())
            c.drawCircle(lx, ly, w * 0.013f * (1f + 0.5f * nt), p)
            fill(0xFFFFFFFF.toInt(), (190 * la * tw).toInt())
            c.drawCircle(lx, ly, w * 0.0032f, p)
        }

        // ===== rain (three depth layers)
        if (rainAmt > 0.02f) {
            val rn = (rainN * q).toInt()
            for (i in 0 until rn) {
                val layer = i % 3
                val sp = rainS[i] * (0.8f + layer * 0.5f)
                val x = rainX[i] * w
                val y = ((t * sp + rainP[i]) % 1f) * h
                val len = h * (0.018f + 0.012f * layer)
                stroke(0xFFEAF2FA.toInt(), w * (0.002f + 0.0012f * layer), ((70 + 45 * layer) * rainAmt).toInt())
                c.drawLine(x, y, x - w * 0.006f * (1f + layer * 0.4f), y + len, p)
            }
            for (i in 0 until 26) {
                val ph = (t * 1.3f + rainP[i]) % 1f
                val x = rainX[(i * 3) % rainN] * w
                val y = gy + 0.07f * h + rainP[(i * 5) % rainN] * (h - gy - 0.14f * h)
                val rr = w * 0.014f * ph
                rect.set(x - rr, y - rr * 0.35f, x + rr, y + rr * 0.35f)
                stroke(0xFFFFFFFF.toInt(), w * 0.0022f, (170 * (1f - ph) * rainAmt).toInt())
                c.drawOval(rect, p)
            }
        }

        // ===== falling drops, crown splash
        c.save()
        c.translate(fgDx, 0f)
        val dit = drops.iterator()
        while (dit.hasNext()) {
            val d = dit.next()
            d.vy += h * 3.2f * dt
            d.y += d.vy * dt
            if (d.y >= cy) {
                dit.remove()
                ripples.add(Ripple(now, false))
                jets.add(Jet(now))
                play(dropId, 0.99f + prnd.nextFloat() * 0.02f, DROP_VOL)
                val cn = (13 * q).toInt() + 4
                for (k in 0 until cn) {
                    val a = (k * 2.0 * PI / cn).toFloat() + prnd.nextFloat() * 0.3f
                    val sp = w * (0.07f + prnd.nextFloat() * 0.06f)
                    splash.add(
                        Particle(
                            cx, cy, cos(a) * sp, -h * (0.05f + prnd.nextFloat() * 0.05f) + sin(a) * sp * 0.2f,
                            0f, 0.55f, 0xFFD4EEFB.toInt(), w * (0.003f + prnd.nextFloat() * 0.004f), h * 0.45f
                        )
                    )
                }
            } else {
                val grown = 0.3f + 0.7f * cl((now - d.start) / 120f)
                drawDrop(c, cx, d.y, w * 0.027f * grown, d.vy, h)
            }
        }
        stepParticles(c, splash, dt)
        c.restore()

        // ===== touch rings, growth waves, sparkles
        val tit = touches.iterator()
        while (tit.hasNext()) {
            val tc = tit.next()
            val age = (now - tc.start) / 500f
            if (age >= 1f) {
                tit.remove()
                continue
            }
            glow(c, tc.x, tc.y, w * 0.10f * (0.4f + age), 255, 255, 255, (70 * (1f - age)).toInt())
            stroke(0xFFFFFFFF.toInt(), w * 0.004f, (190 * (1f - age)).toInt())
            c.drawCircle(tc.x, tc.y, w * (0.02f + 0.07f * age), p)
        }
        val fit2 = floaters.iterator()
        while (fit2.hasNext()) {
            val fl = fit2.next()
            val age = (now - fl.start) / 1100f
            if (age >= 1f) {
                fit2.remove()
                continue
            }
            val fa = (255 * sin(age * PI.toFloat())).toInt()
            tp.setShadowLayer(w * 0.01f, 0f, 0f, 0xAAFFFFFF.toInt())
            text(c, "+١", fl.x, fl.y - h * 0.09f * age - w * 0.04f, w * 0.05f * (0.8f + 0.3f * age), cAccent, alpha = fa)
            tp.clearShadowLayer()
        }
        val wit = waves.iterator()
        while (wit.hasNext()) {
            val wv = wit.next()
            val age = (now - wv.start) / 1500f
            if (age >= 1f) {
                wit.remove()
                continue
            }
            val r = w * (0.15f + 1.1f * age)
            rect.set(cx + fgDx - r, cy - r * 0.42f, cx + fgDx + r, cy + r * 0.42f)
            stroke(0xFFFFE49A.toInt(), w * 0.014f * (1f - age), (170 * (1f - age)).toInt())
            c.drawOval(rect, p)
            rect.inset(-w * 0.03f, -w * 0.012f)
            stroke(0xFFFFFFFF.toInt(), w * 0.005f, (110 * (1f - age)).toInt())
            c.drawOval(rect, p)
            for (k in 0 until 14) {
                val ang = k * (2f * PI.toFloat() / 14f) + age * 0.8f
                val r0 = w * (0.10f + 0.5f * age)
                val r1 = r0 + w * 0.10f * (1f - age)
                stroke(0xFFFFE9A8.toInt(), w * 0.004f, (150 * (1f - age)).toInt())
                c.drawLine(cx + fgDx + cos(ang) * r0, cy + sin(ang) * r0 * 0.5f, cx + fgDx + cos(ang) * r1, cy + sin(ang) * r1 * 0.5f, p)
            }
        }
        val flashA = 1f - (now - flashAt) / 800f
        if (flashA > 0f) {
            fill(0xFFFFF6D8.toInt(), (80 * flashA).toInt())
            c.drawRect(0f, 0f, w, h, p)
        }
        if (doneAmt > 0.5f && prnd.nextFloat() < dt * 25f * q) {
            particles.add(
                Particle(
                    prnd.nextFloat() * w, gy + prnd.nextFloat() * (h - gy) * 0.9f,
                    (prnd.nextFloat() - 0.5f) * w * 0.03f, -h * 0.05f, 0f, 2.2f,
                    0xFFFFE9A0.toInt(), w * (0.003f + prnd.nextFloat() * 0.004f), 0f
                )
            )
        }
        stepParticles(c, particles, dt)

        if (cTintA > 0.5f) {
            fill(cTint, cTintA.toInt())
            c.drawRect(0f, 0f, w, h, p)
        }

        // golden glow when complete + vignette
        if (doneAmt > 0.01f) {
            fill(0xFFFFE19A.toInt(), (38 * doneAmt * (0.7f + 0.3f * sin(t * 2f))).toInt())
            c.drawRect(0f, 0f, w, h, p)
        }
        gp.style = Paint.Style.FILL
        gp.shader = vignette
        c.drawRect(0f, 0f, w, h, gp)
        gp.shader = null

        c.restore()
        drawHud(c, w, h, now)
        drawIntro(c, w, h, now)
        postInvalidateOnAnimation()
    }

    private fun easeRiver(a: Float): Float {
        val u = 1f - cl(a)
        return 1f - u * u * u
    }

    private fun updateRivers(now: Long, dt: Float, introDone: Boolean) {
        for (i in 0..4) {
            val on = introDone && count >= milestone(if (i < 3) 3 else 4)
            if (on && !riverWas[i]) riverDelay[i] = now + (if (i < 3) i else i - 3) * 380L
            riverWas[i] = on
            if (on) {
                if (now >= riverDelay[i]) riverP[i] = min(1f, riverP[i] + dt * riverSpd[i])
            } else {
                riverP[i] = max(0f, riverP[i] - dt * 1.6f)
            }
        }
    }

    private fun drawRiver(c: Canvas, i: Int, w: Float, t: Float) {
        val a = riverP[i]
        if (a < 0.01f) return
        val m = riverPms[i]
        val e = easeRiver(a)
        riverSeg.reset()
        m.getSegment(0f, m.length * e, riverSeg, true)
        riverSeg.rLineTo(0f, 0f)
        val k = riverW[i] * (0.6f + 0.4f * cl(a * 2f))
        stroke(0xFF3F8A33.toInt(), w * 0.105f * k, 120)
        c.drawPath(riverSeg, p)
        stroke(0xFF3C82C4.toInt(), w * 0.080f * k)
        c.drawPath(riverSeg, p)
        stroke(0xFF5FAAE6.toInt(), w * 0.062f * k)
        c.drawPath(riverSeg, p)
        stroke(0xFF8CC8F4.toInt(), w * 0.030f * k, 150)
        c.drawPath(riverSeg, p)
        stroke(0xFFFFFFFF.toInt(), w * 0.007f * k, 170)
        p.pathEffect = DashPathEffect(floatArrayOf(w * 0.05f, w * 0.10f), -t * w * 0.22f)
        c.drawPath(riverSeg, p)
        p.pathEffect = null
        stroke(0xFFFFFFFF.toInt(), w * 0.004f * k, 120)
        p.pathEffect = DashPathEffect(floatArrayOf(w * 0.03f, w * 0.07f), -t * w * 0.34f + w * 0.02f)
        c.drawPath(riverSeg, p)
        p.pathEffect = null
        // shining head of the water while the river is flowing out
        if (a < 0.999f) {
            m.getPosTan(m.length * e, riverPos, null)
            glow(c, riverPos[0], riverPos[1], w * 0.08f, 255, 255, 255, 150)
            fill(0xFFFFFFFF.toInt(), 230)
            c.drawCircle(riverPos[0], riverPos[1], w * 0.011f, p)
        }
    }

    /** Logo animation shown when the app opens: "الاخوة للهواتف" + developer credit. */
    private fun drawIntro(c: Canvas, w: Float, h: Float, now: Long) {
        val ageL = now - born
        if (ageL >= introMs) return
        val age = ageL.toFloat()
        val t = age / 1000f
        val out = 1f - sm(age, introMs - 800f, introMs.toFloat())
        c.saveLayerAlpha(0f, 0f, w, h, (255 * out).toInt())

        if (!introChime && age > 1000f) {
            introChime = true
            play(chimeId, 1f, 0.14f)
        }

        // background
        vGrad(c, 0f, 0f, w, h, 0xFF041C1E.toInt(), 0xFF0B5A4A.toInt())
        val cx0 = w * 0.5f
        val cy0 = h * 0.35f
        glow(c, cx0, cy0, w * (0.66f + 0.04f * sin(t * 2f)), 70, 210, 180, 100)
        glow(c, cx0, cy0, w * 0.36f, 255, 224, 140, (80 * sm(age, 300f, 1400f)).toInt())

        // twinkling stars
        val starA = sm(age, 0f, 700f)
        for (i in 0 until moteN) {
            val tw = 0.5f + 0.5f * sin(t * 1.8f + moteP[i] * 12f)
            fill(0xFFFFF4C8.toInt(), (180 * tw * starA).toInt())
            c.drawCircle(moteX[i] * w, moteY[i] * h * 0.92f, w * (0.0022f + 0.002f * moteP[i]), p)
        }

        // expanding rings
        for (k in 0..2) {
            val f = (t * 0.55f + k / 3f) % 1f
            stroke(0xFFFFE49A.toInt(), w * 0.004f * (1f - f), (150 * (1f - f)).toInt())
            c.drawCircle(cx0, cy0, w * (0.12f + 0.42f * f), p)
        }

        // emblem: a phone holding a water drop
        val pop = back(cl((age - 150f) / 900f))
        val bob = sin(t * 2.2f) * w * 0.008f
        c.save()
        c.translate(cx0, cy0 + bob)
        c.scale(pop, pop)
        c.rotate(sin(t * 1.4f) * 2.5f)
        val pw = w * 0.20f
        val ph = w * 0.36f
        val rr = pw * 0.16f
        rect.set(-pw / 2f, -ph / 2f, pw / 2f, ph / 2f)
        fill(0xFF000000.toInt(), 80)
        rect.offset(0f, w * 0.012f)
        c.drawRoundRect(rect, rr, rr, p)
        rect.offset(0f, -w * 0.012f)
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(-pw / 2f, -ph / 2f, pw / 2f, ph / 2f, 0xFFFFE9A8.toInt(), 0xFFC8901A.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rr, rr, gp)
        gp.shader = null
        // screen
        rect.set(-pw / 2f + pw * 0.08f, -ph / 2f + ph * 0.07f, pw / 2f - pw * 0.08f, ph / 2f - ph * 0.07f)
        gp.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFF12867A.toInt(), 0xFF052F34.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rr * 0.6f, rr * 0.6f, gp)
        gp.shader = null
        // notch and home bar
        fill(0xFF0A2A2C.toInt())
        rect.set(-pw * 0.13f, -ph / 2f + ph * 0.085f, pw * 0.13f, -ph / 2f + ph * 0.105f)
        c.drawRoundRect(rect, ph * 0.01f, ph * 0.01f, p)
        stroke(0xFFFFFFFF.toInt(), w * 0.004f, 150)
        c.drawLine(-pw * 0.14f, ph / 2f - ph * 0.105f, pw * 0.14f, ph / 2f - ph * 0.105f, p)
        // droplet with ripples
        val dr = pw * 0.17f * (1f + 0.05f * sin(t * 3f))
        val dy = -ph * 0.06f
        path.reset()
        path.moveTo(0f, dy - dr * 1.9f)
        path.lineTo(-dr * 0.92f, dy - dr * 0.3f)
        path.lineTo(dr * 0.92f, dy - dr * 0.3f)
        path.close()
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(-dr * 0.3f, dy - dr * 0.3f, dr * 1.8f, 0xFFD2F2FF.toInt(), 0xFF2F8FDB.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, gp)
        c.drawCircle(0f, dy, dr, gp)
        gp.shader = null
        stroke(0xFFFFFFFF.toInt(), dr * 0.18f, 220)
        rect.set(-dr * 0.6f, dy - dr * 0.6f, dr * 0.6f, dy + dr * 0.6f)
        c.drawArc(rect, 110f, 60f, false, p)
        for (k in 0..2) {
            val f = (t * 0.7f + k / 3f) % 1f
            rect.set(-pw * 0.36f * f, ph * 0.20f - pw * 0.10f * f, pw * 0.36f * f, ph * 0.20f + pw * 0.10f * f)
            stroke(0xFFBFEFFF.toInt(), w * 0.003f, (200 * (1f - f)).toInt())
            c.drawOval(rect, p)
        }
        // travelling shine across the phone body
        rect.set(-pw / 2f, -ph / 2f, pw / 2f, ph / 2f)
        c.save()
        path.reset()
        path.addRoundRect(rect, rr, rr, Path.Direction.CW)
        c.clipPath(path)
        val shx = -pw + ((t * 0.6f) % 1.8f) * pw * 1.6f
        path.reset()
        path.moveTo(shx, -ph / 2f)
        path.lineTo(shx + pw * 0.22f, -ph / 2f)
        path.lineTo(shx + pw * 0.22f - pw * 0.30f, ph / 2f)
        path.lineTo(shx - pw * 0.30f, ph / 2f)
        path.close()
        fill(0xFFFFFFFF.toInt(), 80)
        c.drawPath(path, p)
        c.restore()
        c.restore()

        // title "الاخوة للهواتف"
        val title = "الاخوة للهواتف"
        val ty = h * 0.605f
        val tsz = fit(title, w * 0.80f, w * 0.15f)
        val reveal = sm(age, 900f, 2000f)
        if (reveal > 0.001f) {
            tp.textSize = tsz
            tp.typeface = Typeface.DEFAULT_BOLD
            val tw2 = tp.measureText(title)
            val tsc = 0.88f + 0.12f * back(cl((age - 900f) / 1100f))
            c.save()
            val half = (tw2 * 0.5f + w * 0.04f) * reveal
            c.clipRect(cx0 - half, ty - tsz * 1.15f, cx0 + half, ty + tsz * 0.55f)
            c.scale(tsc, tsc, cx0, ty)
            val x0 = cx0 - tw2 * 1.5f + ((t * 0.55f) % 1.7f) * tw2 * 2.4f
            tp.shader = LinearGradient(
                x0, 0f, x0 + tw2, 0f,
                intArrayOf(0xFFFFD968.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD968.toInt()),
                floatArrayOf(0.35f, 0.5f, 0.65f), Shader.TileMode.CLAMP
            )
            tp.setShadowLayer(w * 0.03f, 0f, 0f, 0xCC2FE0C0.toInt())
            text(c, title, cx0, ty, tsz, 0xFFFFD968.toInt())
            tp.shader = null
            tp.clearShadowLayer()
            c.restore()
        }

        // ornament line + diamond
        val lineA = sm(age, 1700f, 2600f)
        if (lineA > 0.01f) {
            val ly = h * 0.635f
            val half = w * 0.30f * lineA
            stroke(0xFFFFD968.toInt(), w * 0.006f, 70)
            c.drawLine(cx0 - half, ly, cx0 + half, ly, p)
            stroke(0xFFFFE49A.toInt(), w * 0.0025f, 230)
            c.drawLine(cx0 - half, ly, cx0 + half, ly, p)
            c.save()
            c.rotate(45f, cx0, ly)
            fill(0xFFFFE49A.toInt(), (255 * lineA).toInt())
            val ds = w * 0.012f * back(cl((age - 1900f) / 600f))
            c.drawRect(cx0 - ds, ly - ds, cx0 + ds, ly + ds, p)
            c.restore()
        }

        // developer credit (under the logo)
        val credA = sm(age, 2300f, 3200f)
        if (credA > 0.01f) {
            val slide = (1f - credA) * h * 0.02f
            val ca = (255 * credA).toInt()
            tp.setShadowLayer(w * 0.012f, 0f, 0f, 0xCC000000.toInt())
            val line1 = "برمجة وتطوير محمد العمري"
            text(c, line1, cx0, h * 0.700f + slide, fit(line1, w * 0.82f, w * 0.058f), 0xFFE8F6F0.toInt(), alpha = ca)
            tp.clearShadowLayer()
            val num = "730215825"
            val nsz = w * 0.062f
            tp.letterSpacing = 0.12f
            tp.textSize = nsz
            tp.typeface = Typeface.DEFAULT_BOLD
            val nw = tp.measureText(num)
            rect.set(cx0 - nw / 2f - w * 0.05f, h * 0.722f + slide, cx0 + nw / 2f + w * 0.05f, h * 0.722f + slide + nsz * 1.7f)
            stroke(0xFFFFD968.toInt(), w * 0.003f, (170 * credA).toInt())
            c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, p)
            fill(0xFFFFD968.toInt(), (22 * credA).toInt())
            c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, p)
            text(c, num, cx0, rect.centerY() + nsz * 0.34f, nsz, 0xFFFFD968.toInt(), alpha = ca)
            tp.letterSpacing = 0f
        }

        // thin loading line
        val bw = w * 0.34f
        stroke(0xFFFFFFFF.toInt(), w * 0.004f, 40)
        c.drawLine(cx0 - bw / 2f, h * 0.90f, cx0 + bw / 2f, h * 0.90f, p)
        stroke(0xFFFFD968.toInt(), w * 0.004f, 220)
        val lf = cl(age / (introMs - 700f))
        c.drawLine(cx0 - bw / 2f, h * 0.90f, cx0 - bw / 2f + bw * lf, h * 0.90f, p)

        c.restore()
    }

    private fun glint(c: Canvas, x: Float, y: Float, sz: Float, a: Int) {
        if (a <= 4) return
        stroke(0xFFFFFFFF.toInt(), sz * 0.16f, a)
        c.drawLine(x - sz, y, x + sz, y, p)
        c.drawLine(x, y - sz, x, y + sz, p)
        stroke(0xFFFFFFFF.toInt(), sz * 0.09f, a / 2)
        c.drawLine(x - sz * 0.55f, y - sz * 0.55f, x + sz * 0.55f, y + sz * 0.55f, p)
        c.drawLine(x - sz * 0.55f, y + sz * 0.55f, x + sz * 0.55f, y - sz * 0.55f, p)
        fill(0xFFFFFFFF.toInt(), a)
        c.drawCircle(x, y, sz * 0.2f, p)
    }

    private fun stone(c: Canvas, x: Float, y: Float, w: Float, k: Int) {
        val rw = w * (0.021f + (k % 3) * 0.002f)
        val rh = w * 0.0135f
        rect.set(x - rw - w * 0.002f, y - rh + w * 0.003f, x + rw + w * 0.002f, y + rh + w * 0.003f)
        fill(0xFF51483F.toInt())
        c.drawOval(rect, p)
        rect.set(x - rw, y - rh, x + rw, y + rh)
        fill(if (k % 2 == 0) 0xFFC4B8AA.toInt() else 0xFFA79A8C.toInt())
        c.drawOval(rect, p)
        rect.set(x - rw * 0.7f, y - rh * 0.8f, x + rw * 0.1f, y - rh * 0.05f)
        fill(0xFFEFE8DC.toInt(), 150)
        c.drawOval(rect, p)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, r: Float, top: Int, bot: Int) {
        fill(bot)
        c.drawCircle(x, y + r * 0.12f, r, p)
        c.drawCircle(x - r * 0.95f, y + r * 0.40f, r * 0.7f, p)
        c.drawCircle(x + r * 0.95f, y + r * 0.35f, r * 0.78f, p)
        rect.set(x - r * 1.5f, y + r * 0.25f, x + r * 1.6f, y + r * 1.05f)
        c.drawRoundRect(rect, r * 0.4f, r * 0.4f, p)
        fill(top)
        c.drawCircle(x, y - r * 0.05f, r * 0.95f, p)
        c.drawCircle(x - r * 0.92f, y + r * 0.25f, r * 0.62f, p)
        c.drawCircle(x + r * 0.92f, y + r * 0.2f, r * 0.7f, p)
        rect.set(x - r * 1.45f, y + r * 0.15f, x + r * 1.55f, y + r * 0.8f)
        c.drawRoundRect(rect, r * 0.35f, r * 0.35f, p)
    }

    private fun leafBlob(c: Canvas, x: Float, y: Float, r: Float) {
        fill(0xFF3B8A38.toInt())
        c.drawCircle(x + r * 0.1f, y + r * 0.15f, r, p)
        fill(0xFF58B047.toInt())
        c.drawCircle(x, y, r * 0.92f, p)
        fill(0xFF82CF60.toInt())
        c.drawCircle(x - r * 0.25f, y - r * 0.28f, r * 0.55f, p)
        fill(0xFFB6E88C.toInt(), 150)
        c.drawCircle(x - r * 0.4f, y - r * 0.45f, r * 0.22f, p)
        fill(0xFF3B8A38.toInt(), 150)
        c.drawCircle(x + r * 0.35f, y + r * 0.25f, r * 0.2f, p)
    }

    private fun tree(
        c: Canvas, x: Float, by: Float, th: Float, s: Float, fruit: Float, w: Float, t: Float
    ) {
        val leafAll = sm(s, 28f, 55f)
        val sway = sin(t * 1.2f + x * 0.01f) * th * 0.02f * (0.4f + leafAll)
        val topX = x + sway
        val topY = by - th
        val tw = th * 0.09f
        rect.set(x - th * 0.38f, by - th * 0.03f, x + th * 0.38f, by + th * 0.05f)
        fill(0xFF000000.toInt(), 45)
        c.drawOval(rect, p)
        path.reset()
        path.moveTo(x - tw, by)
        path.lineTo(topX - tw * 0.3f, topY)
        path.lineTo(topX + tw * 0.3f, topY)
        path.lineTo(x + tw, by)
        path.close()
        fill(0xFF6A4A36.toInt())
        c.drawPath(path, p)
        path.reset()
        path.moveTo(x - tw, by)
        path.lineTo(topX - tw * 0.3f, topY)
        path.lineTo(topX, topY)
        path.lineTo(x - tw * 0.1f, by)
        path.close()
        fill(0xFF8A6446.toInt())
        c.drawPath(path, p)

        val ex = floatArrayOf(-0.30f, 0.30f, -0.17f, 0.17f)
        val ey = floatArrayOf(0.88f, 0.88f, 1.03f, 1.03f)
        val es = floatArrayOf(0.40f, 0.40f, 0.66f, 0.66f)
        stroke(0xFF6A4A36.toInt(), th * 0.045f)
        for (i in 0..3) {
            val sx = x + (topX - x) * es[i]
            val sy = by - th * es[i]
            c.drawLine(sx, sy, x + ex[i] * th + sway, by - ey[i] * th, p)
        }
        if (leafAll > 0.01f) {
            val r = th * 0.19f
            // every blob of foliage sprouts at its own moment with a little overshoot
            for (i in 0..3) {
                val g = grow(s, 28f + i * 4f, 18f)
                if (g > 0.02f) leafBlob(c, x + ex[i] * th + sway, by - ey[i] * th, r * g)
            }
            val g4 = grow(s, 40f, 18f)
            if (g4 > 0.02f) leafBlob(c, topX, topY - r * 0.3f, r * 1.15f * g4)
            val g5 = grow(s, 34f, 18f)
            if (g5 > 0.02f) leafBlob(c, x + sway * 0.7f, by - th * 0.72f, r * 0.8f * g5)
            if (fruit > 0.02f) {
                val fr = th * 0.022f * fruit
                for (i in 0..3) {
                    fill(if (i % 2 == 0) 0xFFE8503A.toInt() else 0xFFF2A33A.toInt())
                    c.drawCircle(x + ex[i] * th + sway + r * 0.35f, by - ey[i] * th + r * 0.3f, fr, p)
                    c.drawCircle(x + ex[i] * th + sway - r * 0.4f, by - ey[i] * th - r * 0.1f, fr, p)
                }
                fill(0xFFF2A33A.toInt())
                c.drawCircle(topX + r * 0.5f, topY, fr, p)
            }
        }
    }

    private fun drawDrop(c: Canvas, x: Float, y: Float, r: Float, vy: Float, h: Float) {
        val st = 1f + min(0.8f, vy / (h * 1.6f))
        val tail = r * 2.6f * st
        glow(c, x, y - r, r * 3.2f, 190, 230, 255, 90)
        stroke(0xFFBFE6FA.toInt(), r * 0.5f, 60)
        c.drawLine(x, y - tail - r * 5f * st, x, y - tail, p)
        path.reset()
        path.moveTo(x, y - tail)
        path.lineTo(x - r * 0.92f, y - r * 0.35f)
        path.lineTo(x + r * 0.92f, y - r * 0.35f)
        path.close()
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.3f, r * 1.6f,
            cDropA, cDropB, Shader.TileMode.CLAMP
        )
        c.drawPath(path, gp)
        c.drawCircle(x, y, r, gp)
        gp.shader = null
        stroke(0xFFFFFFFF.toInt(), r * 0.2f, 220)
        rect.set(x - r * 0.6f, y - r * 0.6f, x + r * 0.6f, y + r * 0.6f)
        c.drawArc(rect, 110f, 60f, false, p)
    }

    // =====================================================================
    //                      واجهة المستخدم الجديدة
    // =====================================================================

    private fun ease(x: Float): Float {
        val u = 1f - cl(x)
        return 1f - u * u * u
    }

    private fun alp(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** colour that blends between its day and night version */
    private fun ui(day: Int, nightC: Int): Int = lerpC(day, nightC, night)

    private fun mw(s: String, size: Float): Float {
        tp.textSize = size
        tp.typeface = Typeface.DEFAULT_BOLD
        return tp.measureText(s)
    }

    private fun arabic2(n: Int): String = arabic(n).let { if (n < 10) "٠$it" else it }

    private fun fmtTime(h: Int, m: Int): String {
        val hh = if (h % 12 == 0) 12 else h % 12
        return arabic(hh) + ":" + arabic2(m) + (if (h < 12) " ص" else " م")
    }

    // ---------------- hit testing (rebuilt every frame while drawing)
    private val hitR = Array(64) { RectF() }
    private val hitI = IntArray(64)
    private var hitN = 0
    private val sh = RectF()
    private val sliderL = FloatArray(2)
    private val sliderR = FloatArray(2)
    private val path2 = Path()

    private fun hit(l: Float, t: Float, r: Float, b: Float, id: Int) {
        if (hitN >= hitR.size) return
        hitR[hitN].set(l, t, r, b)
        hitI[hitN] = id
        hitN++
    }

    private fun hitRect(r: RectF, id: Int) = hit(r.left, r.top, r.right, r.bottom, id)

    private fun hitAt(x: Float, y: Float): Int {
        for (i in hitN - 1 downTo 0) if (hitR[i].contains(x, y)) return hitI[i]
        return -1
    }

    // ---------------- wrapped text (cached so it costs nothing per frame)
    private val wrapCache = HashMap<String, StaticLayout>()

    private fun wrapped(s: String, maxW: Int, maxH: Float, startSize: Float, minSize: Float): StaticLayout {
        val key = "$s|$maxW|${maxH.toInt()}|${startSize.toInt()}"
        wrapCache[key]?.let { return it }
        val tpp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tpp.typeface = Typeface.DEFAULT_BOLD
        var size = startSize
        tpp.textSize = size
        @Suppress("DEPRECATION")
        var lay = StaticLayout(s, tpp, maxW, Layout.Alignment.ALIGN_CENTER, 1.18f, 0f, false)
        while (lay.height > maxH && size > minSize) {
            size *= 0.94f
            tpp.textSize = size
            @Suppress("DEPRECATION")
            lay = StaticLayout(s, tpp, maxW, Layout.Alignment.ALIGN_CENTER, 1.18f, 0f, false)
        }
        if (wrapCache.size > 48) wrapCache.clear()
        wrapCache[key] = lay
        return lay
    }

    private fun drawWrapped(c: Canvas, lay: StaticLayout, x: Float, y: Float, color: Int, alpha: Int = 255, shadow: Int = 0) {
        lay.paint.color = color
        lay.paint.alpha = alpha.coerceIn(0, 255)
        if (shadow != 0) lay.paint.setShadowLayer(width * 0.01f, 0f, 0f, shadow) else lay.paint.clearShadowLayer()
        c.save()
        c.translate(x - lay.width / 2f, y - lay.height / 2f)
        lay.draw(c)
        c.restore()
    }

    // ---------------- icons (all drawn by code, no emoji fonts needed)
    private fun icoColor(): Int = ui(0xFF3E2D20.toInt(), 0xFFF2EEFF.toInt())

    private fun iconSound(c: Canvas, x: Float, y: Float, r: Float, ic: Int = icoColor()) {
        val u = r * 0.5f
        fill(ic)
        path.reset()
        path.moveTo(x - u * 1.0f, y - u * 0.38f)
        path.lineTo(x - u * 0.45f, y - u * 0.38f)
        path.lineTo(x + u * 0.1f, y - u * 0.85f)
        path.lineTo(x + u * 0.1f, y + u * 0.85f)
        path.lineTo(x - u * 0.45f, y + u * 0.38f)
        path.lineTo(x - u * 1.0f, y + u * 0.38f)
        path.close()
        c.drawPath(path, p)
        if (muted) {
            stroke(ic, r * 0.11f)
            c.drawLine(x + u * 0.5f, y - u * 0.4f, x + u * 1.1f, y + u * 0.4f, p)
            c.drawLine(x + u * 1.1f, y - u * 0.4f, x + u * 0.5f, y + u * 0.4f, p)
        } else {
            val n = if (volume < 0.34f) 1 else if (volume < 0.67f) 2 else 3
            stroke(ic, r * 0.10f)
            for (i in 0 until n) {
                val rr = u * (0.55f + 0.38f * i)
                rect.set(x + u * 0.1f - rr, y - rr, x + u * 0.1f + rr, y + rr)
                c.drawArc(rect, -45f, 90f, false, p)
            }
        }
    }

    private fun iconMoon(c: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.addCircle(x, y, r * 0.42f, Path.Direction.CW)
        path2.reset()
        path2.addCircle(x + r * 0.24f, y - r * 0.14f, r * 0.36f, Path.Direction.CW)
        path.op(path2, Path.Op.DIFFERENCE)
        fill(icoColor())
        c.drawPath(path, p)
    }

    private fun iconSun(c: Canvas, x: Float, y: Float, r: Float) {
        val ic = 0xFFFFD968.toInt()
        fill(ic)
        c.drawCircle(x, y, r * 0.24f, p)
        stroke(ic, r * 0.09f)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            c.drawLine(x + cos(a) * r * 0.38f, y + sin(a) * r * 0.38f, x + cos(a) * r * 0.54f, y + sin(a) * r * 0.54f, p)
        }
    }

    private fun iconBars(c: Canvas, x: Float, y: Float, r: Float) {
        fill(icoColor())
        val bw = r * 0.15f
        val hs = floatArrayOf(0.38f, 0.66f, 0.95f)
        for (i in 0..2) {
            val bx = x + (i - 1) * r * 0.36f
            val bottom = y + r * 0.42f
            rect.set(bx - bw, bottom - hs[i] * r * 0.9f, bx + bw, bottom)
            c.drawRoundRect(rect, bw, bw, p)
        }
    }

    private fun iconGear(c: Canvas, x: Float, y: Float, r: Float) {
        val ic = icoColor()
        stroke(ic, r * 0.17f)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            c.drawLine(x + cos(a) * r * 0.40f, y + sin(a) * r * 0.40f, x + cos(a) * r * 0.57f, y + sin(a) * r * 0.57f, p)
        }
        stroke(ic, r * 0.15f)
        c.drawCircle(x, y, r * 0.30f, p)
    }

    /** a ring of tasbih beads with the number of the active formula in the middle */
    private fun iconBeads(c: Canvas, x: Float, y: Float, r: Float) {
        val ic = icoColor()
        for (k in 0 until 9) {
            val a = k * 2f * PI.toFloat() / 9f - PI.toFloat() / 2f
            fill(if (k == 0) cAccent else alp(ic, 190))
            c.drawCircle(x + cos(a) * r * 0.60f, y + sin(a) * r * 0.60f, r * 0.095f, p)
        }
        text(c, arabic(formula + 1), x, y + r * 0.19f, r * 0.55f, ic)
    }

    private fun iconClose(c: Canvas, x: Float, y: Float, r: Float) {
        stroke(icoColor(), r * 0.12f)
        c.drawLine(x - r * 0.34f, y - r * 0.34f, x + r * 0.34f, y + r * 0.34f, p)
        c.drawLine(x + r * 0.34f, y - r * 0.34f, x - r * 0.34f, y + r * 0.34f, p)
    }

    private fun iconFlame(c: Canvas, x: Float, y: Float, r: Float, lit: Float) {
        glow(c, x, y, r * 2.2f, 255, 190, 90, (110 * lit).toInt())
        path.reset()
        path.moveTo(x, y - r)
        path.cubicTo(x + r * 0.95f, y - r * 0.25f, x + r * 0.85f, y + r * 0.85f, x, y + r)
        path.cubicTo(x - r * 0.85f, y + r * 0.85f, x - r * 0.95f, y - r * 0.25f, x, y - r)
        fill(0xFFFFFFFF.toInt(), (235 * lit).toInt())
        c.drawPath(path, p)
        path.reset()
        val i = r * 0.55f
        path.moveTo(x, y - i * 0.2f)
        path.cubicTo(x + i * 0.9f, y + i * 0.2f, x + i * 0.8f, y + i * 1.2f, x, y + i * 1.3f)
        path.cubicTo(x - i * 0.8f, y + i * 1.2f, x - i * 0.9f, y + i * 0.2f, x, y - i * 0.2f)
        fill(0xFFFF9A2E.toInt(), (255 * lit).toInt())
        c.drawPath(path, p)
    }

    // ---------------- reusable controls
    private fun roundBtn(c: Canvas, x: Float, y: Float, r: Float, active: Boolean) {
        val nt = night
        fill(0xFF000000.toInt(), 34)
        c.drawCircle(x, y + r * 0.13f, r, p)
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(
            0f, y - r, 0f, y + r,
            alp(ui(0xFFFFFFFF.toInt(), 0xFF3A4890.toInt()), (0xE6 - 0x26 * nt).toInt()),
            alp(ui(0xFFECF1F7.toInt(), 0xFF141C4A.toInt()), 0xB0), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, gp)
        gp.shader = null
        stroke(if (active) cAccent else 0xFFFFFFFF.toInt(), width * (if (active) 0.006f else 0.003f), if (active) 255 else (230 - 120 * nt).toInt())
        c.drawCircle(x, y, r, p)
    }

    private fun drawSlider(c: Canvas, l: Float, r: Float, y: Float, v: Float, u: Float) {
        val th = u * 0.026f
        stroke(ui(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), th, 38)
        c.drawLine(l, y, r, y, p)
        val kx = r - v * (r - l)
        if (v > 0.005f) {
            gp.style = Paint.Style.STROKE
            gp.strokeCap = Paint.Cap.ROUND
            gp.strokeWidth = th
            gp.shader = LinearGradient(kx, y, r, y, cBarA, cBarB, Shader.TileMode.CLAMP)
            c.drawLine(kx, y, r, y, gp)
            gp.shader = null
            gp.style = Paint.Style.FILL
        }
        fill(0xFF000000.toInt(), 50)
        c.drawCircle(kx, y + th * 0.5f, th * 1.55f, p)
        fill(0xFFFFFFFF.toInt())
        c.drawCircle(kx, y, th * 1.5f, p)
        stroke(cAccent, th * 0.38f)
        c.drawCircle(kx, y, th * 1.5f, p)
        fill(cAccent)
        c.drawCircle(kx, y, th * 0.5f, p)
    }

    private fun drawToggle(c: Canvas, cxx: Float, y: Float, wd: Float, on: Boolean) {
        val hh = wd * 0.52f
        rect.set(cxx - wd / 2f, y - hh / 2f, cxx + wd / 2f, y + hh / 2f)
        if (on) fill(cAccent) else fill(ui(0xFF9A9288.toInt(), 0xFF4A5380.toInt()))
        c.drawRoundRect(rect, hh / 2f, hh / 2f, p)
        val kx = if (on) rect.left + hh / 2f else rect.right - hh / 2f
        fill(0xFF000000.toInt(), 40)
        c.drawCircle(kx, y + hh * 0.06f, hh * 0.40f, p)
        fill(0xFFFFFFFF.toInt())
        c.drawCircle(kx, y, hh * 0.40f, p)
    }

    private fun chipRow(
        c: Canvas, n: Int, y: Float, rh: Float, texts: Array<String>, sel: Int,
        idBase: Int, active: Boolean, w: Float, u: Float, brown: Int
    ) {
        val padL = sh.left + w * 0.055f
        val padR = sh.right - w * 0.055f
        val gap = w * 0.02f
        val cw = (padR - padL - gap * (n - 1)) / n
        for (i in 0 until n) {
            val right = padR - i * (cw + gap)
            rect.set(right - cw, y - rh / 2f, right, y + rh / 2f)
            val on = i == sel
            if (on) {
                gp.style = Paint.Style.FILL
                gp.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, cBarA, cAccent, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, rh / 2f, rh / 2f, gp)
                gp.shader = null
            } else {
                fill(brown, 24)
                c.drawRoundRect(rect, rh / 2f, rh / 2f, p)
                stroke(brown, width * 0.0025f, 70)
                c.drawRoundRect(rect, rh / 2f, rh / 2f, p)
            }
            val sz = fit(texts[i], cw * 0.80f, 0.040f * u)
            text(c, texts[i], rect.centerX(), rect.centerY() + sz * 0.34f, sz, if (on) 0xFFFFFFFF.toInt() else brown)
            if (active) hitRect(rect, idBase + i)
        }
    }

    // ---------------- top row of five round buttons
    private fun drawTopButtons(c: Canvas, w: Float, h: Float, live: Boolean) {
        val br = min(w * 0.0575f, h * 0.0285f)
        val cy0 = h * 0.047f
        val gap = (w * 0.9f - 10f * br) / 4f
        val ids = intArrayOf(H_SETTINGS, H_STATS, H_NIGHT, H_SOUND, H_FORMULA)
        for (i in 0..4) {
            val x = w * 0.05f + br + i * (2f * br + gap)
            roundBtn(c, x, cy0, br, ids[i] == H_SOUND && volOpen)
            when (ids[i]) {
                H_SETTINGS -> iconGear(c, x, cy0, br)
                H_STATS -> iconBars(c, x, cy0, br)
                H_NIGHT -> if (night > 0.5f) iconSun(c, x, cy0, br) else iconMoon(c, x, cy0, br)
                H_SOUND -> iconSound(c, x, cy0, br)
                H_FORMULA -> iconBeads(c, x, cy0, br)
            }
            if (live) hit(x - br * 1.2f, cy0 - br * 1.25f, x + br * 1.2f, cy0 + br * 1.25f, ids[i])
        }
    }

    private fun drawVolPopup(c: Canvas, w: Float, h: Float, u: Float, live: Boolean) {
        val nt = night
        val ph = u * 0.15f
        rect.set(w * 0.07f, h * 0.083f, w * 0.93f, h * 0.083f + ph)
        val rad = ph / 2f
        fill(0xFF000000.toInt(), 40)
        rect.offset(0f, w * 0.01f)
        c.drawRoundRect(rect, rad, rad, p)
        rect.offset(0f, -w * 0.01f)
        fill(ui(0xFFFFFDF6.toInt(), 0xFF141C4A.toInt()), 244)
        c.drawRoundRect(rect, rad, rad, p)
        stroke(cAccent, w * 0.004f, 220)
        c.drawRoundRect(rect, rad, rad, p)
        val cy0 = rect.centerY()
        iconSound(c, rect.right - ph * 0.62f, cy0, ph * 0.40f)
        val sl = rect.left + w * 0.19f
        val sr = rect.right - ph * 1.10f
        sliderL[0] = sl
        sliderR[0] = sr
        drawSlider(c, sl, sr, cy0, volume, u)
        val brown = ui(0xFF3E2D20.toInt(), 0xFFCBC5E8.toInt())
        text(c, arabic((volume * 100f).roundToInt()) + "٪", rect.left + w * 0.095f, cy0 + u * 0.012f, 0.036f * u, brown)
        if (live) {
            hitRect(rect, H_VOLPOP)
            hit(sl - w * 0.05f, rect.top, sr + w * 0.05f, rect.bottom, H_VOLTRACK)
        }
        if (nt < 0f) return
    }

    // ---------------- the main glass panel
    private fun drawHud(c: Canvas, w: Float, h: Float, now: Long) {
        hitN = 0
        val nt = night
        val u = min(w, h * 0.455f)
        val t = now / 1000f
        val ink = ui(0xFF2B1D14.toInt(), 0xFFF7F2E6.toInt())
        val brown = ui(0xFF3E2D20.toInt(), 0xFFCBC5E8.toInt())
        val shadowC = if (nt > 0.5f) 0xAA000000.toInt() else 0xCCFFFFFF.toInt()
        val L = looks[formula]
        val ex = h * 0.085f * panelExtra
        val live = panel == 0

        drawTopButtons(c, w, h, live)

        // ---- glass panel
        val top = h * 0.085f
        val bottom = h * 0.342f + ex
        val prad = w * 0.055f
        rect.set(w * 0.05f, top, w * 0.95f, bottom)
        fill(0xFF000000.toInt(), 30)
        rect.offset(0f, w * 0.012f)
        c.drawRoundRect(rect, prad, prad, p)
        rect.offset(0f, -w * 0.012f)
        gp.style = Paint.Style.FILL
        val gc = ui(0xFFFFFFFF.toInt(), 0xFF0C1536.toInt())
        gp.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            alp(gc, (0x6A + (0xA4 - 0x6A) * nt).toInt()), alp(gc, (0x30 + (0x78 - 0x30) * nt).toInt()), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, prad, prad, gp)
        gp.shader = null
        stroke(0xFFFFFFFF.toInt(), w * 0.003f, (200 - 110 * nt).toInt())
        c.drawRoundRect(rect, prad, prad, p)
        // slow light sweep over the glass
        c.save()
        path.reset()
        path.addRoundRect(rect, prad, prad, Path.Direction.CW)
        c.clipPath(path)
        val bx = w * (-0.3f + (t * 0.18f) % 1.7f)
        path.reset()
        path.moveTo(bx, rect.top)
        path.lineTo(bx + w * 0.10f, rect.top)
        path.lineTo(bx + w * 0.10f - w * 0.08f, rect.bottom)
        path.lineTo(bx - w * 0.08f, rect.bottom)
        path.close()
        fill(0xFFFFFFFF.toInt(), 34)
        c.drawPath(path, p)
        c.restore()
        rect.inset(w * 0.009f, w * 0.009f)
        stroke(cAccent, w * 0.002f, 105)
        c.drawRoundRect(rect, prad * 0.85f, prad * 0.85f, p)

        // ---- small caption with ornament lines
        val subSize = 0.034f * u
        val subY = h * 0.113f
        val sw = mw(L.sub, subSize)
        tp.setShadowLayer(w * 0.01f, 0f, 0f, shadowC)
        text(c, L.sub, w * 0.5f, subY, subSize, brown)
        tp.clearShadowLayer()
        val ly = subY - subSize * 0.32f
        stroke(brown, u * 0.003f, 110)
        c.drawLine(w * 0.5f - sw / 2f - u * 0.035f, ly, w * 0.5f - sw / 2f - u * 0.15f, ly, p)
        c.drawLine(w * 0.5f + sw / 2f + u * 0.035f, ly, w * 0.5f + sw / 2f + u * 0.15f, ly, p)

        // ---- the phrase (wraps by itself, so long formulas never overlap anything)
        val pTop = h * 0.126f
        val pHt = h * (0.07f + (if (formula == 2) 0.085f else 0f))
        val pHa = h * 0.07f + ex
        val lay = wrapped(L.phrase, (w * 0.82f).toInt(), pHt, if (formula == 2) 0.062f * u else 0.125f * u, 0.026f * u)
        val breathe = 1f + 0.012f * sin(t * 1.6f) + 0.045f * pulse * pulse
        val pcy = pTop + pHa / 2f
        glow(
            c, w * 0.5f, pcy, w * 0.36f,
            if (nt > 0.5f) 150 else 255, if (nt > 0.5f) 170 else 226, if (nt > 0.5f) 255 else 140,
            (34f + 22f * sin(t * 1.6f) + 70f * pulse).toInt()
        )
        c.save()
        c.scale(breathe, breathe, w * 0.5f, pcy)
        drawWrapped(c, lay, w * 0.5f, pcy, ink, 255, shadowC)
        c.restore()

        // ---- counter  [max] / [count]
        val nBase = h * 0.243f + ex
        val num = arabic(count)
        val numSize = fit(num, w * 0.34f, 0.15f * u)
        val maxS = arabic(maxCount)
        val msz = 0.046f * u
        val gap = u * 0.028f
        val nW = mw(num, numSize)
        val sW = mw("/", msz)
        val mW = mw(maxS, msz)
        val total = mW + gap + sW + gap + nW
        val x0 = w * 0.5f - total / 2f
        tp.setShadowLayer(w * 0.01f, 0f, 0f, shadowC)
        text(c, maxS, x0, nBase, msz, brown, Paint.Align.LEFT)
        text(c, "/", x0 + mW + gap, nBase, msz, brown, Paint.Align.LEFT)
        val nx = x0 + mW + gap + sW + gap + nW / 2f
        val sc = 1f + 0.16f * pulse * pulse
        c.save()
        c.scale(sc, sc, nx, nBase - numSize * 0.35f)
        text(c, num, nx, nBase, numSize, lerpC(ink, 0xFFE0A82E.toInt(), pulse))
        c.restore()
        tp.clearShadowLayer()

        // ---- progress bar (fills right -> left)
        val left = 0.10f * w
        val right = 0.90f * w
        val by = h * 0.268f + ex
        stroke(0xFF000000.toInt(), w * 0.024f, 40)
        c.drawLine(left, by + w * 0.004f, right, by + w * 0.004f, p)
        stroke(ui(0xFFF5EFE0.toInt(), 0xFF3A4378.toInt()), w * 0.021f)
        c.drawLine(left, by, right, by, p)
        val fx = right - (shown / 100f) * (right - left)
        if (fx < right - 1f) {
            gp.style = Paint.Style.STROKE
            gp.strokeCap = Paint.Cap.ROUND
            gp.strokeWidth = w * 0.021f
            gp.shader = LinearGradient(fx, by, right, by, cBarA, cBarB, Shader.TileMode.CLAMP)
            c.drawLine(fx, by, right, by, gp)
            gp.shader = null
            gp.style = Paint.Style.FILL
            c.save()
            c.clipRect(fx, by - w * 0.02f, right, by + w * 0.02f)
            val span = right - left
            val sx = left + ((t * 0.45f) % 1.5f) * span * 1.3f - span * 0.2f
            stroke(0xFFFFFFFF.toInt(), w * 0.009f, 110)
            c.drawLine(sx, by - w * 0.002f, sx + w * 0.07f, by - w * 0.002f, p)
            c.restore()
            glow(c, fx, by, w * 0.045f, 255, 255, 255, (110f + 60f * sin(t * 4f)).toInt())
        }
        for (k in 0..3) {
            val mx = right - (k + 1) * 0.25f * (right - left)
            val reached = count >= milestone(k + 1)
            val age = (now - nodeAt[k]) / 650f
            val pop = if (age in 0f..1f) 1f + 0.7f * sin(age * PI.toFloat()) * (1f - age * 0.5f) else 1f
            if (reached) {
                fill(0xFFFFD968.toInt(), 90)
                c.drawCircle(mx, by, w * 0.036f * pop, p)
                val r2 = w * 0.033f
                rect.set(mx - r2, by - r2, mx + r2, by + r2)
                stroke(0xFFFFD968.toInt(), w * 0.003f, 210)
                c.drawArc(rect, t * 140f + k * 60f, 100f, false, p)
                c.drawArc(rect, t * 140f + k * 60f + 180f, 100f, false, p)
            }
            fill(if (reached) 0xFFE0A82E.toInt() else 0xFFFFFFFF.toInt())
            c.drawCircle(mx, by, w * 0.021f * pop, p)
            stroke(if (reached) 0xFFB9851A.toInt() else 0xFFBBBBBB.toInt(), w * 0.003f)
            c.drawCircle(mx, by, w * 0.021f * pop, p)
            if (reached) {
                fill(0xFFFFFFFF.toInt(), 230)
                c.drawCircle(mx, by, w * 0.007f * pop, p)
            }
            tp.setShadowLayer(w * 0.008f, 0f, 0f, shadowC)
            text(c, L.stages[k], mx, h * 0.298f + ex, 0.033f * u,
                if (reached) ui(0xFF8A6A1F.toInt(), 0xFFFFD968.toInt()) else brown)
            tp.clearShadowLayer()
        }

        // ---- quick stats line
        val mini = if (statStreak > 0)
            "اليوم  " + arabic(statToday) + "   •   السلسلة  " + arabic(statStreak)
        else "اليوم  " + arabic(statToday)
        val msize = fit(mini, w * 0.78f, 0.030f * u)
        text(c, mini, w * 0.5f, h * 0.323f + ex, msize, brown, alpha = 215)

        // ---- verse card (below the panel)
        val v = cardV
        if (v != null) {
            val age = (now - cardStart).toFloat()
            val done = count >= maxCount
            var a = cl(age / 300f)
            if (!done) a = min(a, cl((4500f - age) / 500f))
            if (!done && age > 4500f) cardV = null
            if (a > 0f) {
                val al = (255 * a).toInt()
                val ct = bottom + h * 0.014f
                val ch = h * 0.108f
                val slide = (1f - a) * h * 0.012f
                val csc = 0.82f + 0.18f * back(cl(age / 450f))
                val ccy = ct + ch / 2f
                c.save()
                c.scale(csc, csc, w * 0.5f, ccy)
                glow(c, w * 0.5f, ccy, w * 0.52f, 255, 214, 110, (70 * a).toInt())
                rect.set(w * 0.10f, ct + slide, w * 0.90f, ct + ch + slide)
                rect.offset(0f, w * 0.01f)
                fill(0xFF000000.toInt(), (60 * a).toInt())
                c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)
                rect.offset(0f, -w * 0.01f)
                gp.style = Paint.Style.FILL
                gp.shader = LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    Color.argb(al, 255, 252, 240), Color.argb(al, 255, 240, 205), Shader.TileMode.CLAMP
                )
                c.drawRoundRect(rect, w * 0.04f, w * 0.04f, gp)
                gp.shader = null
                val sf = cl(age / 1300f)
                if (sf < 1f) {
                    c.save()
                    path.reset()
                    path.addRoundRect(rect, w * 0.04f, w * 0.04f, Path.Direction.CW)
                    c.clipPath(path)
                    val kx = w * (0.05f + 0.90f * sf)
                    path.reset()
                    path.moveTo(kx, rect.top)
                    path.lineTo(kx + w * 0.10f, rect.top)
                    path.lineTo(kx + w * 0.10f - w * 0.06f, rect.bottom)
                    path.lineTo(kx - w * 0.06f, rect.bottom)
                    path.close()
                    fill(0xFFFFFFFF.toInt(), (150 * a).toInt())
                    c.drawPath(path, p)
                    c.restore()
                }
                stroke(0xFFD4A937.toInt(), w * 0.004f, al)
                c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)
                rect.inset(w * 0.012f, w * 0.012f)
                stroke(0xFFE8CC7A.toInt(), w * 0.0018f, al)
                c.drawRoundRect(rect, w * 0.03f, w * 0.03f, p)
                val vl = wrapped(v[0], (w * 0.68f).toInt(), ch * 0.58f, 0.066f * u, 0.030f * u)
                drawWrapped(c, vl, w * 0.5f, ct + ch * 0.40f + slide, 0xFF8A6A1F.toInt(), al)
                val rs = fit(v[1], w * 0.66f, 0.034f * u)
                text(c, v[1], w * 0.5f, ct + ch * 0.88f + slide, rs, 0xFF3E2D20.toInt(), alpha = al)
                c.restore()
            }
        }

        // ---- footer: logo name + developer credit
        vGrad(c, 0f, h * 0.915f, w, h, 0x00000000, 0x66000000)
        tp.setShadowLayer(w * 0.01f, 0f, 0f, Color.BLACK)
        text(c, "الاخوة للهواتف", w * 0.5f, h * 0.951f, w * 0.042f, 0xFFFFE08A.toInt(), alpha = 240)
        text(c, DEVELOPER, w * 0.5f, h * 0.977f, fit(DEVELOPER, w * 0.92f, w * 0.034f), Color.WHITE, alpha = 225)
        tp.clearShadowLayer()

        if (volOpen && live) drawVolPopup(c, w, h, u, live)

        if (sheetA > 0.01f) {
            if (panelDraw == 1) drawSettings(c, w, h, u, live = panel == 1)
            else drawStats(c, w, h, u, now, live = panel == 2)
        }
    }

    // ---------------- bottom sheets
    private fun sheetBase(c: Canvas, w: Float, h: Float, u: Float, title: String, live: Boolean) {
        val sa = sheetA
        fill(0xFF000000.toInt(), (170 * sa).toInt())
        c.drawRect(0f, 0f, w, h, p)
        if (live) hit(0f, 0f, w, h, H_DIM)
        sh.set(w * 0.04f, h * 0.07f, w * 0.96f, h * 0.93f)
        c.saveLayerAlpha(0f, 0f, w, h, (255 * cl(sa * 1.4f)).toInt())
        c.translate(0f, (1f - ease(sa)) * h * 0.06f)
        val rad = w * 0.06f
        fill(0xFF000000.toInt(), 70)
        sh.offset(0f, w * 0.014f)
        c.drawRoundRect(sh, rad, rad, p)
        sh.offset(0f, -w * 0.014f)
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(
            0f, sh.top, 0f, sh.bottom,
            ui(0xFFFFFDF4.toInt(), 0xFF141D47.toInt()), ui(0xFFF5E9CF.toInt(), 0xFF0A1030.toInt()), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(sh, rad, rad, gp)
        gp.shader = null
        stroke(cAccent, w * 0.004f, 200)
        c.drawRoundRect(sh, rad, rad, p)
        val ink = ui(0xFF2B1D14.toInt(), 0xFFF7F2E6.toInt())
        val ty = sh.top + sh.height() * 0.050f
        tp.setShadowLayer(w * 0.01f, 0f, 0f, if (night > 0.5f) 0xAA000000.toInt() else 0xCCFFFFFF.toInt())
        text(c, title, w * 0.5f, ty + u * 0.02f, 0.058f * u, ink)
        tp.clearShadowLayer()
        // ornament under the title
        val oy = ty + u * 0.07f
        stroke(cAccent, u * 0.004f, 200)
        c.drawLine(w * 0.5f - u * 0.18f, oy, w * 0.5f + u * 0.18f, oy, p)
        fill(cAccent)
        c.save()
        c.rotate(45f, w * 0.5f, oy)
        c.drawRect(w * 0.5f - u * 0.011f, oy - u * 0.011f, w * 0.5f + u * 0.011f, oy + u * 0.011f, p)
        c.restore()
        // close button
        val cr = u * 0.05f
        val ccx = sh.left + w * 0.095f
        val ccy = ty
        roundBtn(c, ccx, ccy, cr, false)
        iconClose(c, ccx, ccy, cr)
        if (live) {
            hit(sh.left, sh.top, sh.right, sh.bottom, H_SHEET)
            hit(ccx - cr * 1.5f, ccy - cr * 1.5f, ccx + cr * 1.5f, ccy + cr * 1.5f, H_CLOSE)
        }
    }

    private fun sheetEnd(c: Canvas) {
        c.restore()
    }

    private fun sectionLabel(c: Canvas, txt: String, y: Float, u: Float) {
        val col = lerpC(cAccent, 0xFFFFFFFF.toInt(), night * 0.45f)
        val x = sh.right - width * 0.058f
        text(c, txt, x, y, 0.038f * u, col, Paint.Align.RIGHT)
        val tw = mw(txt, 0.038f * u)
        stroke(col, u * 0.0035f, 110)
        c.drawLine(x - tw - u * 0.03f, y - u * 0.013f, sh.left + width * 0.058f, y - u * 0.013f, p)
    }

    private fun drawSettings(c: Canvas, w: Float, h: Float, u: Float, live: Boolean) {
        sheetBase(c, w, h, u, "الإعدادات", live)
        val SH = sh.height()
        val ink = ui(0xFF2B1D14.toInt(), 0xFFF7F2E6.toInt())
        val brown = ui(0xFF3E2D20.toInt(), 0xFFCBC5E8.toInt())
        val padL = sh.left + w * 0.055f
        val padR = sh.right - w * 0.055f
        val rh = SH * 0.054f
        fun yy(f: Float) = sh.top + SH * f

        // volume
        sectionLabel(c, "مستوى الصوت", yy(0.118f), u)
        val vy = yy(0.165f)
        val sl = sh.left + w * 0.17f
        val sr = sh.right - w * 0.075f
        sliderL[1] = sl
        sliderR[1] = sr
        drawSlider(c, sl, sr, vy, volume, u)
        text(c, arabic((volume * 100f).roundToInt()) + "٪", sh.left + w * 0.095f, vy + u * 0.013f, 0.036f * u, brown)
        if (live) hit(sl - w * 0.05f, vy - SH * 0.03f, sr + w * 0.05f, vy + SH * 0.03f, H_SET_VOL)

        // formulas
        sectionLabel(c, "الصيغة", yy(0.228f), u)
        for (i in 0..2) {
            val y = yy(0.285f + i * 0.063f)
            val Li = looks[i]
            val sel = i == formula
            rect.set(padL, y - rh / 2f, padR, y + rh / 2f)
            fill(Li.accent, if (sel) 48 else 14)
            c.drawRoundRect(rect, rh * 0.5f, rh * 0.5f, p)
            stroke(if (sel) Li.accent else brown, w * 0.003f, if (sel) 255 else 60)
            c.drawRoundRect(rect, rh * 0.5f, rh * 0.5f, p)
            val rx0 = padR - rh * 0.55f
            stroke(Li.accent, w * 0.0035f)
            c.drawCircle(rx0, y, rh * 0.22f, p)
            if (sel) {
                fill(Li.accent)
                c.drawCircle(rx0, y, rh * 0.12f, p)
            }
            val tsz = fit(Li.label, rect.width() * 0.56f, 0.040f * u)
            text(c, Li.label, rx0 - rh * 0.42f, y + tsz * 0.33f, tsz, ink, Paint.Align.RIGHT)
            val cnt = if (sel) count else prefs.getInt(countKey(i), 0)
            text(c, arabic(cnt) + " / " + arabic(maxCount), rect.left + rh * 0.45f, y + u * 0.011f, 0.029f * u, brown, Paint.Align.LEFT, 200)
            if (live) hitRect(rect, H_F0 + i)
        }

        // limit
        sectionLabel(c, "الحد", yy(0.482f), u)
        chipRow(c, 4, yy(0.536f), rh, Array(4) { arabic(MAX_OPTIONS[it]) }, MAX_OPTIONS.indexOf(maxCount), H_LIM0, live, w, u, brown)

        // look
        sectionLabel(c, "المظهر", yy(0.600f), u)
        chipRow(c, 3, yy(0.654f), rh, arrayOf("تلقائي", "نهاري", "ليلي"), themeMode, H_TH0, live, w, u, brown)

        // reminders
        sectionLabel(c, "التذكير اليومي", yy(0.718f), u)
        for (i in 0..2) {
            val y = yy(0.776f + i * 0.066f)
            val on = prefs.getBoolean("rem_on$i", false)
            val hh = prefs.getInt("rem_h$i", Reminders.DEF_H[i])
            val mm = prefs.getInt("rem_m$i", Reminders.DEF_M[i])
            rect.set(padL, y - rh * 0.52f, padR, y + rh * 0.52f)
            fill(brown, if (on) 26 else 12)
            c.drawRoundRect(rect, rh * 0.5f, rh * 0.5f, p)
            val nsz = fit(Reminders.NAMES[i], w * 0.24f, 0.040f * u)
            text(c, Reminders.NAMES[i], padR - rh * 0.35f, y + nsz * 0.33f, nsz, if (on) ink else brown, Paint.Align.RIGHT)
            // time pill (tap to change)
            val tr = RectF(padL + w * 0.16f, y - rh * 0.40f, padL + w * 0.47f, y + rh * 0.40f)
            fill(cAccent, if (on) 55 else 22)
            c.drawRoundRect(tr, tr.height() / 2f, tr.height() / 2f, p)
            stroke(cAccent, w * 0.0028f, if (on) 255 else 110)
            c.drawRoundRect(tr, tr.height() / 2f, tr.height() / 2f, p)
            val ts = fmtTime(hh, mm)
            val tsz = fit(ts, tr.width() * 0.8f, 0.036f * u)
            text(c, ts, tr.centerX(), tr.centerY() + tsz * 0.34f, tsz, if (on) ink else brown)
            drawToggle(c, padL + w * 0.075f, y, w * 0.115f, on)
            if (live) {
                hit(tr.left, tr.top - rh * 0.1f, tr.right, tr.bottom + rh * 0.1f, H_REM_T0 + i)
                hit(padL, y - rh * 0.5f, padL + w * 0.14f, y + rh * 0.5f, H_REM_S0 + i)
            }
        }
        text(c, "تنبيهات هادئة بلا صوت مزعج • اضغط على الوقت لتغييره", w * 0.5f, yy(0.962f),
            fit("تنبيهات هادئة بلا صوت مزعج • اضغط على الوقت لتغييره", w * 0.84f, 0.028f * u), brown, alpha = 190)
        sheetEnd(c)
    }

    private fun statCard(c: Canvas, r: RectF, title: String, value: String, unit: String, u: Float, ink: Int, brown: Int) {
        val rad = u * 0.05f
        fill(0xFF000000.toInt(), 22)
        r.offset(0f, u * 0.006f)
        c.drawRoundRect(r, rad, rad, p)
        r.offset(0f, -u * 0.006f)
        fill(ui(0xFFFFFFFF.toInt(), 0xFF1C2656.toInt()), 235)
        c.drawRoundRect(r, rad, rad, p)
        stroke(cAccent, u * 0.003f, 140)
        c.drawRoundRect(r, rad, rad, p)
        val rhh = r.height()
        text(c, title, r.centerX(), r.top + rhh * 0.27f, 0.036f * u, brown)
        val vs = fit(value, r.width() * 0.78f, 0.092f * u)
        text(c, value, r.centerX(), r.top + rhh * 0.67f, vs, ink)
        text(c, unit, r.centerX(), r.top + rhh * 0.89f, 0.028f * u, brown, alpha = 200)
    }

    private fun streakLabel(n: Int): String = when {
        n <= 0 -> "ابدأ سلسلتك اليوم"
        n == 1 -> "يوم متتالٍ"
        n == 2 -> "يومان متتاليان"
        n <= 10 -> "أيام متتالية"
        else -> "يومًا متتاليًا"
    }

    private fun streakMsg(n: Int): String = when {
        n <= 0 -> "أول استغفار اليوم يفتح لك باب السلسلة"
        n == 1 -> "بداية موفّقة! عُد غدًا لتكمل سلسلتك"
        n < 7 -> "ما شاء الله! ثباتك يزداد يومًا بعد يوم"
        n < 30 -> "أحبّ الأعمال إلى الله أدومها وإن قلّ"
        else -> "شهرٌ من المداومة… ثبّتك الله وتقبّل منك"
    }

    private fun drawStats(c: Canvas, w: Float, h: Float, u: Float, now: Long, live: Boolean) {
        sheetBase(c, w, h, u, "إحصائياتك", live)
        val SH = sh.height()
        val ink = ui(0xFF2B1D14.toInt(), 0xFFF7F2E6.toInt())
        val brown = ui(0xFF3E2D20.toInt(), 0xFFCBC5E8.toInt())
        val padL = sh.left + w * 0.055f
        val padR = sh.right - w * 0.055f
        val t = now / 1000f
        val an = ease((now - panelOpenedAt) / 900f)
        fun yy(f: Float) = sh.top + SH * f
        val rad = u * 0.055f

        // --- hero: streak
        val hero = RectF(padL, yy(0.095f), padR, yy(0.285f))
        fill(0xFF000000.toInt(), 30)
        hero.offset(0f, u * 0.008f)
        c.drawRoundRect(hero, rad, rad, p)
        hero.offset(0f, -u * 0.008f)
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(
            hero.left, hero.top, hero.right, hero.bottom,
            ui(0xFFFFB347.toInt(), 0xFF8B5CE0.toInt()), ui(0xFFE8602B.toInt(), 0xFF3F2A9C.toInt()), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(hero, rad, rad, gp)
        gp.shader = null
        c.save()
        path.reset()
        path.addRoundRect(hero, rad, rad, Path.Direction.CW)
        c.clipPath(path)
        val shx = hero.left + ((t * 0.22f) % 1.6f - 0.3f) * hero.width()
        path.reset()
        path.moveTo(shx, hero.top)
        path.lineTo(shx + w * 0.10f, hero.top)
        path.lineTo(shx + w * 0.02f, hero.bottom)
        path.lineTo(shx - w * 0.08f, hero.bottom)
        path.close()
        fill(0xFFFFFFFF.toInt(), 36)
        c.drawPath(path, p)
        c.restore()
        val hh = hero.height()
        val fr = hh * 0.27f
        val flick = 1f + 0.05f * sin(t * 7f) + 0.03f * sin(t * 11f)
        iconFlame(c, hero.right - hh * 0.46f, hero.top + hh * 0.46f, fr * flick, if (statStreak > 0) 1f else 0.5f)
        val colX = hero.left + (hero.width() - hh * 0.9f) / 2f
        val sn = arabic((statStreak * an).roundToInt())
        tp.setShadowLayer(u * 0.01f, 0f, u * 0.004f, 0x66000000)
        text(c, sn, colX, hero.top + hh * 0.46f, fit(sn, hero.width() * 0.4f, 0.135f * u), 0xFFFFFFFF.toInt())
        text(c, streakLabel(statStreak), colX, hero.top + hh * 0.64f, fit(streakLabel(statStreak), hero.width() * 0.58f, 0.040f * u), 0xFFFFF4DC.toInt())
        tp.clearShadowLayer()
        val msg = streakMsg(statStreak)
        text(c, msg, hero.centerX(), hero.top + hh * 0.88f, fit(msg, hero.width() * 0.90f, 0.031f * u), 0xFFFFFFFF.toInt(), alpha = 235)

        // --- today / week
        val cw = (padR - padL - w * 0.03f) / 2f
        val cardT = yy(0.305f)
        val cardB = yy(0.455f)
        rect.set(padR - cw, cardT, padR, cardB)
        statCard(c, rect, "اليوم", arabic((statToday * an).roundToInt()), "استغفار", u, ink, brown)
        rect.set(padL, cardT, padL + cw, cardB)
        statCard(c, rect, "هذا الأسبوع", arabic((statWeek * an).roundToInt()), "استغفار", u, ink, brown)

        // --- total
        val tot = RectF(padL, yy(0.475f), padR, yy(0.565f))
        fill(ui(0xFFFFFFFF.toInt(), 0xFF1C2656.toInt()), 235)
        c.drawRoundRect(tot, rad, rad, p)
        stroke(cAccent, u * 0.003f, 140)
        c.drawRoundRect(tot, rad, rad, p)
        text(c, "الإجمالي منذ بدايتك", tot.right - u * 0.045f, tot.centerY() + u * 0.014f, 0.038f * u, brown, Paint.Align.RIGHT)
        val tv = arabic((totalAll.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() * an).roundToInt())
        text(c, tv, tot.left + u * 0.05f, tot.centerY() + u * 0.03f, fit(tv, tot.width() * 0.4f, 0.078f * u), ink, Paint.Align.LEFT)

        // --- 7-day chart
        val ch = RectF(padL, yy(0.585f), padR, yy(0.945f))
        fill(ui(0xFFFFFFFF.toInt(), 0xFF1C2656.toInt()), 235)
        c.drawRoundRect(ch, rad, rad, p)
        stroke(cAccent, u * 0.003f, 140)
        c.drawRoundRect(ch, rad, rad, p)
        text(c, "آخر ٧ أيام", ch.right - u * 0.045f, ch.top + ch.height() * 0.11f, 0.038f * u, brown, Paint.Align.RIGHT)
        var mx = 1
        for (v in barVals) if (v > mx) mx = v
        val col = (ch.width() - u * 0.08f) / 7f
        val baseY = ch.bottom - ch.height() * 0.16f
        val areaH = ch.height() * 0.56f
        val bwid = col * 0.50f
        for (i in 0..6) {
            val bcx = ch.right - u * 0.04f - (i + 0.5f) * col
            val v = barVals[i]
            val frac = if (v <= 0) 0.03f else max(0.06f, v.toFloat() / mx)
            val bh = areaH * frac * an
            rect.set(bcx - bwid / 2f, baseY - bh, bcx + bwid / 2f, baseY)
            val today = i == 6
            if (v <= 0) {
                fill(brown, 40)
                c.drawRoundRect(rect, bwid / 2f, bwid / 2f, p)
            } else {
                gp.style = Paint.Style.FILL
                gp.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, if (today) 0xFFFFD968.toInt() else cBarA, if (today) 0xFFE0A82E.toInt() else cBarB, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, bwid / 2f, bwid / 2f, gp)
                gp.shader = null
                text(c, arabic(v), bcx, rect.top - u * 0.016f, fit(arabic(v), col * 0.95f, 0.030f * u), ink)
            }
            text(c, barLbl[i], bcx, ch.bottom - ch.height() * 0.05f, fit(barLbl[i], col * 0.98f, 0.027f * u), if (today) ink else brown, alpha = if (today) 255 else 200)
        }
        sheetEnd(c)
    }

    // ---------------- night scenery
    private fun drawNightSky(c: Canvas, w: Float, h: Float, t: Float, nt: Float, cam: Float) {
        val skyH = h * 0.40f
        for (i in 0 until starN) {
            val x = starX[i] * w + cam * w * 0.004f
            val y = starY[i] * skyH
            val tw = 0.55f + 0.45f * sin(t * (0.8f + starS[i] * 1.4f) + starX[i] * 40f)
            val al = (235f * nt * tw * (0.35f + 0.65f * starS[i])).toInt()
            fill(0xFFFFFAE8.toInt(), al)
            c.drawCircle(x, y, w * (0.0014f + 0.0026f * starS[i]), p)
            if (starS[i] > 0.82f) {
                stroke(0xFFFFFAE8.toInt(), w * 0.0014f, al / 2)
                c.drawLine(x - w * 0.007f, y, x + w * 0.007f, y, p)
                c.drawLine(x, y - w * 0.007f, x, y + w * 0.007f, p)
            }
        }
        // crescent moon with a soft halo
        val mx = w * 0.835f + cam * w * 0.006f
        val my = h * 0.205f
        val mr = w * 0.062f
        glow(c, mx, my, mr * 6f, 150, 175, 255, (60 * nt).toInt())
        glow(c, mx, my, mr * 2.6f, 235, 240, 255, (120 * nt).toInt())
        fill(0xFFB9C4F0.toInt(), (34 * nt).toInt())
        c.drawCircle(mx, my, mr, p)
        path.reset()
        path.addCircle(mx, my, mr, Path.Direction.CW)
        path2.reset()
        path2.addCircle(mx - mr * 0.46f, my - mr * 0.18f, mr * 0.86f, Path.Direction.CW)
        path.op(path2, Path.Op.DIFFERENCE)
        fill(0xFFFFF6D8.toInt(), (255 * nt).toInt())
        c.drawPath(path, p)
        // a shooting star now and then
        val cyc = t % 13f
        if (cyc < 1.1f) {
            val f = cyc / 1.1f
            val sx = w * (0.14f + 0.28f * ((t / 13f).toInt() % 3)) + w * 0.22f * f
            val sy = h * 0.05f + h * 0.05f * f
            stroke(0xFFFFFFFF.toInt(), w * 0.003f, (230 * (1f - f) * nt).toInt())
            c.drawLine(sx - w * 0.10f, sy - h * 0.02f, sx, sy, p)
        }
    }

    private fun drawNightOverlay(c: Canvas, w: Float, h: Float, t: Float, nt: Float, pcx: Float, pcy: Float, rx: Float, ry: Float) {
        val top = h * 0.30f
        val a = (118 * nt).toInt()
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(0f, top, 0f, top + h * 0.05f, Color.argb(0, 8, 14, 40), Color.argb(a, 8, 14, 40), Shader.TileMode.CLAMP)
        c.drawRect(0f, top, w, top + h * 0.05f, gp)
        gp.shader = null
        fill(0xFF080E28.toInt(), a)
        c.drawRect(0f, top + h * 0.05f, w, h, p)
        // moonlight on the pond
        val mxp = pcx + rx * 0.35f
        glow(c, mxp, pcy, rx * 0.7f, 190, 210, 255, (46 * nt).toInt())
        for (k in 0..5) {
            val y = pcy - ry * 0.5f + k * ry * 0.2f
            val len = rx * (0.28f - k * 0.03f)
            val wob = sin(t * 1.3f + k * 1.4f) * rx * 0.05f
            stroke(0xFFEAF0FF.toInt(), w * 0.003f, (150 * nt * (1f - k * 0.12f)).toInt())
            c.drawLine(mxp - len + wob, y, mxp + len + wob, y, p)
        }
    }

    // ---------------- per-frame state (theme, palette, panels)
    private fun updateLook(dt: Float, now: Long) {
        if (now - themeAutoCheck > 20000L) {
            themeAutoCheck = now
            val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            autoNight = hr >= 19 || hr < 5
            refreshStats()
        }
        val nTarget = when (themeMode) {
            1 -> 0f
            2 -> 1f
            else -> if (autoNight) 1f else 0f
        }
        night += (nTarget - night) * min(1f, dt * 2.2f)
        val L = looks[formula]
        val k = min(1f, dt * 3.5f)
        cAccent = lerpC(cAccent, L.accent, k)
        cBarA = lerpC(cBarA, L.barA, k)
        cBarB = lerpC(cBarB, L.barB, k)
        cDropA = lerpC(cDropA, L.dropA, k)
        cDropB = lerpC(cDropB, L.dropB, k)
        cPondA = lerpC(cPondA, L.pondA, k)
        cPondB = lerpC(cPondB, L.pondB, k)
        cTint = lerpC(cTint, L.tint, k)
        cTintA += (L.tintA - cTintA) * k
        panelExtra += ((if (formula == 2) 1f else 0f) - panelExtra) * min(1f, dt * 6f)
        sheetA += ((if (panel != 0) 1f else 0f) - sheetA) * min(1f, dt * 9f)
        if (volOpen && dragId == 0 && now - volOpenAt > 3200L) volOpen = false
    }

    // ---------------- stats storage
    private fun keyOf(cal: Calendar): Int =
        cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)

    private fun keyDaysAgo(n: Int): Int {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return keyOf(cal)
    }

    private fun loadDaily() {
        val s = prefs.getString("daily", "") ?: ""
        for (part in s.split(';')) {
            val kv = part.split(':')
            if (kv.size == 2) {
                val k = kv[0].toIntOrNull()
                val v = kv[1].toIntOrNull()
                if (k != null && v != null) daily[k] = v
            }
        }
    }

    private fun dailyString(): String =
        daily.keys.sorted().takeLast(200).joinToString(";") { "$it:${daily[it]}" }

    private fun refreshStats() {
        statToday = daily[keyDaysAgo(0)] ?: 0
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % 7 // Saturday = 0 ... Friday = 6
        var wk = 0
        for (i in 0..dow) wk += daily[keyDaysAgo(i)] ?: 0
        statWeek = wk
        var st = 0
        var i = if (statToday > 0) 0 else 1
        while (i < 200 && (daily[keyDaysAgo(i)] ?: 0) > 0) {
            st++
            i++
        }
        statStreak = st
        for (j in 0..6) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(6 - j))
            barVals[j] = daily[keyOf(cal)] ?: 0
            barLbl[j] = dayNames[cal.get(Calendar.DAY_OF_WEEK)]
        }
    }

    private fun recordStat() {
        val k = keyDaysAgo(0)
        daily[k] = (daily[k] ?: 0) + 1
        totalAll++
        refreshStats()
    }

    // ---------------- actions
    private fun panelBottomY(): Float = height * (0.342f + 0.085f * panelExtra)

    private fun openPanel(which: Int) {
        panel = which
        panelDraw = which
        panelOpenedAt = SystemClock.uptimeMillis()
        volOpen = false
        refreshStats()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun closePanel() {
        panel = 0
        save()
    }

    private fun switchFormula(i: Int) {
        if (i == formula) return
        prefs.edit().putInt(countKey(formula), count).apply()
        formula = i
        count = prefs.getInt(countKey(i), 0).coerceIn(0, maxCount)
        cardV = null
        drops.clear()
        val now = SystemClock.uptimeMillis()
        waves.add(Wave(now))
        play(chimeId, 1f, CHIME_VOL * 0.7f)
        burst(width * 0.5f, height * 0.2f, 22)
        save()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun setMax(n: Int) {
        if (n == maxCount) return
        maxCount = n
        if (count >= maxCount) count = 0
        cardV = null
        save()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun setVolFromX(x: Float, idx: Int) {
        val l = sliderL[idx]
        val r = sliderR[idx]
        if (r - l < 1f) return
        volume = cl((r - x) / (r - l))
    }

    private fun toggleNight() {
        themeMode = if (night > 0.5f) 1 else 2
        save()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun pickTime(i: Int) {
        val h0 = prefs.getInt("rem_h$i", Reminders.DEF_H[i])
        val m0 = prefs.getInt("rem_m$i", Reminders.DEF_M[i])
        try {
            TimePickerDialog(context, TimePickerDialog.OnTimeSetListener { _, hh, mm ->
                prefs.edit().putInt("rem_h$i", hh).putInt("rem_m$i", mm).putBoolean("rem_on$i", true).apply()
                Reminders.schedule(context, i)
                (context as? MainActivity)?.askNotif()
            }, h0, m0, false).show()
        } catch (e: Exception) {
        }
    }

    private fun toggleReminder(i: Int) {
        val on = !prefs.getBoolean("rem_on$i", false)
        prefs.edit().putBoolean("rem_on$i", on).apply()
        if (on) {
            Reminders.schedule(context, i)
            (context as? MainActivity)?.askNotif()
        } else {
            Reminders.cancel(context, i)
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun panelHit(id: Int, x: Float) {
        when (id) {
            H_CLOSE, H_DIM, -1 -> closePanel()
            H_SET_VOL -> {
                dragId = H_SET_VOL
                setVolFromX(x, 1)
            }
            in H_F0..H_F0 + 2 -> switchFormula(id - H_F0)
            in H_LIM0..H_LIM0 + 3 -> setMax(MAX_OPTIONS[id - H_LIM0])
            in H_TH0..H_TH0 + 2 -> {
                themeMode = id - H_TH0
                save()
            }
            in H_REM_T0..H_REM_T0 + 2 -> pickTime(id - H_REM_T0)
            in H_REM_S0..H_REM_S0 + 2 -> toggleReminder(id - H_REM_S0)
            else -> {}
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val tnow = SystemClock.uptimeMillis()
        if (tnow - born < introMs) {
            // tap the intro to skip it (after the logo has appeared)
            if (e.actionMasked == MotionEvent.ACTION_DOWN && tnow - born > 1600 && tnow - born < introMs - 800) {
                born = tnow - introMs + 800
            }
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val id = hitAt(e.x, e.y)
                if (panel != 0) {
                    panelHit(id, e.x)
                    return true
                }
                if (volOpen) {
                    if (id == H_VOLTRACK) {
                        dragId = H_VOLTRACK
                        volOpenAt = tnow
                        setVolFromX(e.x, 0)
                        return true
                    }
                    if (id == H_VOLPOP) return true
                    if (id != H_SOUND) {
                        volOpen = false
                        if (id == -1) return true
                    }
                }
                when (id) {
                    H_FORMULA -> switchFormula((formula + 1) % 3)
                    H_SOUND -> {
                        volOpen = !volOpen
                        volOpenAt = tnow
                    }
                    H_NIGHT -> toggleNight()
                    H_STATS -> openPanel(2)
                    H_SETTINGS -> openPanel(1)
                    else -> {
                        tap(e.x, e.y)
                        postDelayed(resetRun, 2500) // hold 2.5s to reset
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragId == H_VOLTRACK) {
                    volOpenAt = tnow
                    setVolFromX(e.x, 0)
                } else if (dragId == H_SET_VOL) {
                    setVolFromX(e.x, 1)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (panel == 0 && !volOpen && hitAt(e.getX(e.actionIndex), e.getY(e.actionIndex)) == -1) {
                    tap(e.getX(e.actionIndex), e.getY(e.actionIndex))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(resetRun)
                if (dragId != 0) {
                    dragId = 0
                    save()
                    play(dropId, 1f, DROP_VOL) // preview of the new level
                }
            }
        }
        return true
    }
}
