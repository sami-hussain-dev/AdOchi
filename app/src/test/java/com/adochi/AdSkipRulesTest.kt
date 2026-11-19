package com.adochi

import com.adochi.skipper.AdSkipRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSkipRulesTest {

    @Test
    fun skipButtonLabels() {
        for (label in listOf("Skip", "Skip ad", "Skip ads", "Skip Ad", "SKIP AD", "Skip ad ›", "​Skip​", "Saltar anuncio", "Pular", "Lewati iklan")) {
            assertTrue(label, AdSkipRules.isSkipLabel(label))
        }
    }

    @Test
    fun notSkipButtons() {
        for (label in listOf(null, "", "Skip in 5", "Video will play after ad", "Skipping stones tutorial", "Skip to content", "Subscribe")) {
            assertFalse(label.toString(), AdSkipRules.isSkipLabel(label))
        }
    }

    @Test
    fun adCountdownLabels() {
        for (label in listOf("Ad · 0:15", "Ad 1 of 2", "Sponsored · 1 of 2 · 0:12", "Ad • 5", "Ad: 0:05", "Anuncio · 0:10")) {
            assertTrue(label, AdSkipRules.isAdLabel(label))
        }
    }

    @Test
    fun feedTagsAndNormalTextAreNotAdsPlaying() {
        for (label in listOf("Sponsored", "Ad", "Adobe Photoshop tutorial", "Advanced Kotlin", "Addition 1 of 2", "Ads explained")) {
            assertFalse(label, AdSkipRules.isAdLabel(label))
        }
    }

    @Test
    fun onlyYouTubeAndFacebook() {
        assertTrue(AdSkipRules.APPS == setOf("com.google.android.youtube", "com.facebook.katana"))
    }
}
