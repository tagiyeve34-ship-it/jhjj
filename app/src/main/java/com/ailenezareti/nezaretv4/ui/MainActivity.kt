package com.ailenezareti.nezaretv4.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.nezaretv4.AppLock
import com.ailenezareti.nezaretv4.Prefs
import com.ailenezareti.nezaretv4.R
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.ActivityMainBinding
import com.ailenezareti.nezaretv4.model.PushRegisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var firstStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestNotificationPermissionIfNeeded()
        if (savedInstanceState == null) show(HomeFragment())
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(HomeFragment())
                R.id.nav_map -> show(MapFragment())
                R.id.nav_calls -> show(CallsFragment())
                R.id.nav_zones -> show(ZonesFragment())
                else -> show(MoreFragment())
            }
            true
        }
        loadInitialChild()
        registerPushToken()
    }

    fun navigate(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    override fun onStart() {
        super.onStart()
        if (!firstStart && !AppLock.unlocked) {
            startActivity(Intent(this, PinActivity::class.java))
            finish()
            return
        }
        firstStart = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.unlocked = false
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
    }

    private fun show(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.content, fragment).commit()
    }

    private fun loadInitialChild() {
        lifecycleScope.launch {
            try {
                val firstChild = ApiClient.service(this@MainActivity).children().body()?.children?.firstOrNull()
                if (firstChild != null && Prefs.child(this@MainActivity) < 0) Prefs.setChild(this@MainActivity, firstChild.id)
            } catch (_: Exception) {}
        }
    }

    private fun registerPushToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Prefs.setFcm(this, token)
            lifecycleScope.launch {
                try { ApiClient.service(this@MainActivity).registerPush(PushRegisterRequest(token)) } catch (_: Exception) {}
            }
        }
    }
}
