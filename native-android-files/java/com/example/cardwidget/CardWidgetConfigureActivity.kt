package com.example.cardwidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import org.json.JSONArray
import org.json.JSONObject

class CardWidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var listView: ListView
    private lateinit var cards: MutableList<JSONObject>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_configure)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        listView = findViewById(R.id.list_cards)
        val btnAdd: Button = findViewById(R.id.btn_add_card)

        cards = loadCards()
        refreshList()

        btnAdd.setOnClickListener { showAddCardDialog() }
    }

    private fun loadCards(): MutableList<JSONObject> {
        val prefs = getSharedPreferences("com.example.cardwidget.Cards", Context.MODE_PRIVATE)
        val json = prefs.getString("cards_json", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            list.add(arr.getJSONObject(i))
        }
        return list
    }

    private fun saveCards() {
        val arr = JSONArray()
        for (c in cards) arr.put(c)
        val prefs = getSharedPreferences("com.example.cardwidget.Cards", Context.MODE_PRIVATE)
        prefs.edit().putString("cards_json", arr.toString()).apply()
    }

    private fun refreshList() {
        val labels = cards.map { "${it.getString("label")} — ${maskNumber(it.getString("number"))}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val card = cards[position]
            finishConfig(card.getString("label"), card.getString("number"))
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            cards.removeAt(position)
            saveCards()
            refreshList()
            true
        }
    }

    private fun maskNumber(number: String): String {
        return if (number.length >= 4) "**** ${number.takeLast(4)}" else number
    }

    private fun showAddCardDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, padding)

        val labelInput = EditText(this)
        labelInput.hint = "عنوان (مثلا: کارت من، کارت علی)"
        container.addView(labelInput)

        val numberInput = EditText(this)
        numberInput.hint = "شماره کارت"
        numberInput.inputType = InputType.TYPE_CLASS_NUMBER
        container.addView(numberInput)

        AlertDialog.Builder(this)
            .setTitle("افزودن کارت جدید")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val label = labelInput.text.toString().trim().ifEmpty { "کارت" }
                val number = numberInput.text.toString().trim().replace(" ", "")
                if (number.isNotEmpty()) {
                    val obj = JSONObject()
                    obj.put("label", label)
                    obj.put("number", number)
                    cards.add(obj)
                    saveCards()
                    refreshList()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun finishConfig(label: String, number: String) {
        CardWidgetProvider.saveCardData(this, appWidgetId, label, number)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        CardWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
