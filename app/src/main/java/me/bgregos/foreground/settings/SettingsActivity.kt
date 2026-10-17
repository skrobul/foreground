package me.bgregos.foreground.settings

import me.bgregos.foreground.BuildConfig

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.databinding.ActivitySettingsBinding
import me.bgregos.foreground.network.TaskwarriorSyncWorker
import me.bgregos.foreground.util.ShowErrorDetail
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var localTasksRepository: TaskRepository
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.settingsSyncprogress.visibility = View.INVISIBLE
        binding.settingsSync.visibility = View.VISIBLE
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(true)

        //load preferences
        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        binding.settingsSync.isChecked = prefs.getBoolean("settings_sync", false)
        binding.settingsAutoSync.isChecked = prefs.getBoolean("settings_auto_sync", false)
        binding.settingsAutoSyncInterval.setText(prefs.getString("settings_auto_sync_interval", "60"))

        // TaskChampion specific settings
        binding.settingsTaskchampionServer.setText(prefs.getString("settings_taskchampion_server", null).orEmpty().ifBlank { BuildConfig.TEST_SYNC_SERVER })
        binding.settingsTaskchampionClientId.setText(prefs.getString("settings_taskchampion_client_id", null).orEmpty().ifBlank { BuildConfig.TEST_SYNC_CLIENT_ID })
        binding.settingsTaskchampionEncryptionSecret.setText(prefs.getString("settings_taskchampion_encryption_secret", null).orEmpty().ifBlank { BuildConfig.TEST_SYNC_SECRET })

        binding.settingsSync.setOnClickListener {

            if (binding.settingsSync.isChecked) {
                attemptInitialSync()
            } else {
                binding.settingsAutoSync.isChecked = false
                //TODO: cancel the auto sync work specifically
                WorkManager.getInstance(this).cancelAllWork()
                CoroutineScope(Dispatchers.Main).launch {
                    localTasksRepository.disableSync()
                }
            }
        }

        binding.settingsAutoSync.setOnClickListener { onAutoSyncClicked() }
        binding.settingsAutoSync.setOnFocusChangeListener { _, focused -> if(!focused) onAutoSyncIntervalChanged() }
        binding.settingsDefaultTags.setText(prefs.getString("settings_default_tags", ""))
    }

    fun onAutoSyncIntervalChanged(){
        val interval: Long = binding.settingsAutoSyncInterval.text.toString().toLong()
        if (interval < 15){
            binding.settingsAutoSyncInterval.setText("15")
        }
    }

    private fun attemptInitialSync(){
        binding.settingsEnableSyncText.text = "Testing Sync"
        binding.settingsSync.isChecked = false
        binding.settingsSync.visibility = View.GONE
        save()
        binding.settingsSyncprogress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            var response: SyncResult
            var exception: Exception? = null
            try {
                response = localTasksRepository.testSync()
            }catch (e: Exception){
                exception = e
                Log.e("test sync error", e.toString())
                response = SyncResult(false, "Invalid or incomplete configuration.")
            }
            Log.i(this.javaClass.toString(), response.message)
            binding.settingsSync.visibility = View.VISIBLE
            binding.settingsEnableSyncText.text = "Enable Sync"
            binding.settingsSyncprogress.visibility = View.INVISIBLE
            if (response.success) {
                createSnackbar(getString(R.string.sync_enabled_message), Snackbar.LENGTH_SHORT)
                binding.settingsSync.isChecked = true
            }else{
                val bar = Snackbar.make(binding.settingsParent, response.message, Snackbar.LENGTH_LONG)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.setAction("Details", ShowErrorDetail(exception?.toString() ?: response.message, this@SettingsActivity))
                bar.setActionTextColor(Color.WHITE)
                bar.show()
            }
        }
    }

    fun onAutoSyncClicked(){
        if(binding.settingsAutoSync.isChecked && !binding.settingsSync.isChecked) {
            createSnackbar(getString(R.string.auto_sync_requirement), Snackbar.LENGTH_SHORT)
            binding.settingsAutoSync.isChecked = false
            return
        }
        if(binding.settingsAutoSync.isChecked){
            var interval: Long = binding.settingsAutoSyncInterval.text.toString().toLong()
            if (interval < 15){
                interval = 15
                binding.settingsAutoSyncInterval.setText("15")
            }
            val syncRequest =
                    PeriodicWorkRequest.Builder(TaskwarriorSyncWorker::class.java, interval, TimeUnit.MINUTES)
                            .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("foreground_sync", ExistingPeriodicWorkPolicy.REPLACE, syncRequest)

        }else{
            WorkManager.getInstance(this).cancelAllWork()
        }
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    fun save() {
        // Store all app settings
        val sp = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = sp.edit()

        editor.putBoolean("settings_sync", binding.settingsSync.isChecked)
        editor.putBoolean("settings_auto_sync", binding.settingsAutoSync.isChecked)
        editor.putString("settings_auto_sync_interval", binding.settingsAutoSyncInterval.text.toString())
        editor.remove("settings_address")
        editor.remove("settings_port")
        editor.remove("settings_credentials")
        editor.remove("settings_cert_ca")
        editor.remove("settings_cert_key")
        editor.remove("settings_cert_private")
        editor.putString("settings_default_tags", binding.settingsDefaultTags.text.toString())

        // TaskChampion specific settings
        editor.putString("settings_taskchampion_server", binding.settingsTaskchampionServer.text.toString())
        editor.putString("settings_taskchampion_client_id", binding.settingsTaskchampionClientId.text.toString())
        editor.putString("settings_taskchampion_encryption_secret", binding.settingsTaskchampionEncryptionSecret.text.toString())

        editor.apply()
    }

    private fun createSnackbar(text: String, length: Int){
        val bar = Snackbar.make(binding.settingsParent, text, length)
        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
        bar.show()
    }
}
