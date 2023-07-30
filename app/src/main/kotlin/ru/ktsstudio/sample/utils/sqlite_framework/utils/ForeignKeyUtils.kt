package ru.ktsstudio.sample.utils.sqlite_framework.utils

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.ktsstudio.sample.utils.sqlite_framework.models.ForeignKey
import ru.ktsstudio.sample.utils.sqlite_framework.models.ForeignKeyDelete

@Suppress("SpreadOperator")
internal inline fun <T, R> SupportSQLiteDatabase.withForeignKeyCheck(
    sql: String,
    args: Array<T>,
    query: (String, Array<T>) -> R
): R {
    try {
        return query(sql, args)
    } catch (e: SQLiteConstraintException) {
        val prepareSql = prepareSql(sql)
        val argsList = args.map { it.toString() }

        val foreignKeyMessage = getForeignKeyMessage(sql = prepareSql.lowercase(), args = argsList)

        val formattedSqlQuery = prepareSql.replace("?", "%S").format(*args)
        throw SQLiteConstraintException("$formattedSqlQuery\n$foreignKeyMessage")
    }
}

internal inline fun <T> SupportSQLiteDatabase.withForeignKeyCheck(sql: String, query: (String) -> T): T {
    try {
        return query(sql)
    } catch (e: SQLiteConstraintException) {
        val prepareSql = prepareSql(sql)
        val foreignKeyListMessage = getForeignKeyListMessage(prepareSql)
        throw SQLiteConstraintException("sql=$prepareSql".plus(foreignKeyListMessage))
    }
}

internal inline fun <R> SupportSQLiteDatabase.insertWithForeignKeyCheck(
    table: String,
    contentValues: ContentValues,
    query: () -> R
): R {
    val keys = contentValues.keySet().joinToString(", ")
    val values = contentValues.valueSet().map { it.value }.joinToString(", ")
    val sql = "INSERT INTO $table ($keys) VALUES ($values)"

    return withForeignKeyCheck(sql) { query() }
}

private fun SupportSQLiteDatabase.getForeignKeyListMessage(sql: String): String {
    val foreignKeyList = getTableNameFromSqlQuery(sql)
        ?.let(::queryForeignKeyList)
        ?.joinToString(", ") { it.toString() }

    if (foreignKeyList.isNullOrEmpty()) return ""

    return ", foreignKeyList=$foreignKeyList"
}

private fun SupportSQLiteDatabase.getForeignKeyMessage(sql: String, args: List<String>): String {
    val tableName = getTableNameFromSqlQuery(sql) ?: return ""
    val foreignKeyList = queryForeignKeyList(tableName)

    if(foreignKeyList.isEmpty()) return ""

    val messages = getForeignKeyMessages(sql, args)

    return messages.orEmpty().ifEmpty {
        val foreignKeyListMessage = foreignKeyList.joinToString(", ") { it.toString() }
        "foreign_key_list=[$foreignKeyListMessage]"
    }
}

private fun SupportSQLiteDatabase.getForeignKeyMessages(
    sql: String,
    args: List<String>
): String? {
    val tableName = getTableNameFromSqlQuery(sql) ?: return ""
    val foreignKeyList = queryForeignKeyList(tableName)

    return when {
        sql.isInsert || sql.isUpdate -> {
            foreignKeyList.mapNotNull { foreignKey ->
                getForeignKeyValueForInsertAndUpdate(sql = sql, args = args, foreignKey = foreignKey)?.let { value ->
                    "FK Error ($tableName.${foreignKey.localColumn} -> " +
                        "${foreignKey.foreignTable}.${foreignKey.foreignColumn})\n" +
                        "There is no field with ${foreignKey.foreignColumn}=$value " +
                        "in the ${foreignKey.foreignTable} table"
                }
            }.joinToString(",\n")
        }
        sql.isDelete -> {
            getForeignKeyValuesForDelete(
                sql = sql,
                args = args,
                tableName = tableName,
                foreignKeyList = foreignKeyList
            )?.joinToString(",\n") { (foreignKey, primaryKeyName, primaryKeyValue, value) ->
                "FK Error ($tableName.${foreignKey?.localColumn} -> " +
                    "${foreignKey?.foreignTable}.${foreignKey?.foreignColumn})\n" +
                    "For $tableName.$primaryKeyName=$primaryKeyValue: it is not possible to delete the field, " +
                    "because the ${foreignKey?.foreignTable} table has the field ${foreignKey?.foreignColumn}=$value"
            }
        }
        else -> null
    }
}

