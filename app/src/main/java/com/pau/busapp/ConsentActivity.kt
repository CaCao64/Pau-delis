package com.pau.busapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pau.busapp.databinding.ActivityConsentBinding

class ConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentBinding

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleHelper.apply(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ConsentManager.hasDecision(this)) {
            goNext()
            return
        }

        binding.btnAcceptAnalytics.setOnClickListener {
            ConsentManager.acceptAnalytics(this)
            AnalyticsTracker.init(this)
            AnalyticsTracker.trackAction(this, "consent_accept", "analytics_consent", "Consentement")
            goNext()
        }

        binding.btnDeclineAnalytics.setOnClickListener {
            ConsentManager.declineAnalytics(this)
            goNext()
        }
    }

    private fun goNext() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
