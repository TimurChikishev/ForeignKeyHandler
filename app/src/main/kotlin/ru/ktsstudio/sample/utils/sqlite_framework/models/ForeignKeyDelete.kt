package ru.ktsstudio.sample.utils.sqlite_framework.models

internal data class ForeignKeyDelete(
    val fk: ForeignKey?,
    val primaryKeyName: String,
    val primaryKeyValue: String?,
    val value: String
)
