package ru.ktsstudio.sample.domain

interface MainUseCase {

    suspend fun insert()

    suspend fun update()

    suspend fun delete()
}

