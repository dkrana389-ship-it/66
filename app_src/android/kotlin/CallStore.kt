package com.deepinnovations.call_history

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallStore {
    data class Today(val count: Int, val missed: Int, val totalSec: Long)

    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("calls", Context.MODE_PRIVATE)

    private fun day(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun rollover(c: Context) {
        val sp = p(c)
        if (sp.getString("day", "") != day()) {
            sp.edit().putString("day", day()).putInt("count", 0).putInt("missed", 0)
                .putLong("total", 0L).apply()
        }
    }

    fun today(c: Context): Today {
        rollover(c)
        val sp = p(c)
        return Today(sp.getInt("count", 0), sp.getInt("missed", 0), sp.getLong("total", 0L))
    }

    fun addCall(c: Context, durSec: Long, missed: Boolean) {
        rollover(c)
        val sp = p(c)
        sp.edit()
            .putInt("count", sp.getInt("count", 0) + 1)
            .putInt("missed", sp.getInt("missed", 0) + if (missed) 1 else 0)
            .putLong("total", sp.getLong("total", 0L) + durSec)
            .apply()
    }

    fun setNumber(c: Context, n: String?) { p(c).edit().putString("number", n).apply() }
    fun takeNumber(c: Context): String? {
        val n = p(c).getString("number", null)
        p(c).edit().remove("number").apply()
        return n
    }

    fun getState(c: Context): String = p(c).getString("state", "IDLE") ?: "IDLE"
    fun setState(c: Context, s: String) { p(c).edit().putString("state", s).apply() }

    fun setIncoming(c: Context, v: Boolean) { p(c).edit().putBoolean("incoming", v).apply() }
    fun isIncoming(c: Context): Boolean = p(c).getBoolean("incoming", false)

    fun setStart(c: Context, ms: Long) { p(c).edit().putLong("start", ms).apply() }
    fun getStart(c: Context): Long = p(c).getLong("start", 0L)
}
