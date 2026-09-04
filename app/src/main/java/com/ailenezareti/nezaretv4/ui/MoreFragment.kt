package com.ailenezareti.nezaretv4.ui
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.nezaretv4.Prefs
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.FragmentMoreBinding
import kotlinx.coroutines.launch
class MoreFragment:Fragment(){private var _b:FragmentMoreBinding?=null;private val b get()=_b!!;override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentMoreBinding.inflate(i,c,false).also{_b=it}.root
 override fun onViewCreated(v:View,s:Bundle?){showAlerts();b.alertsTab.setOnClickListener{showAlerts()};b.settingsTab.setOnClickListener{showSettings()};b.logout.setOnClickListener{Prefs.logout(requireContext());startActivity(Intent(requireContext(),LoginActivity::class.java));requireActivity().finish()};b.darkMode.isChecked=(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;b.darkMode.setOnCheckedChangeListener{_,on->AppCompatDelegate.setDefaultNightMode(if(on)AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)};load()}
 private fun showAlerts(){b.alertsPane.visibility=View.VISIBLE;b.settingsPane.visibility=View.GONE;b.alertsTab.setTextColor(resources.getColor(com.ailenezareti.nezaretv4.R.color.white,null));b.alertsTab.backgroundTintList=android.content.res.ColorStateList.valueOf(resources.getColor(com.ailenezareti.nezaretv4.R.color.purple,null));b.settingsTab.setTextColor(resources.getColor(com.ailenezareti.nezaretv4.R.color.text,null));b.settingsTab.backgroundTintList=android.content.res.ColorStateList.valueOf(resources.getColor(com.ailenezareti.nezaretv4.R.color.surface2,null))}
 private fun showSettings(){b.alertsPane.visibility=View.GONE;b.settingsPane.visibility=View.VISIBLE;b.settingsTab.setTextColor(resources.getColor(com.ailenezareti.nezaretv4.R.color.white,null));b.settingsTab.backgroundTintList=android.content.res.ColorStateList.valueOf(resources.getColor(com.ailenezareti.nezaretv4.R.color.purple,null));b.alertsTab.setTextColor(resources.getColor(com.ailenezareti.nezaretv4.R.color.text,null));b.alertsTab.backgroundTintList=android.content.res.ColorStateList.valueOf(resources.getColor(com.ailenezareti.nezaretv4.R.color.surface2,null))}
 private fun load(){val id=Prefs.child(requireContext());viewLifecycleOwner.lifecycleScope.launch{try{val api=ApiClient.service(requireContext());val ch=api.children().body()?.children.orEmpty().firstOrNull{it.id==id};b.settingsName.text=ch?.name?:"Nezaret hesabı";b.settingsEmail.text="Tətbiq və hesab parametrləri";val a=api.alerts(id).body()?.alerts.orEmpty().take(20);b.alertsText.text=if(a.isEmpty())"Yeni bildiriş yoxdur" else a.joinToString("\n\n"){val icon=when{it.alert_type.contains("zone",true)->"⌂";it.alert_type.contains("call",true)->"☎";else->"●"};"$icon  ${it.message}\n     ${it.created_at}"}}catch(_:Exception){b.alertsText.text="Bildirişlər yüklənmədi"}}}
 override fun onDestroyView(){super.onDestroyView();_b=null}}