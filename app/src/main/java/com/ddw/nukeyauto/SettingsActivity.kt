package com.ddw.nukeyauto

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.billing.BillingHelper
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.ui.mobile.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var prefs: SharedPreferences
    private val viewModel: SettingsViewModel by viewModels()

    private var billingHelper: BillingHelper? = null

    /** Language tags matching the dropdown items order. */
    private val langTags = arrayOf("en", "nl")

    /** Theme values matching the dropdown items order. */
    private val themeValues = arrayOf(
        PrefKeys.THEME_SYSTEM,
        PrefKeys.THEME_LIGHT,
        PrefKeys.THEME_DARK
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etToken = findViewById<TextInputEditText>(R.id.etToken)
        val etId = findViewById<TextInputEditText>(R.id.etId)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val actvLanguage = findViewById<AutoCompleteTextView>(R.id.actvLanguage)
        val actvTheme = findViewById<AutoCompleteTextView>(R.id.actvTheme)
        val switchConfirm = findViewById<MaterialSwitch>(R.id.switchConfirm)

        // Pre-fill stored credentials
        try {
            etToken.setText(prefs.getString(PrefKeys.NUKI_TOKEN, ""))
            etId.setText(prefs.getString(PrefKeys.NUKI_ID, ""))
        } catch (e: Exception) {
            // EncryptedSharedPreferences can fail if the KeyStore is corrupted
        }

        // Language dropdown adapter
        val langLabels = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_dutch)
        )
        val adapter = ArrayAdapter(this, R.layout.item_dropdown, langLabels)
        actvLanguage.setAdapter(adapter)

        // Select current language in dropdown
        val currentLocale = AppCompatDelegate.getApplicationLocales()
        val currentLang = currentLocale.toLanguageTags()
        val selectedIndex = if (currentLang.startsWith("nl")) 1 else 0
        actvLanguage.setText(langLabels[selectedIndex], false)

        // Theme dropdown adapter
        val themeLabels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        actvTheme.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, themeLabels))

        // Select current theme in dropdown
        val currentTheme = safeGetString(PrefKeys.THEME, PrefKeys.THEME_SYSTEM)
        val themeIndex = themeValues.indexOf(currentTheme).coerceAtLeast(0)
        actvTheme.setText(themeLabels[themeIndex], false)

        // The theme is applied on Save, together with the language — picking one in
        // the dropdown only changes the selection.

        // Dynamic colors switch — default ON, stored on Save like every other setting
        val switchDynamicColors = findViewById<MaterialSwitch>(R.id.switchDynamicColors)
        switchDynamicColors.isChecked = safeGetBoolean(PrefKeys.DYNAMIC_COLORS, true)

        // Confirmation switch — read from prefs, default ON
        switchConfirm.isChecked = safeGetBoolean(PrefKeys.CONFIRM_ACTION, true)

        // Delay slider — read from prefs, default 0 (off)
        val sliderDelay = findViewById<Slider>(R.id.sliderDelay)
        val tvDelayValue = findViewById<TextView>(R.id.tvDelayValue)
        val savedDelay = safeGetInt(PrefKeys.OPEN_DELAY_SECONDS, 0)
        sliderDelay.value = savedDelay.toFloat()
        updateDelayLabel(tvDelayValue, savedDelay)

        sliderDelay.addOnChangeListener { _, value, _ ->
            updateDelayLabel(tvDelayValue, value.toInt())
        }

        // Rate on Play Store
        findViewById<MaterialButton>(R.id.btnRate).setOnClickListener {
            val uri = "market://details?id=${BuildConfig.APPLICATION_ID}".toUri()
            val fallback =
                "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}".toUri()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, fallback))
            }
        }

        // Send feedback
        findViewById<MaterialButton>(R.id.btnFeedback).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.version_label, BuildConfig.VERSION_NAME))
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, "mailto:$FEEDBACK_EMAIL".toUri()))
            }
        }

        // Share app
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                putExtra(
                    Intent.EXTRA_TEXT,
                    getString(R.string.share_text, BuildConfig.APPLICATION_ID)
                )
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        }

        // Version label
        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.version_label, BuildConfig.VERSION_NAME)

        // Support the developer — billing setup
        val cardSupporter = findViewById<MaterialCardView>(R.id.cardSupporter)
        val tvSupporterLabel = findViewById<TextView>(R.id.tvSupporterLabel)
        val tvDonationCount = findViewById<TextView>(R.id.tvDonationCount)
        val supportContainer = findViewById<LinearLayout>(R.id.supportButtonsContainer)
        val tvSupportUnavailable = findViewById<TextView>(R.id.tvSupportUnavailable)

        // Show supporter card if user has donated before
        updateSupporterCard(cardSupporter, tvSupporterLabel, tvDonationCount)

        billingHelper = BillingHelper(this, prefs) { success ->
            runOnUiThread {
                if (success) {
                    updateSupporterCard(cardSupporter, tvSupporterLabel, tvDonationCount)
                    showThankYouDialog()
                } else {
                    Toast.makeText(this, R.string.support_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        billingHelper?.connect()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                billingHelper?.products?.collect { productList ->
                    supportContainer.removeAllViews()
                    if (productList.isEmpty()) {
                        tvSupportUnavailable.visibility = View.VISIBLE
                    } else {
                        tvSupportUnavailable.visibility = View.GONE
                        for (product in productList) {
                            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                            val label = productIdToLabel(product.productId)
                            val button = MaterialButton(
                                this@SettingsActivity,
                                null,
                                com.google.android.material.R.attr.materialButtonOutlinedStyle
                            ).apply {
                                text = getString(R.string.support_button_label, label, price)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    bottomMargin = (8 * resources.displayMetrics.density).toInt()
                                }
                                setOnClickListener {
                                    billingHelper?.launchPurchase(product)
                                }
                            }
                            supportContainer.addView(button)
                        }
                    }
                }
            }
        }

        // Save credentials + language + confirmation preference
        btnSave.setOnClickListener {
            val token = etToken.text.toString().replace("\\s".toRegex(), "")
            val id = etId.text.toString().replace("\\s".toRegex(), "")

            // Preferences do not depend on the credentials, so persist them either
            // way — otherwise an empty token field would silently discard them.
            persistPendingSettings()

            applySelectedTheme(themeLabels, actvTheme.text.toString())
            applySelectedLanguage(langLabels, actvLanguage.text.toString())

            if (token.isEmpty() || id.isEmpty()) {
                Toast.makeText(this, R.string.toast_fill_all_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveCredentials(token, id)
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Write the switches and the delay slider to prefs. Nothing on this screen is
     * stored before Save is pressed, so this is the single place that persists them.
     */
    private fun persistPendingSettings() {
        val switchConfirm = findViewById<MaterialSwitch>(R.id.switchConfirm)
        val switchDynamicColors = findViewById<MaterialSwitch>(R.id.switchDynamicColors)
        val sliderDelay = findViewById<Slider>(R.id.sliderDelay)
        prefs.edit {
            putBoolean(PrefKeys.CONFIRM_ACTION, switchConfirm.isChecked)
            putBoolean(PrefKeys.DYNAMIC_COLORS, switchDynamicColors.isChecked)
            putInt(PrefKeys.OPEN_DELAY_SECONDS, sliderDelay.value.toInt())
        }
    }

    /**
     * Store and apply the theme picked in the dropdown, but only when it differs
     * from the stored one — applying it always would rebuild the screen on every save.
     */
    private fun applySelectedTheme(labels: Array<String>, selectedLabel: String) {
        val themeIndex = labels.indexOf(selectedLabel).coerceAtLeast(0)
        val newTheme = themeValues[themeIndex]
        if (newTheme == safeGetString(PrefKeys.THEME, PrefKeys.THEME_SYSTEM)) return
        prefs.edit { putString(PrefKeys.THEME, newTheme) }
        AppCompatDelegate.setDefaultNightMode(NukiApp.themeToNightMode(newTheme))
    }

    /**
     * Apply the language picked in the dropdown, but only when it differs from
     * the active one — setting it always would recreate the Activity on every save.
     */
    private fun applySelectedLanguage(labels: Array<String>, selectedLabel: String) {
        val langIndex = labels.indexOf(selectedLabel)
        val langTag = if (langIndex >= 0) langTags[langIndex] else "en"
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (!current.startsWith(langTag)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langTag))
        }
    }

    private fun safeGetString(key: String, default: String): String = try {
        prefs.getString(key, default) ?: default
    } catch (e: Exception) {
        default
    }

    private fun safeGetBoolean(key: String, default: Boolean): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (e: Exception) {
        default
    }

    private fun safeGetInt(key: String, default: Int): Int = try {
        prefs.getInt(key, default)
    } catch (e: Exception) {
        default
    }

    override fun onDestroy() {
        billingHelper?.disconnect()
        super.onDestroy()
    }

    /**
     * Show a thank-you dialog after a successful donation.
     */
    private fun showThankYouDialog() {
        val count = BillingHelper.getDonationCount(prefs)
        val message = resources.getQuantityString(R.plurals.supporter_dialog_message, count, count)

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_star_shine)
            .setTitle(R.string.supporter_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Show or hide the supporter card and update the donation count text.
     */
    private fun updateSupporterCard(
        card: MaterialCardView,
        labelView: TextView,
        countView: TextView
    ) {
        if (BillingHelper.isSupporter(prefs)) {
            card.visibility = View.VISIBLE
            labelView.setText(R.string.supporter_status_label)
            val count = BillingHelper.getDonationCount(prefs)
            countView.text = resources.getQuantityString(
                R.plurals.supporter_donation_count, count, count
            )
        } else {
            card.visibility = View.GONE
        }
    }

    /**
     * Map a product ID to a readable label for the support buttons.
     */
    private fun productIdToLabel(productId: String): String {
        return when (productId) {
            BillingHelper.PRODUCT_SMALL -> getString(R.string.support_label_coffee)
            BillingHelper.PRODUCT_MEDIUM -> getString(R.string.support_label_pizza)
            BillingHelper.PRODUCT_LARGE -> getString(R.string.support_label_dinner)
            else -> getString(R.string.support_label_support)
        }
    }

    /**
     * Update the delay label next to the slider.
     */
    private fun updateDelayLabel(view: TextView, seconds: Int) {
        view.text = if (seconds == 0) {
            getString(R.string.settings_delay_off)
        } else {
            resources.getQuantityString(R.plurals.settings_delay_value, seconds, seconds)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val FEEDBACK_EMAIL = "hoolite+nukey@gmail.com"
    }
}
