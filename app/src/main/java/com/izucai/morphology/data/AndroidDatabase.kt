package com.izucai.morphology.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** [SqlRunner] di atas `android.database.sqlite` (koneksi read-only). */
class AndroidSqlRunner(private val db: SQLiteDatabase) : SqlRunner {
    override fun <T> query(sql: String, args: List<String>, map: (SqlRow) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            val row = CursorRow(cursor)
            while (cursor.moveToNext()) out += map(row)
        }
        return out
    }

    private class CursorRow(private val cursor: Cursor) : SqlRow {
        override fun string(index: Int): String? = if (cursor.isNull(index)) null else cursor.getString(index)
        override fun long(index: Int): Long = cursor.getLong(index)
    }
}

/**
 * Menyalin `assets/russian_morphology.db` ke penyimpanan aplikasi saat pertama kali dijalankan
 * (atau setelah aplikasi diperbarui), lalu membukanya read-only. Tidak ada akses jaringan.
 */
object MorphologyDatabase {
    private const val DB_NAME = "russian_morphology.db"
    private const val PREFS = "morphology_db"
    private const val KEY_STAMP = "asset_stamp"

    @Volatile
    private var instance: SQLiteDatabase? = null

    /** Panggil dari thread latar: penyalinan pertama memakan waktu beberapa detik. */
    fun open(context: Context): SQLiteDatabase {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val app = context.applicationContext
            val target = app.getDatabasePath(DB_NAME)
            ensureCopied(app, target)
            return SQLiteDatabase.openDatabase(
                target.path, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).also { instance = it }
        }
    }

    private fun ensureCopied(context: Context, target: File) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Cap versi: berubah setiap kali APK dipasang/diperbarui, sehingga basis data lama tidak tertinggal.
        val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (target.exists() && prefs.getString(KEY_STAMP, null) == stamp) return

        target.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        context.assets.open(DB_NAME).use { input ->
            temp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        }
        if (target.exists()) target.delete()
        File(target.path + "-journal").delete()
        if (!temp.renameTo(target)) throw IllegalStateException("Gagal menyiapkan basis data offline")
        prefs.edit().putString(KEY_STAMP, stamp).apply()
    }
}
