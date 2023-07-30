package ru.ktsstudio.sample.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.ktsstudio.sample.domain.MainUseCase

class MainViewModel(
    private val mainUseCase: MainUseCase
) : ViewModel() {

    fun insert() {
        viewModelScope.launch {
            mainUseCase.insert()
        }
    }

    fun update() {
        viewModelScope.launch {
            mainUseCase.update()
        }
    }

    fun delete() {
        viewModelScope.launch {
            mainUseCase.delete()
        }
    }
}