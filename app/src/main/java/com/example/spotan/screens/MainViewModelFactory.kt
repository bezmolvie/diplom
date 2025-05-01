package com.example.spotan.screens
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.spotan.data.PlayedItemDao
class MainViewModelFactory(
    private val token: String,
    private val playedItemDao: PlayedItemDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(token, playedItemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}