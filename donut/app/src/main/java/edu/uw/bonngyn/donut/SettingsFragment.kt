package edu.uw.bonngyn.donut

import android.os.Bundle
import android.preference.SwitchPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.util.Log

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var testPref: SwitchPreference;

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)
        //Toggle radius option slider based off the state of radius_toggle
        findPreference("radius_option").isEnabled = preferenceManager.sharedPreferences.getBoolean("radius_option_bool", false)
        val edit = preferenceManager.sharedPreferences.edit()
        findPreference("radius_toggle").setOnPreferenceClickListener {
            if(it.sharedPreferences.getBoolean("radius_toggle", false)) {
                findPreference("radius_option").isEnabled = true
            } else {
                findPreference("radius_option").isEnabled = false
            }
            edit.putBoolean("radius_option_bool", findPreference("radius_option").isEnabled).commit()
            true
        }
    }


}
