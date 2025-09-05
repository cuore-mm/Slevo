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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@HiltViewModel
class SettingsCookieViewModel @Inject constructor(
    private val repository: CookieRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsCookieUiState())
    val uiState: StateFlow<SettingsCookieUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCookies().collect { cookies ->
                _uiState.update { state ->
                    state.copy(cookies = cookies.map { it.toCookieItem() })
                }
            }
        }
    }

    fun removeCookie(item: CookieItem) {
        viewModelScope.launch {
            repository.remove(item.cookie)
        }
    }
}

data class SettingsCookieUiState(
    val cookies: List<CookieItem> = emptyList()
)

data class CookieItem(
    val name: String,
    val domain: String,
    val path: String,
    val expires: String,
    val size: Int,
    val valuePreview: String,
    val cookie: Cookie,
)

private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

private fun Cookie.toCookieItem(): CookieItem {
    val expiresText = if (persistent) {
        formatter.format(Instant.ofEpochMilli(expiresAt))
    } else {
        "セッション"
    }
    val sizeBytes = name.toByteArray().size + value.toByteArray().size
    val preview = if (value.length <= 4) {
        value
    } else {
        value.take(4) + "…"
    }
    return CookieItem(
        name = name,
        domain = domain,
        path = path,
        expires = expiresText,
        size = sizeBytes,
        valuePreview = preview,
        cookie = this,
    )
}

