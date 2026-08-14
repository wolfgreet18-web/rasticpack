package com.example.cardwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

class CardWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_COPY = "com.example.cardwidget.ACTION_COPY_CARD"
        const val PREFS_NAME = "com.example.cardwidget.CardWidgetProvider"
        const val PREF_KEY_NUMBER = "card_number_"
        const val PREF_KEY_LABEL = "card_label_"

        fun saveCardData(context: Context, appWidgetId: Int, label: String, number: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            prefs.putString(PREF_KEY_LABEL + appWidgetId, label)
            prefs.putString(PREF_KEY_NUMBER + appWidgetId, number)
            prefs.apply()
        }

        fun loadCardLabel(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_KEY_LABEL + appWidgetId, "کارت من") ?: "کارت من"
        }

        fun loadCardNumber(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_KEY_NUMBER + appWidgetId, "") ?: ""
        }

        fun deleteCardData(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            prefs.remove(PREF_KEY_LABEL + appWidgetId)
            prefs.remove(PREF_KEY_NUMBER + appWidgetId)
            prefs.apply()
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val label = loadCardLabel(context, appWidgetId)
            val number = loadCardNumber(context, appWidgetId)
            val display = if (number.length >= 4) "**** ${number.takeLast(4)}" else number

            val views = RemoteViews(context.packageName, R.layout.card_widget)
            views.setTextViewText(R.id.widget_label, label)
            views.setTextViewText(R.id.widget_number, display)

            val intent = Intent(context, CardWidgetProvider::class.java).apply {
                action = ACTION_COPY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COPY) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val number = loadCardNumber(context, appWidgetId)
                if (number.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("card_number", number)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "شماره کارت کپی شد", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            deleteCardData(context, id)
        }
    }
}
