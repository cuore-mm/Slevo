package com.websarva.wings.android.bbsviewer.ui.common

import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.ImageDownloadState
import com.websarva.wings.android.bbsviewer.data.repository.ImageDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImageThumbnailViewModel @Inject constructor(
    private val repository: ImageDownloadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageThumbnailUiState())
    val uiState: StateFlow<ImageThumbnailUiState> = _uiState

    fun loadImages(urls: List<String>) {
        urls.forEach { url ->
            if (_uiState.value.items.containsKey(url)) return@forEach
            _uiState.update { current ->
                current.copy(items = current.items + (url to ImageThumbnailItemState()))
            }
            viewModelScope.launch {
                repository.downloadImage(url).collect { state ->
                    Log.d("ImageThumbnailViewModel", "State for $url: $state")
                    when (state) {
                        is ImageDownloadState.Progress -> {
                            _uiState.update { current ->
                                val item = current.items[url] ?: ImageThumbnailItemState()
                                current.copy(
                                    items = current.items + (url to item.copy(
                                        downloaded = state.downloaded,
                                        total = state.total,
                                        isLoading = true
                                    ))
                                )
                            }
                        }
                        is ImageDownloadState.Success -> {
                            val bitmap = BitmapFactory.decodeByteArray(state.bytes, 0, state.bytes.size)
                            _uiState.update { current ->
                                val item = current.items[url] ?: ImageThumbnailItemState()
                                current.copy(
                                    items = current.items + (url to item.copy(
                                        bitmap = bitmap,
                                        bytes = state.bytes,
                                        isLoading = false
                                    ))
                                )
                            }
                        }
                        is ImageDownloadState.Error -> {
                            _uiState.update { current ->
                                val item = current.items[url] ?: ImageThumbnailItemState()
                                current.copy(
                                    items = current.items + (url to item.copy(isLoading = false))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

