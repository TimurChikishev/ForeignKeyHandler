package ru.ktsstudio.sample.domain

interface MainRepository {

    suspend fun insert()

    suspend fun update()

    suspend fun delete()
}