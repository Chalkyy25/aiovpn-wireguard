package com.aiovpn.home

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.login.ServerListActivity
import com.aiovpn.util.SettingsStore
import com.aiovpn.app.BuildConfig
import com.aiovpn.app.R
import com.wireguard.android.updater.Updater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore

    private lateinit var sideNav: View
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View

    private lateinit var settingKillSwitch: TextView
    private lateinit var settingAutoConnect: TextView
    private lateinit var settingProtocol: TextView

    private lateinit var settingCheckUpdates: RelativeLayout
    private lateinit var currentVersionText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var checkUpdatesActionText: TextView
    private lateinit var updateReleaseNotes: TextView

    private lateinit var navTexts: List<View>
    private var isExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsStore = SettingsStore(this)

        sideNav = findViewById(R.id.sideNav)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        settingKillSwitch = findViewById(R.id.settingKillSwitch)
        settingAutoConnect = findViewById(R.id.settingAutoConnect)
        settingProtocol = findViewById(R.id.settingProtocol)

        settingCheckUpdates = findViewById(R.id.settingCheckUpdates)
        currentVersionText = findViewById(R.id.currentVersionText)
        updateStatusText = findViewById(R.id.updateStatusText)
        checkUpdatesActionText = findViewById(R.id.checkUpdatesActionText)
        updateReleaseNotes = findViewById(R.id.updateReleaseNotes)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        bindNavigation()
        bindSettings()
        bindUpdaterSection()
        observeSettings()
    }

    private fun bindSettings() {
        settingKillSwitch.setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.killSwitchFlow.first()
                settingsStore.setKillSwitch(!current)
            }
        }

        settingAutoConnect.setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.autoConnectFlow.first()
                settingsStore.setAutoConnect(!current)
            }
        }

        settingProtocol.setOnClickListener {
            Toast.makeText(this, "Protocol selection coming next", Toast.LENGTH_SHORT).show()
        }

        settingKillSwitch.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }

        settingAutoConnect.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }

        settingProtocol.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsStore.killSwitchFlow.collect { enabled ->
                settingKillSwitch.text = if (enabled) {
                    getString(R.string.aio_setting_kill_switch) + "  •  On"
                } else {
                    getString(R.string.aio_setting_kill_switch) + "  •  Off"
                }
            }
        }

        lifecycleScope.launch {
            settingsStore.autoConnectFlow.collect { enabled ->
                settingAutoConnect.text = if (enabled) {
                    getString(R.string.aio_setting_auto_connect) + "  •  On"
                } else {
                    getString(R.string.aio_setting_auto_connect) + "  •  Off"
                }
            }
        }

        lifecycleScope.launch {
            // Placeholder until protocol choice is wired from real prefs
            settingProtocol.text = getString(R.string.aio_setting_protocol) + "  •  Smart Auto"
        }
    }

    private fun bindUpdaterSection() {
        currentVersionText.text = BuildConfig.VERSION_NAME

        settingCheckUpdates.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }

        settingCheckUpdates.setOnClickListener {
            when (val state = Updater.state.value) {
                is Updater.Progress.Available -> {
                    state.update()
                    Toast.makeText(this, "Starting update...", Toast.LENGTH_SHORT).show()
                }

                is Updater.Progress.Failure -> {
                    state.retry()
                    Toast.makeText(this, "Retrying update...", Toast.LENGTH_SHORT).show()
                }

                is Updater.Progress.NeedsUserIntervention -> {
                    startActivity(state.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }

                else -> {
                    Updater.checkNow()
                    Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            Updater.state.collect { progress ->
                when (progress) {
                    is Updater.Progress.Complete -> {
                        updateStatusText.text = "Up to date"
                        checkUpdatesActionText.text = "Done"
                        updateReleaseNotes.visibility = View.GONE
                    }

                    is Updater.Progress.Rechecking -> {
                        updateStatusText.text = "Checking..."
                        checkUpdatesActionText.text = "Please wait"
                        updateReleaseNotes.visibility = View.GONE
                    }

                    is Updater.Progress.Downloading -> {
                        updateStatusText.text = "Downloading..."
                        checkUpdatesActionText.text = "In progress"
                        updateReleaseNotes.visibility = View.GONE
                    }

                    is Updater.Progress.Installing -> {
                        updateStatusText.text = "Installing..."
                        checkUpdatesActionText.text = "Please wait"
                        updateReleaseNotes.visibility = View.GONE
                    }

                    is Updater.Progress.Available -> {
                        updateStatusText.text = "Update available"
                        checkUpdatesActionText.text = if (progress.mandatory) "Required" else "Update"

                        if (!progress.releaseNotes.isNullOrBlank()) {
                            updateReleaseNotes.text = progress.releaseNotes
                            updateReleaseNotes.visibility = View.VISIBLE
                        } else {
                            updateReleaseNotes.visibility = View.GONE
                        }
                    }

                    is Updater.Progress.NeedsUserIntervention -> {
                        updateStatusText.text = "Action required"
                        checkUpdatesActionText.text = "Open"
                        updateReleaseNotes.visibility = View.GONE
                    }

                    is Updater.Progress.Failure -> {
                        updateStatusText.text = "Update failed"
                        checkUpdatesActionText.text = "Retry"
                        updateReleaseNotes.text = progress.error.message ?: "Unknown error"
                        updateReleaseNotes.visibility = View.VISIBLE
                    }

                    is Updater.Progress.Corrupt -> {
                        updateStatusText.text = "Updater issue"
                        checkUpdatesActionText.text = "Unavailable"
                        updateReleaseNotes.text = "This install cannot use in-app updates."
                        updateReleaseNotes.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(
            navHomeContainer,
            navServersContainer,
            navAccountContainer,
            navSettingsContainer
        )

        navSettingsContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    v.isSelected = true
                    toggleSidebar(true)
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    v.postDelayed({
                        if (navContainers.none { it.hasFocus() }) {
                            navSettingsContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    settingKillSwitch.requestFocus()
                    true
                } else {
                    false
                }
            }

            container.setOnClickListener {
                when (container.id) {
                    R.id.navHomeContainer -> {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }

                    R.id.navServersContainer -> {
                        startActivity(Intent(this, ServerListActivity::class.java))
                    }

                    R.id.navAccountContainer -> {
                        startActivity(Intent(this, AccountActivity::class.java))
                        finish()
                    }

                    R.id.navSettingsContainer -> {
                        // already here
                    }
                }
            }
        }
    }

    private fun animateSettingRow(view: View, hasFocus: Boolean) {
        if (hasFocus) {
            view.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(120)
                .start()
        } else {
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120)
                .start()
        }
    }

    private fun toggleSidebar(expand: Boolean) {
        if (isExpanded == expand) return
        isExpanded = expand

        val targetWidth = if (expand) 240.toPx() else 84.toPx()
        val targetAlpha = if (expand) 1f else 0f

        val widthAnimator = ValueAnimator.ofInt(sideNav.width, targetWidth)
        widthAnimator.addUpdateListener { animator ->
            val params = sideNav.layoutParams
            params.width = animator.animatedValue as Int
            sideNav.layoutParams = params
        }

        widthAnimator.duration = 250
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.start()

        navTexts.forEach { text ->
            text.animate().alpha(targetAlpha).setDuration(200).start()
        }
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()
}