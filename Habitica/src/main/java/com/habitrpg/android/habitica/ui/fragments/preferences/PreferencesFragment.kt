package com.habitrpg.android.habitica.ui.fragments.preferences

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceScreen
import com.habitrpg.android.habitica.HabiticaApplication
import com.habitrpg.android.habitica.HabiticaBaseApplication
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.data.InventoryRepository
import com.habitrpg.android.habitica.extensions.notNull
import com.habitrpg.android.habitica.helpers.LanguageHelper
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.helpers.SoundManager
import com.habitrpg.android.habitica.helpers.TaskAlarmManager
import com.habitrpg.android.habitica.helpers.notifications.PushNotificationManager
import com.habitrpg.android.habitica.models.ContentResult
import com.habitrpg.android.habitica.models.user.User
import com.habitrpg.android.habitica.prefs.TimePreference
import com.habitrpg.android.habitica.ui.activities.ClassSelectionActivity
import com.habitrpg.android.habitica.ui.activities.FixCharacterValuesActivity
import com.habitrpg.android.habitica.ui.activities.MainActivity
import io.reactivex.functions.Consumer
import java.util.*
import javax.inject.Inject

class PreferencesFragment : BasePreferencesFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var inventoryRepository: InventoryRepository
    @Inject
    lateinit var soundManager: SoundManager
    @Inject
    lateinit  var pushNotificationManager: PushNotificationManager

    private var timePreference: TimePreference? = null
    private var pushNotificationsPreference: PreferenceScreen? = null
    private var classSelectionPreference: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        HabiticaBaseApplication.component?.inject(this)
        super.onCreate(savedInstanceState)

        val userID = preferenceManager.sharedPreferences.getString(context?.getString(R.string.SP_userID), null)
        if (userID != null) {
            compositeSubscription.add(userRepository.getUser(userID).subscribe(Consumer { this@PreferencesFragment.setUser(it) }, RxErrorHandler.handleEmptyError()))
        }
    }

    override fun setupPreferences() {
        timePreference = findPreference("reminder_time") as TimePreference
        val useReminder = preferenceManager.sharedPreferences.getBoolean("use_reminder", false)
        timePreference?.isEnabled = useReminder


        pushNotificationsPreference = findPreference("pushNotifications") as PreferenceScreen
        val userPushNotifications = preferenceManager.sharedPreferences.getBoolean("usePushNotifications", true)
        pushNotificationsPreference?.isEnabled = userPushNotifications


        classSelectionPreference = findPreference("choose_class")
        classSelectionPreference?.isVisible = false
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

    }

    override fun onPause() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when(preference.key) {
            "logout" -> {
                context.notNull { HabiticaBaseApplication.logout(it) }
                activity?.finish()
            }
            "choose_class" -> {
                val bundle = Bundle()
                bundle.putString("size", user?.preferences?.size)
                bundle.putString("skin", user?.preferences?.skin)
                bundle.putString("shirt", user?.preferences?.shirt)
                bundle.putInt("hairBangs", user?.preferences?.hair?.bangs ?: 0)
                bundle.putInt("hairBase", user?.preferences?.hair?.base ?: 0)
                bundle.putString("hairColor", user?.preferences?.hair?.color)
                bundle.putInt("hairMustache", user?.preferences?.hair?.mustache ?: 0)
                bundle.putInt("hairBeard", user?.preferences?.hair?.beard ?: 0)
                bundle.putBoolean("isInitialSelection", user?.flags?.classSelected == false)

                val intent = Intent(activity, ClassSelectionActivity::class.java)
                intent.putExtras(bundle)

                if (user?.flags?.classSelected == true && user?.preferences?.disableClasses == false) {
                    context.notNull { context ->
                        val builder = AlertDialog.Builder(context)
                                .setMessage(getString(R.string.change_class_confirmation))
                                .setNegativeButton(getString(R.string.dialog_go_back)) { dialog, _ -> dialog.dismiss() }
                                .setPositiveButton(getString(R.string.change_class)) { _, _ -> startActivityForResult(intent, MainActivity.SELECT_CLASS_RESULT) }
                        val alert = builder.create()
                        alert.show()
                    }
                } else {
                    startActivityForResult(intent, MainActivity.SELECT_CLASS_RESULT)
                }
                return true
            }
            "reload_content" -> {
                @Suppress("DEPRECATION")
                val dialog = ProgressDialog.show(context, context?.getString(R.string.reloading_content), null, true)
                inventoryRepository.retrieveContent(true).subscribe({
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                }) { throwable ->
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                    RxErrorHandler.reportError(throwable)
                }
            }
            "fixCharacterValues" -> {
                val intent = Intent(activity, FixCharacterValuesActivity::class.java)
                activity?.startActivity(intent)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }


    @SuppressLint("ObsoleteSdkInt")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "use_reminder" -> {
                val useReminder = sharedPreferences.getBoolean(key, false)
                timePreference?.isEnabled = useReminder
                if (useReminder) {
                    TaskAlarmManager.scheduleDailyReminder(context)
                } else {
                    TaskAlarmManager.removeDailyReminder(context)
                }
            }
            "reminder_time" -> {
                TaskAlarmManager.removeDailyReminder(context)
                TaskAlarmManager.scheduleDailyReminder(context)
            }
            "usePushNotifications" -> {
                val userPushNotifications = sharedPreferences.getBoolean(key, false)
                pushNotificationsPreference?.isEnabled = userPushNotifications
                if (userPushNotifications) {
                    pushNotificationManager.addPushDeviceUsingStoredToken()
                } else {
                    pushNotificationManager.removePushDeviceUsingStoredToken()
                }
            }
            "cds_time" -> {
                val timeval = sharedPreferences.getString("cds_time", "00:00")
                val pieces = timeval?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
                if (pieces != null) {
                    val hour = Integer.parseInt(pieces[0])
                    userRepository.changeCustomDayStart(hour).subscribe(Consumer { }, RxErrorHandler.handleEmptyError())
                }
            }
            "language" -> {
                val languageHelper = LanguageHelper(sharedPreferences.getString(key, "en"))

                Locale.setDefault(languageHelper.locale)
                val configuration = Configuration()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
                    @Suppress("Deprecation")
                    configuration.locale = languageHelper.locale
                } else {
                    configuration.setLocale(languageHelper.locale)
                }
                @Suppress("DEPRECATION")
                activity?.resources?.updateConfiguration(configuration, activity?.resources?.displayMetrics)
                userRepository.updateLanguage(user, languageHelper.languageCode)
                        .flatMap<ContentResult> { inventoryRepository.retrieveContent(true) }
                        .subscribe(Consumer { }, RxErrorHandler.handleEmptyError())

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    val intent = Intent(activity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                } else {
                    val intent = Intent(activity, MainActivity::class.java)
                    this.startActivity(intent)
                    activity?.finishAffinity()
                }
            }
            "audioTheme" -> {
                val newAudioTheme = sharedPreferences.getString(key, "off")
                userRepository.updateUser(user, "preferences.sound", newAudioTheme)
                        .subscribe(Consumer { }, RxErrorHandler.handleEmptyError())
                soundManager.soundTheme = newAudioTheme
                soundManager.preloadAllFiles()
            }
            "dailyDueDefaultView" -> userRepository.updateUser(user, "preferences.dailyDueDefaultView", sharedPreferences.getBoolean(key, false))
                    .subscribe(Consumer { }, RxErrorHandler.handleEmptyError())
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is TimePreference) {
            if (preference.getKey() == "cds_time") {
                if (fragmentManager?.findFragmentByTag(DayStartPreferenceDialogFragment.TAG) == null) {
                    DayStartPreferenceDialogFragment.newInstance(this, preference.getKey())
                            .show(fragmentManager, DayStartPreferenceDialogFragment.TAG)
                }
            } else {
                if (fragmentManager?.findFragmentByTag(TimePreferenceDialogFragment.TAG) == null) {
                    TimePreferenceDialogFragment.newInstance(this, preference.getKey())
                            .show(fragmentManager, TimePreferenceDialogFragment.TAG)
                }
            }
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    fun setUser(user: User?) {
        this.user = user
        if (10 <= user?.stats?.lvl ?: 0) {
            if (user?.flags?.classSelected == true) {
                if (user.preferences?.disableClasses == true) {
                    classSelectionPreference?.title = getString(R.string.enable_class)
                } else {
                    classSelectionPreference?.title = getString(R.string.change_class)
                    classSelectionPreference?.summary = getString(R.string.change_class_description)
                }
                classSelectionPreference?.isVisible = true
            } else {
                classSelectionPreference?.title = getString(R.string.enable_class)
                classSelectionPreference?.isVisible = true
            }
        }
        val cdsTimePreference = findPreference("cds_time") as? TimePreference
        cdsTimePreference?.text = user?.preferences?.dayStart.toString() + ":00"
        findPreference("dailyDueDefaultView").setDefaultValue(user?.preferences?.dailyDueDefaultView)
    }
}
