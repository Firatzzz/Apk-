package com.example.catatanku

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String, val note: String, val time: Long,
    val location: String, val done: Boolean = false,
    val remind: Boolean = true
)

data class Tx(
    val id: String = UUID.randomUUID().toString(),
    val title: String, val amount: Long, val income: Boolean,
    val category: String, val time: Long
)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("catatanku", Context.MODE_PRIVATE)

    fun loadTasks(): List<Task> = runCatching {
        val a = JSONArray(sp.getString("tasks", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it)
            Task(o.getString("id"), o.getString("title"), o.getString("note"),
                o.getLong("time"), o.getString("location"), o.getBoolean("done"),
                o.optBoolean("remind", true)) }
    }.getOrDefault(emptyList())

    fun loadTxs(): List<Tx> = runCatching {
        val a = JSONArray(sp.getString("txs", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it)
            Tx(o.getString("id"), o.getString("title"), o.getLong("amount"),
                o.getBoolean("income"), o.getString("category"), o.getLong("time")) }
    }.getOrDefault(emptyList())

    fun save(tasks: List<Task>, txs: List<Tx>) {
        val t = JSONArray(); tasks.forEach { t.put(JSONObject()
            .put("id", it.id).put("title", it.title).put("note", it.note)
            .put("time", it.time).put("location", it.location).put("done", it.done)
            .put("remind", it.remind)) }
        val x = JSONArray(); txs.forEach { x.put(JSONObject()
            .put("id", it.id).put("title", it.title).put("amount", it.amount)
            .put("income", it.income).put("category", it.category).put("time", it.time)) }
        sp.edit().putString("tasks", t.toString()).putString("txs", x.toString()).apply()
    }
}
