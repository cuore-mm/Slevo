package com.websarva.wings.android.slevo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.repository.CookieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Cookie

@HiltViewModel
class SettingsCookieViewModel @Inject constructor(
    private val repository: CookieRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsCookieUiState())
    val uiState: StateFlow<SettingsCookieUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCookies().collect { cookies ->
                _uiState.update { it.copy(cookies = cookies) }
            }
        }
    }

    fun removeCookie(cookie: Cookie) {
        viewModelScope.launch {
            repository.remove(cookie)
        }
    }
}

data class SettingsCookieUiState(
    val cookies: List<Cookie> = emptyList()
)

