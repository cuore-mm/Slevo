package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ThreadPagerViewModel @Inject constructor() : ViewModel() {
    private val _currentPage = MutableStateFlow(-1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }
}
