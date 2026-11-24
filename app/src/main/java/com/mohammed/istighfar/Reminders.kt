package com.mohammed.istighfar

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.Random

/** Daily quiet reminders: after Fajr, after Asr and before sleep (times are editable). */
object Reminders {
    val DEF_H = intArrayOf(5, 16, 22)
    val DEF_M = intArrayOf(30, 30, 0)
    val NAMES = arrayOf("بعد الفجر", "بعد العصر", "قبل النوم")
    const val CHANNEL = "istighfar_reminders"

    private val TITLES = arrayOf("صباحٌ مبارك", "وقتُ استغفار", "ختام يومك")
    private val TEXTS = arrayOf(
        arrayOf(
            "ابدأ يومك بـ «أستغفر الله» وانظر كيف تُفتح لك الأبواب",
            "دقيقة استغفار بعد الفجر تملأ يومك سكينة",
            "اللهم اغفر لنا… ابدأ صباحك بالاستغفار"
        ),
        arrayOf(
            "استراحة هادئة… قُل «أستغفر الله» ولو مئة مرة",
            "أنهار وجنات ومطر… بالاستغفار يكثر الخير",
            "لحظة استغفار في آخر النهار"
        ),
        arrayOf(
            "اختم يومك بالاستغفار وأنت مطمئن القلب",
            "قبل أن تنام… استغفر ربك إنه كان غفارًا",
            "ليلة هادئة تبدأ بـ «أستغفر الله وأتوب إليه»"
        )
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("istighfar", Context.MODE_PRIVATE)

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun alarmIntent(ctx: Context, slot: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("slot", slot)
        return PendingIntent.getBroadcast(ctx, 7100 + slot, i, flags())
    }

    fun isOn(ctx: Context, slot: Int) = prefs(ctx).getBoolean("rem_on$slot", false)

    fun schedule(ctx: Context, slot: Int) {
        if (!isOn(ctx, slot)) {
            cancel(ctx, slot)
            return
        }
        val p = prefs(ctx)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, p.getInt("rem_h$slot", DEF_H[slot]))
        cal.set(Calendar.MINUTE, p.getInt("rem_m$slot", DEF_M[slot]))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(ctx, slot)
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: Exception) {
        }
    }

    fun cancel(ctx: Context, slot: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(ctx, slot))
    }

    fun rescheduleAll(ctx: Context) {
        for (i in 0..2) schedule(ctx, i)
    }

    fun show(ctx: Context, slot: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "تذكير الاستغفار", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "تذكير يومي هادئ بالاستغفار"
            ch.setSound(null, null)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            ctx, 7200 + slot,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags()
        )
        val text = TEXTS[slot][Random().nextInt(TEXTS[slot].size)]
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(TITLES[slot])
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        nm.notify(7300 + slot, b.build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra("slot", -1)
        if (slot !in 0..2) return
        if (Reminders.isOn(context, slot)) {
            Reminders.show(context, slot)
            Reminders.schedule(context, slot) // next day
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.rescheduleAll(context)
    }
}
