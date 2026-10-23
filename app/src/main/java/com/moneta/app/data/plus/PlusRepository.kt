package com.moneta.app.data.plus

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val adUnlockUntil: Long = 0L
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
        // Not started yet (very first moments after install): treat as inside the trial.
        val trialActive = trialEnds == null || now < trialEnds
        val daysLeft = when {
            trialEnds == null -> TRIAL_DAYS
            now >= trialEnds -> 0
            else -> (((trialEnds - now) + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
        }
        val hasPlus = purchased || trialActive
        val adUntil = prefs[Keys.AD_UNLOCK_UNTIL] ?: 0L
        PlusState(
            purchased = purchased,
            trialDaysLeft = daysLeft,
            hasPlus = hasPlus,
            insightsUnlocked = hasPlus || now < adUntil,
            adUnlockUntil = adUntil
        )
    }.distinctUntilChanged()

    val hasPlus: Flow<Boolean> = state.map { it.hasPlus }.distinctUntilChanged()

    /** Starts the free trial the first time the app runs. Later calls do nothing. */
    suspend fun ensureTrialStarted() {
        if (context.plusStore.data.first()[Keys.TRIAL_ENDS_AT] == null) {
            context.plusStore.edit { it[Keys.TRIAL_ENDS_AT] = System.currentTimeMillis() + TRIAL_DAYS * DAY_MILLIS }
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
