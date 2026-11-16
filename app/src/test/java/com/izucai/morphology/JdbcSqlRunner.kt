package com.izucai.morphology

import com.izucai.morphology.data.MorphologyDao
import com.izucai.morphology.data.MorphologyRepository
import com.izucai.morphology.data.SqlRow
import com.izucai.morphology.data.SqlRunner
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * [SqlRunner] berbasis JDBC untuk unit test JVM: menjalankan SQL aplikasi yang sama persis
 * terhadap berkas `app/src/main/assets/russian_morphology.db` yang dikemas ke dalam APK.
 */
class JdbcSqlRunner(private val connection: Connection) : SqlRunner {
    override fun <T> query(sql: String, args: List<String>, map: (SqlRow) -> T): List<T> {
        val out = ArrayList<T>()
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, arg -> statement.setString(i + 1, arg) }
            statement.executeQuery().use { rs ->
                val row = ResultRow(rs)
                while (rs.next()) out += map(row)
            }
        }
        return out
    }

    private class ResultRow(private val rs: ResultSet) : SqlRow {
        override fun string(index: Int): String? = rs.getString(index + 1)
        override fun long(index: Int): Long = rs.getLong(index + 1)
    }
}

object TestDatabase {
    /** Unit test Gradle berjalan dengan direktori kerja = folder modul `app/`. */
    private val file: File = listOf("src/main/assets/russian_morphology.db", "app/src/main/assets/russian_morphology.db")
        .map(::File).firstOrNull { it.exists() }
        ?: error("russian_morphology.db belum ada di assets — jalankan data_pipeline/build_db.py")

    private val connection: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:file:${file.absolutePath.replace('\\', '/')}?mode=ro")
    }

    val dao: MorphologyDao by lazy { MorphologyDao(JdbcSqlRunner(connection)) }
    val repository: MorphologyRepository by lazy { MorphologyRepository(dao, Dispatchers.Unconfined) }
}
