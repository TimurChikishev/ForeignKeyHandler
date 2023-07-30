package ru.ktsstudio.sample.domain

class MainUseCaseImpl(
    private val mainRepository: MainRepository
) : MainUseCase {

    override suspend fun insert() = mainRepository.insert()

    override suspend fun update() = mainRepository.update()

    override suspend fun delete() = mainRepository.delete()
}