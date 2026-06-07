package com.hgu.watervalve.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.local.db.WaterRecordDao
import com.hgu.watervalve.domain.model.WaterRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开阀记录 ViewModel。
 */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val waterRecordDao: WaterRecordDao,
) : ViewModel() {

    private val _records = MutableStateFlow<List<WaterRecord>>(emptyList())
    val records: StateFlow<List<WaterRecord>> = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            waterRecordDao.observeAll().collect { list ->
                _records.value = list
                _isLoading.value = false
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            waterRecordDao.deleteAll()
        }
    }
}
