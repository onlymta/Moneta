package com.moneta.app.data.plus

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.moneta.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewarded ads, only ever started by the user tapping "Watch an ad to unlock". Nothing here runs
 * for people who own Plus, and no ad is ever shown without that tap. Before the first ad the
 * consent form Google requires (EEA/UK) is shown when it applies.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plusRepository: PlusRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initialized = false

    fun watchToUnlock(activity: Activity, onBusy: (Boolean) -> Unit, onMessage: (String) -> Unit) {
        onBusy(true)
        onMessage("")
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val proceed = {
            if (consent.canRequestAds()) {
                loadAndShow(activity, onBusy, onMessage)
            } else {
                onBusy(false)
                onMessage("Ads need your privacy choice first. Try again, or get Plus (no ads).")
            }
        }
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> proceed() }
            },
            { _ -> proceed() }
        )
    }

    private fun loadAndShow(activity: Activity, onBusy: (Boolean) -> Unit, onMessage: (String) -> Unit) {
        if (!initialized) {
            MobileAds.initialize(context) { }
            initialized = true
        }
        RewardedAd.load(
            activity,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    var rewarded = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onBusy(false)
                            if (!rewarded) onMessage("Watch the ad to the end to unlock.")
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            onBusy(false)
                            onMessage("The ad couldn't be shown. Try again in a moment.")
                        }
                    }
                    // Hide the system bars while the ad plays so it fills the whole screen.
                    ad.setImmersiveMode(true)
                    ad.show(activity) { _ ->
                        rewarded = true
                        scope.launch { plusRepository.unlockInsights() }
                        onMessage("Unlocked for $AD_UNLOCK_HOURS hours.")
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onBusy(false)
                    val reason = when (error.code) {
                        0 -> "internal error"
                        1 -> "the ad IDs are not valid"
                        2 -> "no internet connection"
                        3 -> "no ad to show yet"
                        else -> "error " + error.code
                    }
                    onMessage("No ad could be loaded ($reason). Try again in a moment, or get Plus.")
                }
            }
        )
    }
}
