package com.ailenezareti.nezaretv4.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.nezaretv4.Prefs
import com.ailenezareti.nezaretv4.R
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.FragmentHomeBinding
import com.ailenezareti.nezaretv4.model.CallEntry
import com.ailenezareti.nezaretv4.model.GeoZone
import com.ailenezareti.nezaretv4.model.LocationPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    private var lastLocations: List<LocationPoint> = emptyList()
    private var lastZones: List<GeoZone> = emptyList()
    private var lastCalls: List<CallEntry> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.shortcutMap.setOnClickListener { (activity as? MainActivity)?.navigate(R.id.nav_map) }
        b.shortcutCalls.setOnClickListener { (activity as? MainActivity)?.navigate(R.id.nav_calls) }
        b.shortcutZones.setOnClickListener { (activity as? MainActivity)?.navigate(R.id.nav_zones) }
        b.shortcutAlerts.setOnClickListener { (activity as? MainActivity)?.navigate(R.id.nav_more) }
        b.bellBtn.setOnClickListener { (activity as? MainActivity)?.navigate(R.id.nav_more) }

        b.batteryCard.setOnClickListener { showBatteryAnalysis() }
        b.gpsStatsCard.setOnClickListener { showGpsAnalysis() }
        b.callAnalysisCard.setOnClickListener { showTopCallers() }
        b.zoneCard.setOnClickListener { showZoneAnalysis() }
        b.topNumber.setOnClickListener { showTopCallers() }

        load()
    }

    private fun load() {
        val id = Prefs.child(requireContext())
        if (id < 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(700)
                if (_b != null) load()
            }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.service(requireContext())
                val children = api.children().body()?.children.orEmpty()
                val child = children.firstOrNull { it.id == id } ?: children.firstOrNull()
                b.childName.text = child?.name ?: "Uşaq"
                b.avatar.text = child?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                // children.last_seen server vaxtidir ve panel vaxtindan 1 saat geride gelir.
                b.lastSeen.text = child?.last_seen?.let { "Son görülmə: ${displayServerDateTime(it)}" }
                    ?: "Son görülmə gözlənilir"

                val loc = api.locations(id, "today").body()?.locations.orEmpty().sortedBy { it.recorded_at }
                lastLocations = loc
                val latest = loc.lastOrNull()
                b.todayPoints.text = loc.size.toString()
                b.batteryTop.text = latest?.battery_pct?.let { "▮ $it%" } ?: "—"
                val battery = latest?.battery_pct ?: 0
                b.batteryAnalysis.text = latest?.battery_pct?.let { "$it%" } ?: "—%"
                b.batteryProgress.progress = battery.coerceIn(0, 100)
                b.batteryAnalysisSub.text = when {
                    battery == 0 -> "Məlumat yoxdur"
                    battery <= 20 -> "Aşağı səviyyə"
                    battery <= 50 -> "Orta səviyyə"
                    else -> "Normal səviyyə"
                }
                // location.recorded_at artiq lokal vaxt kimi gelir, əlavə +1 etmirik.
                b.activityLocation.text = latest?.let { "●  Mövqe yeniləndi  •  ${displayLocationTime(it.recorded_at)}" }
                    ?: "●  Mövqe məlumatı yoxdur"

                var meters = 0.0
                for (i in 1 until loc.size) {
                    val d = distance(gp(loc[i - 1]), gp(loc[i]))
                    if (d < 3000) meters += d
                }
                b.todayDistance.text = String.format("%.1f km", meters / 1000.0)

                val today = LocalDate.now().toString()
                val calls = api.calls(id, today, today, "all", null, 1000, 0).body()?.calls.orEmpty()
                lastCalls = calls
                b.todayCalls.text = calls.size.toString()
                val incoming = calls.count { it.call_type.equals("incoming", true) || it.call_type == "1" }
                val outgoing = calls.count { it.call_type.equals("outgoing", true) || it.call_type == "2" }
                val missed = calls.count { it.call_type.equals("missed", true) || it.call_type == "3" }
                b.callAnalysisTotal.text = "${calls.size} zəng"
                b.incomingCount.text = incoming.toString()
                b.outgoingCount.text = outgoing.toString()
                b.missedCount.text = missed.toString()

                val top = groupedCalls(calls).firstOrNull()
                b.topNumber.text = if (top == null) {
                    "Ən çox danışılan: —"
                } else {
                    "Ən çox danışılan: ${top.number} • ${formatDuration(top.totalSeconds)}  ›"
                }

                val latestCall = calls.maxByOrNull { it.occurred_at }
                b.activityCall.text = latestCall?.let {
                    "☎  ${it.contact_name?.takeIf { n -> n.isNotBlank() } ?: it.phone_number}  •  ${displayCallTime(it.occurred_at)}"
                } ?: "☎  Son zəng yoxdur"

                val zones = api.zones(id).body()?.zones.orEmpty()
                lastZones = zones
                val activeZones = zones.filter { it.is_active == 1 }
                val currentZone = latest?.let { l -> activeZones.firstOrNull { inside(l, it) } }
                b.zoneAnalysis.text = currentZone?.name ?: "Zona xaricində"
                val dwell = currentZone?.let { zoneDwellMinutes(loc, it) } ?: 0
                b.zoneAnalysisSub.text = if (currentZone != null) {
                    "Bu gün bu zonada təx. ${formatMinutes(dwell)}"
                } else {
                    "${activeZones.size} aktiv zona"
                }

                val alerts = api.alerts(id).body()?.alerts.orEmpty()
                val zoneAlert = alerts.firstOrNull { it.alert_type.contains("zone", true) }
                b.activityZone.text = zoneAlert?.let { "⌖  ${it.message}" } ?: "⌖  Zona bildirişi yoxdur"
            } catch (_: Exception) {
                // Dashboard mövcud məlumatı saxlasın; bir API xətası bütün ekranı dağıtmasın.
            }
        }
    }

    private data class NumberSummary(
        val number: String,
        val name: String?,
        val calls: List<CallEntry>,
        val totalSeconds: Int,
        val talkCalls: Int
    )

    private fun groupedCalls(calls: List<CallEntry>): List<NumberSummary> {
        return calls.groupBy { normalizePhone(it.phone_number) }
            .mapNotNull { (normalized, list) ->
                if (normalized.isBlank()) return@mapNotNull null
                val latest = list.maxByOrNull { it.occurred_at } ?: list.first()
                val spoken = list.filter { it.duration_sec > 0 }
                NumberSummary(
                    number = latest.phone_number,
                    name = latest.contact_name,
                    calls = list.sortedByDescending { it.occurred_at },
                    totalSeconds = spoken.sumOf { it.duration_sec.coerceAtLeast(0) },
                    talkCalls = spoken.size
                )
            }
            .sortedWith(compareByDescending<NumberSummary> { it.totalSeconds }.thenByDescending { it.calls.size })
    }

    private fun showTopCallers() {
        if (lastCalls.isEmpty()) {
            showTextDialog("Bu gün ən çox danışılan nömrələr", "Bu gün üçün zəng məlumatı yoxdur.")
            return
        }
        val rows = groupedCalls(lastCalls).take(15)
        val text = buildString {
            append("Danışıq müddətinə görə sıralama\n\n")
            rows.forEachIndexed { index, row ->
                append("${index + 1}. ${row.name?.takeIf { it.isNotBlank() } ?: row.number}\n")
                if (!row.name.isNullOrBlank()) append("   ${row.number}\n")
                append("   Danışıq: ${formatDuration(row.totalSeconds)} • ${row.calls.size} zəng")
                if (row.talkCalls != row.calls.size) append(" • ${row.talkCalls} danışıq")
                append("\n\n")
            }
        }
        showTextDialog("Bu gün ən çox danışılan nömrələr", text)
    }

    private fun showGpsAnalysis() {
        if (lastLocations.isEmpty()) {
            showTextDialog("GPS məlumatları", "Bu gün üçün GPS məlumatı yoxdur.")
            return
        }
        val rows = lastLocations.takeLast(120).reversed()
        val text = buildString {
            append("Bu gün: ${lastLocations.size} GPS nöqtəsi\n")
            append("Son nöqtələr yuxarıdadır.\n\n")
            rows.forEachIndexed { index, p ->
                append("${index + 1}. ${displayLocationDateTime(p.recorded_at)}")
                append("   •   Batareya ${p.battery_pct?.let { "$it%" } ?: "—"}\n")
                append("   GPS: ${p.latitude}, ${p.longitude}")
                append("   •   Dəqiqlik ${p.accuracy_m ?: "—"} m\n\n")
            }
        }
        showTextDialog("GPS nöqtələri + batareya", text)
    }

    private fun showBatteryAnalysis() {
        if (lastLocations.isEmpty()) {
            showTextDialog("Batareya + GPS analizi", "Bu gün üçün məlumat yoxdur.")
            return
        }
        val rows = lastLocations.filter { it.battery_pct != null }.takeLast(80)
        val text = buildString {
            val last = rows.lastOrNull()
            append("Son GPS nöqtəsi\n")
            if (last != null) {
                append("${displayLocationDateTime(last.recorded_at)} • ${last.latitude}, ${last.longitude} • ${last.battery_pct}%\n\n")
            }
            append("Batareya dəyişiklikləri\n\n")
            var previous: Int? = null
            rows.forEach { p ->
                val now = p.battery_pct ?: return@forEach
                val arrow = when {
                    previous == null -> "•"
                    now > previous!! -> "↑ qalxdı"
                    now < previous!! -> "↓ düşdü"
                    else -> "→"
                }
                if (previous == null || now != previous) {
                    append("$arrow  ${displayLocationDateTime(p.recorded_at)}   $now%\n")
                    append("GPS ${p.latitude}, ${p.longitude}\n\n")
                }
                previous = now
            }
        }
        showTextDialog("Batareya + GPS analizi", text)
    }

    private fun showZoneAnalysis() {
        if (lastZones.isEmpty()) {
            showTextDialog("Zona analizi", "Aktiv zona məlumatı yoxdur.")
            return
        }
        var totalMeters = 0.0
        for (i in 1 until lastLocations.size) {
            val d = distance(gp(lastLocations[i - 1]), gp(lastLocations[i]))
            if (d < 3000) totalMeters += d
        }
        val active = lastZones.filter { it.is_active == 1 }
        val current = lastLocations.lastOrNull()?.let { p -> active.firstOrNull { inside(p, it) } }
        val text = buildString {
            append("Hazırda: ${current?.name ?: "Zona xaricində"}\n")
            append("Bu gün gedilən yol: ${String.format("%.1f", totalMeters / 1000.0)} km\n\n")
            active.forEach { z ->
                val samples = lastLocations.filter { inside(it, z) }
                val mins = zoneDwellMinutes(lastLocations, z)
                append("${z.name}${if (current?.id == z.id) "  •  İNDİ BURADADIR" else ""}\n")
                append("• Zonada qalma: ${formatMinutes(mins)}\n")
                append("• GPS qeydi: ${samples.size}\n")
                append("• İlk görünmə: ${samples.firstOrNull()?.let { displayLocationDateTime(it.recorded_at) } ?: "—"}\n")
                append("• Son görünmə: ${samples.lastOrNull()?.let { displayLocationDateTime(it.recorded_at) } ?: "—"}\n\n")
            }
        }
        showTextDialog("Zona analizi", text)
    }

    private fun zoneDwellMinutes(loc: List<LocationPoint>, zone: GeoZone): Int {
        if (loc.size < 2) return 0
        var seconds = 0L
        for (i in 1 until loc.size) {
            if (!inside(loc[i - 1], zone) || !inside(loc[i], zone)) continue
            val delta = secondsBetween(loc[i - 1].recorded_at, loc[i].recorded_at)
            if (delta in 1..1800) seconds += delta
        }
        return (seconds / 60L).toInt()
    }

    private fun secondsBetween(a: String, c: String): Long = try {
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        Duration.between(LocalDateTime.parse(a, f), LocalDateTime.parse(c, f)).seconds
    } catch (_: Exception) { 0L }

    private fun inside(p: LocationPoint, z: GeoZone): Boolean = try {
        distance(gp(p), GeoPoint(z.latitude.toDouble(), z.longitude.toDouble())) <= z.radius_m
    } catch (_: Exception) { false }

    private fun gp(p: LocationPoint) = GeoPoint(p.latitude.toDouble(), p.longitude.toDouble())

    private fun distance(a: GeoPoint, c: GeoPoint): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(c.latitude)
        val dp = Math.toRadians(c.latitude - a.latitude)
        val dl = Math.toRadians(c.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun normalizePhone(s: String) = s.filter { it.isDigit() || it == '+' }

    private fun formatDuration(sec: Int): String {
        if (sec <= 0) return "0 san"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}s ${m} dəq ${s} san"
            m > 0 -> "$m dəq $s san"
            else -> "$s san"
        }
    }

    private fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60}s ${m % 60} dəq" else "$m dəq"

    private fun displayServerDateTime(raw: String): String = try {
        LocalDateTime.parse(raw, DATE_TIME).plusHours(1).format(DISPLAY_DATE_TIME)
    } catch (_: Exception) { raw }

    private fun displayLocationDateTime(raw: String): String = try {
        LocalDateTime.parse(raw, DATE_TIME).format(DISPLAY_DATE_TIME)
    } catch (_: Exception) { raw }

    private fun displayLocationTime(raw: String): String = try {
        LocalDateTime.parse(raw, DATE_TIME).format(DISPLAY_TIME)
    } catch (_: Exception) { raw.takeLast(8).take(5) }

    private fun displayCallTime(raw: String): String = try {
        LocalDateTime.parse(raw, DATE_TIME).plusHours(1).format(DISPLAY_TIME)
    } catch (_: Exception) { raw.takeLast(8).take(5) }

    private fun showTextDialog(title: String, text: String) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setPadding(42, 24, 42, 24)
            setTextColor(resources.getColor(R.color.text, null))
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Bağla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        private val DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm")
    }
}
