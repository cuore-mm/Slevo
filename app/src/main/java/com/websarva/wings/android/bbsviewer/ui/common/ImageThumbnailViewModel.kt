package com.websarva.wings.android.bbsviewer.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    private val _imageStates = MutableStateFlow<Map<String, ImageThumbnailItemState>>(emptyMap())
    val imageStates: StateFlow<Map<String, ImageThumbnailItemState>> = _imageStates

    fun loadImage(url: String) {
        if (_imageStates.value.containsKey(url)) return

        viewModelScope.launch {
            repository.downloadImage(url).collect { result ->
                when (result) {
                    is ImageDownloadState.Progress -> {
                        _imageStates.update { current ->
                            val state = current[url] ?: ImageThumbnailItemState()
                            current + (url to state.copy(downloaded = result.downloaded, total = result.total))
                        }
                    }
                    is ImageDownloadState.Success -> {
                        val bmp = BitmapFactory.decodeByteArray(result.data, 0, result.data.size)
                        _imageStates.update { current ->
                            val state = current[url] ?: ImageThumbnailItemState()
                            current + (url to state.copy(bitmap = bmp, isLoading = false))
                        }
                    }
                    is ImageDownloadState.Error -> {
                        _imageStates.update { current ->
                            val state = current[url] ?: ImageThumbnailItemState()
                            current + (url to state.copy(isLoading = false))
                        }
                    }
                }
            }
        }
    }
}

data class ImageThumbnailItemState(
    val bitmap: Bitmap? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val isLoading: Boolean = true,
)
