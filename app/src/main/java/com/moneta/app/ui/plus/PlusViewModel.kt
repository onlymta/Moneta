package com.moneta.app.ui.plus

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.plus.AdsManager
import com.moneta.app.data.plus.BillingManager
import com.moneta.app.data.plus.BillingUi
import com.moneta.app.data.plus.PlusRepository
import com.moneta.app.data.plus.PlusState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlusViewModel @Inject constructor(
    private val plusRepository: PlusRepository,
    private val billing: BillingManager,
    private val ads: AdsManager
) : ViewModel() {

    val state: StateFlow<PlusState> = plusRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlusState.Loading)

    val billingUi: StateFlow<BillingUi> = billing.ui

    private val _adBusy = MutableStateFlow(false)
    val adBusy: StateFlow<Boolean> = _adBusy.asStateFlow()

    private val _adMessage = MutableStateFlow<String?>(null)
    val adMessage: StateFlow<String?> = _adMessage.asStateFlow()

    fun startTrial() {
        viewModelScope.launch { plusRepository.startTrial() }
    }

    /** TEST-TOGGLE */
    fun setTestPlus(plus: Boolean) {
        viewModelScope.launch { plusRepository.setTestPlus(plus) }
    }

    fun loadPrice() = billing.refresh(userInitiated = false)

    fun restorePurchase() = billing.refresh(userInitiated = true)

    fun buy(activity: Activity) = billing.purchase(activity)

    fun watchAd(activity: Activity) {
        ads.watchToUnlock(
            activity,
            onBusy = { _adBusy.value = it },
            onMessage = { _adMessage.value = it.ifBlank { null } }
        )
    }
}
