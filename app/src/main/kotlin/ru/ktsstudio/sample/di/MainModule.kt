package ru.ktsstudio.sample.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.ktsstudio.sample.data.MainRepositoryImpl
import ru.ktsstudio.sample.domain.MainRepository
import ru.ktsstudio.sample.domain.MainUseCase
import ru.ktsstudio.sample.domain.MainUseCaseImpl
import ru.ktsstudio.sample.presentation.MainViewModel

val mainModule = module {
    viewModel {
        MainViewModel(
            mainUseCase = get()
        )
    }
    factory<MainUseCase> {
        MainUseCaseImpl(
            mainRepository = get()
        )
    }
    factory<MainRepository> {
        MainRepositoryImpl(
            userDao = get(),
            billDao = get(),
            categoryDao = get(),
            transactionDao = get(),
        )
    }
}