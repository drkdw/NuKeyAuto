package com.ddw.nukeyauto

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.ddw.nukeyauto.data.repo.NukiRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var repository: NukiRepository
    @Inject lateinit var prefs: SharedPreferences

    /** Language tags matching the dropdown items order. */
    private val langTags = arrayOf("en", "nl")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etToken = findViewById<TextInputEditText>(R.id.etToken)
        val etId = findViewById<TextInputEditText>(R.id.etId)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val actvLanguage = findViewById<AutoCompleteTextView>(R.id.actvLanguage)
        val switchConfirm = findViewById<MaterialSwitch>(R.id.switchConfirm)

        // Pre-fill stored credentials
        try {
            etToken.setText(prefs.getString("nuki_token", ""))
            etId.setText(prefs.getString("nuki_id", ""))
        } catch (e: Exception) {
            // EncryptedSharedPreferences can fail if the KeyStore is corrupted
        }

        // Language dropdown adapter
        val langLabels = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_dutch)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, langLabels)
        actvLanguage.setAdapter(adapter)

        // Select current language in dropdown
        val currentLocale = AppCompatDelegate.getApplicationLocales()
        val currentLang = currentLocale.toLanguageTags()
        val selectedIndex = if (currentLang.startsWith("nl")) 1 else 0
        actvLanguage.setText(langLabels[selectedIndex], false)

        // Confirmation switch — read from prefs, default ON
        switchConfirm.isChecked = prefs.getBoolean(PREF_CONFIRM_ACTION, true)

        // Version label
        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME

        // Save credentials + language + confirmation preference
        btnSave.setOnClickListener {
            val token = etToken.text.toString().replace("\\s".toRegex(), "")
            val id = etId.text.toString().replace("\\s".toRegex(), "")

            if (token.isNotEmpty() && id.isNotEmpty()) {
                repository.saveCredentials(token, id)

                // Save confirmation preference
                prefs.edit().putBoolean(PREF_CONFIRM_ACTION, switchConfirm.isChecked).apply()

                // Apply language selection on save
                val langIndex = langLabels.indexOf(actvLanguage.text.toString())
                val langTag = if (langIndex >= 0) langTags[langIndex] else "en"
                val locales = LocaleListCompat.forLanguageTags(langTag)
                AppCompatDelegate.setApplicationLocales(locales)

                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, R.string.toast_fill_all_fields, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val PREF_CONFIRM_ACTION = "confirm_action"
    }
}
