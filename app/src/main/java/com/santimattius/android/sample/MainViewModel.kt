package com.santimattius.android.sample

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MainUiState(
    val text: String = "",
    val isEnabled: Boolean = false
)

class MainViewModel(
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state

    init {
        initializeUiState()
    }

    private fun initializeUiState() {
        _state.value = _state.value.copy(
            text = "Android Shared Preferences",
            isEnabled = sharedPreferences.getBoolean("is_enabled", false)
        )
    }

    fun onCheckedChange(isEnabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = isEnabled)
        sharedPreferences.edit { putBoolean("is_enabled", isEnabled) }
    }

    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {

        private val sharedPreferences: SharedPreferences by lazy {
            context.getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE)
        }

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(sharedPreferences) as T
        }
    }
}