private fun SupportSQLiteDatabase.getForeignKeyValueForInsertAndUpdate(
    sql: String,
    args: List<String>,
    foreignKey: ForeignKey
): String? {
    val value = when {
        sql.isInsert -> getValuesFromUpdateAndInsertSqlQueryByColumnName(
            sql = sql,
            args = args,
            columnName = foreignKey.localColumn,
            getColumnNamesFromSql = ::getColumnNamesFromSqlInsertQuery,
            isArgsSizeEqualColumnsSize = { columns -> args.size == columns.size }
        )
        sql.isUpdate -> getValuesFromUpdateAndInsertSqlQueryByColumnName(
            sql = sql,
            args = args,
            columnName = foreignKey.localColumn,
            getColumnNamesFromSql = ::getColumnNamesFromSqlUpdateQuery,
            isArgsSizeEqualColumnsSize = { columns -> sql.updateColumnArgsSize == columns.size }
        )
        else -> null
    } ?: return null

    val cursor = query("SELECT * FROM ${foreignKey.foreignTable} WHERE ${foreignKey.foreignColumn} = ?", arrayOf(value))
    val isForeignKey = cursor.use { it.moveToFirst() }.not()

    return if (isForeignKey) value else null
}

@Suppress("ReturnCount")
private fun SupportSQLiteDatabase.getForeignKeyValuesForDelete(
    sql: String,
    tableName: String,
    args: List<String>,
    foreignKeyList: List<ForeignKey>
): MutableList<ForeignKeyDelete>? {
    if (sql.isDelete.not() || foreignKeyList.isEmpty()) return null
    val primaryKeyName = queryPrimaryKeyName(tableName) ?: return null

    val fkMap = foreignKeyList.associateBy({ it.localColumn }, { it })
    val fromColumns = foreignKeyList.map { it.localColumn }

    val sqlWithArgs = formatSqlQuery(sql, args.toTypedArray())
    val conditions = getConditionsFromSqlDeleteQuery(sql = sqlWithArgs)

    val cursorWithDeleteConditions = query("SELECT * FROM $tableName WHERE $conditions")

    val result = mutableListOf<ForeignKeyDelete>()

    cursorWithDeleteConditions.use {
        while (it.moveToNext()) {
            val primaryKeyIndex = it.getColumnIndex(primaryKeyName)
            val primaryKeyValue = if (primaryKeyIndex != -1) it.getString(primaryKeyIndex) else null

            val values = fromColumns.mapNotNull { columnName ->
                val columnIndex = it.getColumnIndex(columnName)
                if (columnIndex != -1) columnName to it.getString(columnIndex) else null
            }
                .filter { (_, value) -> value != null }

            val foreignKeyDeleteList = values.mapNotNull { (columnName, value) ->
                getForeignKeyDelete(
                    value = value,
                    foreignKeyMap = fkMap,
                    columnName = columnName,
                    primaryKeyName = primaryKeyName,
                    primaryKeyValue = primaryKeyValue,
                )
            }

            result.addAll(foreignKeyDeleteList)
        }
    }

    return result
}

private fun SupportSQLiteDatabase.getForeignKeyDelete(
    foreignKeyMap: Map<String, ForeignKey>,
    primaryKeyName: String,
    primaryKeyValue: String?,
    columnName: String,
    value: String
): ForeignKeyDelete? {
    val fk = foreignKeyMap[columnName] ?: return null
    val table = fk.foreignTable
    val to = fk.foreignColumn

    val cursor = query("SELECT * FROM $table WHERE $to = $value")
    val isForeignKey = cursor.use { it.moveToFirst() }

    return if (isForeignKey) {
        ForeignKeyDelete(
            fk = fk,
            primaryKeyName = primaryKeyName,
            primaryKeyValue = primaryKeyValue,
            value = value
        )
    } else {
        null
    }
}
