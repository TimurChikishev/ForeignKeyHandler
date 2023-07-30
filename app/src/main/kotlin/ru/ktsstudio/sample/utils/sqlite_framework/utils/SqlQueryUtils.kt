package ru.ktsstudio.sample.utils.sqlite_framework.utils

import androidx.sqlite.db.SupportSQLiteDatabase
import ru.ktsstudio.sample.utils.sqlite_framework.models.ForeignKey
import ru.ktsstudio.sample.utils.sqlite_framework.models.TableInfo

internal fun SupportSQLiteDatabase.queryForeignKeyList(tableName: String): List<ForeignKey> {
    val foreignKeyList = mutableListOf<ForeignKey>()
    val sql = "PRAGMA foreign_key_list($tableName)"
    val cursor = query(sql)

    cursor.use {
        while (cursor.moveToNext()) {
            val table = cursor.getString(2)
            val fromTable = cursor.getString(3)
            val toTable = cursor.getString(4)

            foreignKeyList.add(
                ForeignKey(
                    foreignTable = table,
                    localTable = tableName,
                    foreignColumn = toTable,
                    localColumn = fromTable,
                )
            )
        }
    }

    return foreignKeyList
}

internal fun SupportSQLiteDatabase.queryTableInfo(tableName: String): List<TableInfo> {
    val tableInfoList = mutableListOf<TableInfo>()
    val sql = "PRAGMA table_info($tableName)"
    val cursor = query(sql)

    cursor.use {
        while (cursor.moveToNext()) {
            val name = cursor.getString(1)
            val pk = cursor.getInt(5)

            tableInfoList.add(
                TableInfo(
                    name = name,
                    primaryKey = pk,
                )
            )
        }
    }

    return tableInfoList
}

internal fun SupportSQLiteDatabase.queryPrimaryKeyName(tableName: String): String? {
    return queryTableInfo(tableName).firstOrNull { it.primaryKey == 1 }?.name
}
