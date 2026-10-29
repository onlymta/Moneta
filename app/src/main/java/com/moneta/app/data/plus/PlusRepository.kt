package com.moneta.app.data.plus

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
const val TRIAL_DAYS = 14
const val AD_UNLOCK_HOURS = 24

/**
 * What the user currently has. [hasPlus] is true for a purchase or while the free trial runs.
 * [insightsUnlocked] adds the 24 hour unlock earned by watching a rewarded ad (charts, PDF report).
 */
data class PlusState(
    val purchased: Boolean = false,
    val trialDaysLeft: Int = 0,
    val hasPlus: Boolean = false,
    val insightsUnlocked: Boolean = false,
    val adUnlockUntil: Long = 0L,
    /** True until the user chooses to start the free trial. */
    val trialAvailable: Boolean = false
) {
    val trialActive: Boolean get() = !purchased && trialDaysLeft > 0

    companion object {
        /** Shown for the instant before stored values are read, so nothing flashes as locked. */
        val Loading = PlusState(hasPlus = true, insightsUnlocked = true)
    }
}

// A separate file from the settings, so it is never part of a backup file and a shared backup can't hand out Plus.
private val Context.plusStore by preferencesDataStore(name = "moneta_plus")

@Singleton
class PlusRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PURCHASED = booleanPreferencesKey("purchased")
        val TRIAL_ENDS_AT = longPreferencesKey("trial_ends_at")
        val AD_UNLOCK_UNTIL = longPreferencesKey("ad_unlock_until")
        // TEST-TOGGLE: 0 = real state, 1 = pretend Free, 2 = pretend Plus. Remove with the toggle before release.
        val TEST_MODE = intPreferencesKey("test_mode")
    }

    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    val state: Flow<PlusState> = combine(context.plusStore.data, clock) { prefs, now ->
        val purchased = prefs[Keys.PURCHASED] ?: false
        val trialEnds = prefs[Keys.TRIAL_ENDS_AT]
        // The trial only runs after the user taps "Start free trial".
        val trialActive = trialEnds != null && now < trialEnds
        val daysLeft = when {
            trialEnds == null -> 0
            now >= trialEnds -> 0
            else -> (((trialEnds - now) + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
        }
        val testMode = prefs[Keys.TEST_MODE] ?: 0 // TEST-TOGGLE
        val purchasedEffective = when (testMode) { 1 -> false; 2 -> true; else -> purchased }
        val trialActiveEffective = if (testMode != 0) false else trialActive
        val hasPlus = purchasedEffective || trialActiveEffective
        val adUntil = prefs[Keys.AD_UNLOCK_UNTIL] ?: 0L
        PlusState(
            purchased = purchasedEffective,
            trialDaysLeft = if (testMode != 0) 0 else daysLeft,
            hasPlus = hasPlus,
            insightsUnlocked = hasPlus || now < adUntil,
            adUnlockUntil = adUntil,
            trialAvailable = !purchasedEffective && trialEnds == null
        )
    }.distinctUntilChanged()

    val hasPlus: Flow<Boolean> = state.map { it.hasPlus }.distinctUntilChanged()

    /** Starts the free trial when the user asks for it. It can only be started once. */
    suspend fun startTrial() {
        if (context.plusStore.data.first()[Keys.TRIAL_ENDS_AT] == null) {
            context.plusStore.edit { it[Keys.TRIAL_ENDS_AT] = System.currentTimeMillis() + TRIAL_DAYS * DAY_MILLIS }
        }
    }

    /** When this device's free trial ends (or ended), or null if it was never started. Written into backup files. */
    suspend fun trialEndsAt(): Long? = context.plusStore.data.first()[Keys.TRIAL_ENDS_AT]

    /**
     * Used when a backup file is restored: if the backup remembers a trial and this install has not started one,
     * the same trial dates are kept, so restoring a backup on a fresh install does not hand out a second trial.
     * It only ever fills in a missing value; it never extends or replaces a trial that already exists.
     */
    suspend fun adoptTrialEnd(endsAt: Long) {
        if (context.plusStore.data.first()[Keys.TRIAL_ENDS_AT] == null) {
            context.plusStore.edit { it[Keys.TRIAL_ENDS_AT] = endsAt }
        }
    }

    /** TEST-TOGGLE: pretend to be a Plus or a Free user. Going to Free also clears any ad unlock so locks can be tested. */
    suspend fun setTestPlus(plus: Boolean) {
        context.plusStore.edit {
            it[Keys.TEST_MODE] = if (plus) 2 else 1
            if (!plus) it[Keys.AD_UNLOCK_UNTIL] = 0L
        }
    }

    suspend fun setPurchased(purchased: Boolean) {
        context.plusStore.edit { it[Keys.PURCHASED] = purchased }
    }

    /** Called after a rewarded ad is watched to the end. */
    suspend fun unlockInsights() {
        context.plusStore.edit {
            it[Keys.AD_UNLOCK_UNTIL] = System.currentTimeMillis() + AD_UNLOCK_HOURS * 60L * 60L * 1000L
        }
    }
}
