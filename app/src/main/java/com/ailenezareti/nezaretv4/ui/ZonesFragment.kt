package com.ailenezareti.nezaretv4.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ailenezareti.nezaretv4.Prefs
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.FragmentZonesBinding
import com.ailenezareti.nezaretv4.databinding.ItemZoneBinding
import com.ailenezareti.nezaretv4.model.GeoZone
import com.ailenezareti.nezaretv4.model.ZoneDeleteRequest
import com.ailenezareti.nezaretv4.model.ZoneSaveRequest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class ZonesFragment : Fragment() {
    private var _b: FragmentZonesBinding? = null
    private val b get() = _b!!
    private val adapter = ZoneAdapter({ z,on -> toggle(z,on) }, { z -> detail(z) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentZonesBinding.inflate(inflater, container, false); return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        b.zoneMap.setTileSource(TileSourceFactory.MAPNIK); b.zoneMap.setMultiTouchControls(true); b.zoneMap.controller.setZoom(13.5)
        b.list.layoutManager = LinearLayoutManager(requireContext()); b.list.adapter = adapter
        b.add.setOnClickListener { addDialog() }
        load()
    }

    private fun load() {
        val id=Prefs.child(requireContext()); if(id<0)return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val zones=ApiClient.service(requireContext()).zones(id).body()?.zones.orEmpty()
                adapter.items=zones; adapter.notifyDataSetChanged(); drawZones(zones)
            } catch (_:Exception) {}
        }
    }

    private fun drawZones(zones:List<GeoZone>) {
        b.zoneMap.overlays.clear()
        val colors=listOf("#2F6BFF","#18B96B","#F44E59")
        zones.forEachIndexed { i,z ->
            val p=GeoPoint(z.latitude.toDouble(),z.longitude.toDouble()); val c=Color.parseColor(colors[i%colors.size])
            val poly=Polygon().apply { points=Polygon.pointsAsCircle(p,z.radius_m.toDouble()); fillPaint.color=(c and 0x00FFFFFF) or 0x33000000; outlinePaint.color=c; outlinePaint.strokeWidth=3f }
            val m=Marker(b.zoneMap).apply { position=p; title=z.name; snippet="Radius: ${z.radius_m} m"; setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM) }
            b.zoneMap.overlays.add(poly); b.zoneMap.overlays.add(m)
        }
        zones.firstOrNull()?.let { b.zoneMap.controller.setCenter(GeoPoint(it.latitude.toDouble(),it.longitude.toDouble())) }
        b.zoneMap.invalidate()
    }

    private fun toggle(z:GeoZone,on:Boolean) { viewLifecycleOwner.lifecycleScope.launch { try { ApiClient.service(requireContext()).updateZone(ZoneSaveRequest(z.id,z.child_id,z.name,z.latitude.toDouble(),z.longitude.toDouble(),z.radius_m,z.notify_enter==1,z.notify_exit==1,on)); load() } catch (_:Exception){} } }

    private fun detail(z:GeoZone) {
        AlertDialog.Builder(requireContext()).setTitle(z.name).setMessage("Status: ${if(z.is_active==1)"Aktiv" else "Qeyri-aktiv"}\n\nKoordinat:\n${z.latitude}, ${z.longitude}\n\nRadius: ${z.radius_m} m\n\nDaxil olma bildirişi: ${if(z.notify_enter==1)"Açıq" else "Bağlı"}\nÇıxma bildirişi: ${if(z.notify_exit==1)"Açıq" else "Bağlı"}")
            .setPositiveButton("Bağla",null).setNegativeButton("Sil") { _,_-> viewLifecycleOwner.lifecycleScope.launch { try { ApiClient.service(requireContext()).deleteZone(ZoneDeleteRequest(z.id,z.child_id)); load() } catch (_:Exception){} } }.show()
    }

    private fun addDialog() {
        val box=LinearLayout(requireContext()).apply { orientation=LinearLayout.VERTICAL; setPadding(36,8,36,0) }
        fun inp(h:String)=EditText(requireContext()).also{it.hint=h;box.addView(it)}
        val name=inp("Zona adı"); val lat=inp("Latitude"); val lon=inp("Longitude"); val rad=inp("Radius, metr")
        AlertDialog.Builder(requireContext()).setTitle("Yeni zona").setView(box).setNegativeButton("Ləğv et",null).setPositiveButton("Yadda saxla") { _,_->
            val id=Prefs.child(requireContext()); viewLifecycleOwner.lifecycleScope.launch { try { ApiClient.service(requireContext()).createZone(ZoneSaveRequest(child_id=id,name=name.text.toString(),latitude=lat.text.toString().toDouble(),longitude=lon.text.toString().toDouble(),radius_m=rad.text.toString().toInt(),notify_enter=true,notify_exit=true)); load() } catch (_:Exception){} }
        }.show()
    }

    override fun onResume(){super.onResume();b.zoneMap.onResume()}
    override fun onPause(){b.zoneMap.onPause();super.onPause()}
    override fun onDestroyView(){super.onDestroyView();_b=null}

    private class ZoneAdapter(val changed:(GeoZone,Boolean)->Unit,val clicked:(GeoZone)->Unit):RecyclerView.Adapter<ZoneAdapter.H>(){
        var items:List<GeoZone> = emptyList(); class H(val b:ItemZoneBinding):RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(p:ViewGroup,v:Int)=H(ItemZoneBinding.inflate(LayoutInflater.from(p.context),p,false))
        override fun getItemCount()=items.size
        override fun onBindViewHolder(h:H,p:Int){val z=items[p];h.b.name.text=z.name;h.b.status.text=if(z.is_active==1)"Aktiv" else "Qeyri-aktiv";h.b.meta.text="Radius: ${z.radius_m} m";h.b.toggle.setOnCheckedChangeListener(null);h.b.toggle.isChecked=z.is_active==1;h.b.toggle.setOnCheckedChangeListener{_,on->changed(z,on)};h.b.root.setOnClickListener{clicked(z)}}
    }
}
