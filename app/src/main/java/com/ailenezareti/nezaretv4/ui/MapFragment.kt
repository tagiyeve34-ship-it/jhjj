package com.ailenezareti.nezaretv4.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.nezaretv4.Prefs
import com.ailenezareti.nezaretv4.R
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.FragmentMapBinding
import com.ailenezareti.nezaretv4.model.LocationPoint
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MapFragment : Fragment() {
    private var _b: FragmentMapBinding? = null
    private val b get() = _b!!

    private var latestPoint: GeoPoint? = null
    private var latestRecord: LocationPoint? = null
    private var points: List<LocationPoint> = emptyList()
    private var routeShown = false
    private var satellite = false
    private var routeHours = 1
    private var routeMode = RouteMode.GPS_POINTS
    private lateinit var sheet: BottomSheetBehavior<View>

    private enum class RouteMode { GPS_POINTS, STOP_MARKERS }

    private val esri: ITileSource = object : XYTileSource(
        "Esri", 0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(index: Long): String {
            val z = MapTileIndex.getZoom(index)
            val x = MapTileIndex.getX(index)
            val y = MapTileIndex.getY(index)
            return "$baseUrl$z/$y/$x$mImageFilenameEnding"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentMapBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        b.map.setTileSource(TileSourceFactory.MAPNIK)
        b.map.setMultiTouchControls(true)
        b.map.controller.setZoom(20.0)

        sheet = BottomSheetBehavior.from(b.bottomSheet)
        sheet.peekHeight = (54 * resources.displayMetrics.density).toInt()
        sheet.isHideable = false
        sheet.isFitToContents = true
        sheet.state = BottomSheetBehavior.STATE_COLLAPSED

        // User kartina basanda aşağı panel açılır.
        b.mapUserCard.setOnClickListener { sheet.state = BottomSheetBehavior.STATE_EXPANDED }
        b.layers.setOnClickListener {
            satellite = !satellite
            b.map.setTileSource(if (satellite) esri else TileSourceFactory.MAPNIK)
            b.map.invalidate()
        }
        b.target.setOnClickListener {
            latestPoint?.let {
                b.map.controller.animateTo(it)
                b.map.controller.setZoom(20.0)
            }
        }
        b.refresh.setOnClickListener { loadLatest() }
        b.route.setOnClickListener { showRouteOptions() }
        b.history.setOnClickListener { pickDate() }
        b.share.setOnClickListener { shareCurrent() }
        b.googleRoute.setOnClickListener { openNavigation() }

        loadLatest()
    }

    private fun loadLatest() {
        val id = Prefs.child(requireContext())
        if (id < 0) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.service(requireContext())
                val child = api.children().body()?.children?.firstOrNull { it.id == id }
                b.mapChild.text = child?.name ?: "Uşaq"
                b.mapAvatar.text = child?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

                val today = api.locations(id, "today").body()?.locations.orEmpty().sortedBy { it.recorded_at }
                if (today.isEmpty()) {
                    Toast.makeText(requireContext(), "GPS məlumatı yoxdur", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                latestRecord = today.last()
                points = listOf(today.last())
                routeShown = false
                latestPoint = gp(today.last())
                updateHeader(today.last())
                drawMap()
                b.map.controller.setCenter(latestPoint)
                b.map.controller.setZoom(20.0)
                b.route.text = "Marşrut"
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Xəritə məlumatı yüklənmədi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRouteOptions() {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()

        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), dp(4))
        }

        wrap.addView(TextView(context).apply {
            text = "Vaxt intervalı"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text, null))
            setPadding(0, dp(8), 0, dp(4))
        })

        val timeGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val options = listOf(1 to "Son 1 saat (default)", 3 to "Son 3 saat", 6 to "Son 6 saat", 12 to "Son 12 saat", 24 to "Son 24 saat")
        options.forEach { (hours, label) ->
            timeGroup.addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = label
                tag = hours
                isChecked = hours == routeHours
                setTextColor(resources.getColor(R.color.text, null))
            })
        }
        wrap.addView(timeGroup)

        wrap.addView(TextView(context).apply {
            text = "Xəritədə göstər"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text, null))
            setPadding(0, dp(12), 0, dp(4))
        })

        val modeGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val gpsRadio = RadioButton(context).apply {
            id = View.generateViewId()
            text = "GPS nöqtələri"
            isChecked = routeMode == RouteMode.GPS_POINTS
            setTextColor(resources.getColor(R.color.text, null))
        }
        val stopRadio = RadioButton(context).apply {
            id = View.generateViewId()
            text = "Dayanma markerləri"
            isChecked = routeMode == RouteMode.STOP_MARKERS
            setTextColor(resources.getColor(R.color.text, null))
        }
        modeGroup.addView(gpsRadio)
        modeGroup.addView(stopRadio)
        wrap.addView(modeGroup)

        AlertDialog.Builder(context)
            .setTitle("Marşrut")
            .setView(wrap)
            .setNegativeButton("Ləğv et", null)
            .setPositiveButton("Göstər") { _, _ ->
                val selectedTime = timeGroup.findViewById<RadioButton>(timeGroup.checkedRadioButtonId)
                routeHours = selectedTime?.tag as? Int ?: 1
                routeMode = if (modeGroup.checkedRadioButtonId == stopRadio.id) RouteMode.STOP_MARKERS else RouteMode.GPS_POINTS
                loadRoute(routeHours)
            }
            .show()
    }

    private fun loadRoute(hours: Int) {
        val id = Prefs.child(requireContext())
        val last = latestRecord ?: return
        val end = parseRaw(last.recorded_at) ?: return
        val from = end.minusHours(hours.toLong())

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = ApiClient.service(requireContext()).locations(
                    id,
                    "custom",
                    from.format(RAW),
                    end.format(RAW)
                ).body()?.locations.orEmpty().sortedBy { it.recorded_at }

                if (result.isEmpty()) {
                    Toast.makeText(requireContext(), "Seçilən intervalda GPS yoxdur", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                points = result
                routeShown = true
                b.route.text = "Marşrut • ${hours}s"
                drawMap()
                fitRoute(result)
                sheet.state = BottomSheetBehavior.STATE_COLLAPSED
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Marşrut yüklənmədi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            loadDateRoute(date)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadDateRoute(date: String) {
        val id = Prefs.child(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = ApiClient.service(requireContext()).locations(
                    id,
                    "custom",
                    "$date 00:00:00",
                    "$date 23:59:59"
                ).body()?.locations.orEmpty().sortedBy { it.recorded_at }

                if (result.isEmpty()) {
                    Toast.makeText(requireContext(), "Bu tarix üçün GPS yoxdur", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                points = result
                routeShown = true
                b.route.text = "Marşrut"
                drawMap("$date • tarixçə")
                fitRoute(result)
                sheet.state = BottomSheetBehavior.STATE_COLLAPSED
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Tarixçə yüklənmədi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateHeader(last: LocationPoint) {
        b.mapStatus.text = "Son mövqe • ${displayLocationDateTime(last.recorded_at)}"
        b.mapBattery.text = last.battery_pct?.let { "▮ $it%" } ?: "—"
        b.mapAddress.text = "${"%.5f".format(last.latitude.toDouble())}, ${"%.5f".format(last.longitude.toDouble())}"
        b.mapMeta.text = "Dəqiqlik ${last.accuracy_m ?: "—"} m • ${displayLocationDateTime(last.recorded_at)} • Batareya ${last.battery_pct ?: "—"}%"
    }

    private fun drawMap(customSummary: String? = null) {
        if (_b == null) return
        b.map.overlays.clear()

        if (routeShown && points.size > 1) {
            val filtered = filterGps(points)
            if (filtered.size > 1) {
                val routeGeo = filtered.map(::gp)
                b.map.overlays.add(Polyline().apply {
                    setPoints(routeGeo)
                    outlinePaint.color = Color.parseColor("#2478F3")
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                })

                b.map.overlays.add(Marker(b.map).apply {
                    position = routeGeo.first()
                    title = "Başlanğıc"
                    snippet = displayLocationDateTime(filtered.first().recorded_at)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = dot("#17C979", 30)
                })

                when (routeMode) {
                    RouteMode.GPS_POINTS -> addGpsMarkers(filtered)
                    RouteMode.STOP_MARKERS -> addStopMarkers(filtered)
                }

                val km = routeDistanceKm(filtered)
                b.mapSummary.text = customSummary
                    ?: "Son $routeHours saat • ${String.format("%.1f km", km)} • ${filtered.size} GPS nöqtə • ${if (routeMode == RouteMode.GPS_POINTS) "GPS nöqtələri" else "dayanma markerləri"}"
            }
        } else {
            b.mapSummary.text = "User kartına basıb paneli aç • Marşrut seçəndə default son 1 saat göstərilir"
        }

        val last = latestRecord
        val p = latestPoint
        if (last != null && p != null) {
            b.map.overlays.add(Marker(b.map).apply {
                position = p
                title = b.mapChild.text.toString()
                snippet = "Son GPS • ${displayLocationDateTime(last.recorded_at)} • Batareya ${last.battery_pct ?: "—"}%"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = avatar(b.mapAvatar.text.toString())
            })
        }
        b.map.invalidate()
    }

    private fun addGpsMarkers(data: List<LocationPoint>) {
        // Çox sıx olduqda da xəritə oxunaqlı qalsın, amma son nöqtə həmişə daxil olsun.
        val step = max(1, data.size / 120)
        for (i in data.indices step step) {
            val x = data[i]
            b.map.overlays.add(Marker(b.map).apply {
                position = gp(x)
                title = "GPS nöqtəsi ${i + 1}"
                snippet = "${displayLocationDateTime(x.recorded_at)} • Dəqiqlik ${x.accuracy_m ?: "—"} m • Batareya ${x.battery_pct ?: "—"}%"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = dot("#2478F3", 20)
            })
        }
    }

    private fun addStopMarkers(data: List<LocationPoint>) {
        if (data.size < 2) return
        var start = 0
        for (i in 1..data.size) {
            val atEnd = i == data.size
            val moved = if (!atEnd) dist(gp(data[start]), gp(data[i])) > 50 else true
            if (moved) {
                if (i - start > 1) {
                    val secs = timeDiff(data[start].recorded_at, data[i - 1].recorded_at)
                    if (secs >= 180) {
                        val m = (secs / 60).roundToInt()
                        b.map.overlays.add(Marker(b.map).apply {
                            position = gp(data[start])
                            title = "Dayanma • ${formatMinutes(m)}"
                            snippet = "${displayLocationDateTime(data[start].recorded_at)} → ${displayLocationDateTime(data[i - 1].recorded_at)} • Batareya ${data[i - 1].battery_pct ?: "—"}%"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = dot("#F79009", 30)
                        })
                    }
                }
                start = i.coerceAtMost(data.lastIndex)
            }
        }
    }

    private fun filterGps(source: List<LocationPoint>): List<LocationPoint> {
        if (source.size < 2) return source
        val out = mutableListOf<LocationPoint>()
        var prev = source.first()
        out.add(prev)
        for (i in 1 until source.size) {
            val cur = source[i]
            val d = dist(gp(prev), gp(cur))
            val sec = timeDiff(prev.recorded_at, cur.recorded_at)
            val speed = if (sec > 0) d / sec * 3.6 else 0.0
            if (d <= 3000 || speed <= 180) {
                out.add(cur)
                prev = cur
            }
        }
        return out
    }

    private fun routeDistanceKm(data: List<LocationPoint>): Double {
        var meters = 0.0
        for (i in 1 until data.size) {
            val d = dist(gp(data[i - 1]), gp(data[i]))
            if (d < 3000) meters += d
        }
        return meters / 1000.0
    }

    private fun fitRoute(data: List<LocationPoint>) {
        if (data.isEmpty()) return
        if (data.size == 1) {
            b.map.controller.setCenter(gp(data.first()))
            b.map.controller.setZoom(20.0)
            return
        }
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        data.forEach {
            val p = gp(it)
            minLat = kotlin.math.min(minLat, p.latitude)
            maxLat = kotlin.math.max(maxLat, p.latitude)
            minLon = kotlin.math.min(minLon, p.longitude)
            maxLon = kotlin.math.max(maxLon, p.longitude)
        }
        try {
            val box = org.osmdroid.util.BoundingBox(maxLat, maxLon, minLat, minLon)
            b.map.zoomToBoundingBox(box, true, 90)
        } catch (_: Exception) {
            b.map.controller.setCenter(gp(data.last()))
            b.map.controller.setZoom(18.0)
        }
    }

    private fun shareCurrent() {
        latestPoint?.let { p ->
            val text = "${b.mapChild.text}: https://maps.google.com/?q=${p.latitude},${p.longitude}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Mövqeyi paylaş"))
        }
    }

    private fun openNavigation() {
        latestPoint?.let { p ->
            val uri = Uri.parse("google.navigation:q=${p.latitude},${p.longitude}&mode=d")
            val google = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
            if (google.resolveActivity(requireContext().packageManager) != null) {
                startActivity(google)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${p.latitude},${p.longitude}")))
            }
        }
    }

    private fun gp(x: LocationPoint) = GeoPoint(x.latitude.toDouble(), x.longitude.toDouble())

    private fun timeDiff(a: String, c: String): Double = try {
        Duration.between(LocalDateTime.parse(a, RAW), LocalDateTime.parse(c, RAW)).seconds.toDouble()
    } catch (_: Exception) { 0.0 }

    private fun parseRaw(raw: String): LocalDateTime? = try { LocalDateTime.parse(raw, RAW) } catch (_: Exception) { null }

    // location.recorded_at artıq lokal vaxtdır. Burada +1 saat ETMİRİK.
    private fun displayLocationDateTime(raw: String): String = try {
        LocalDateTime.parse(raw, RAW).format(DISPLAY)
    } catch (_: Exception) { raw }

    private fun formatMinutes(m: Int) = if (m >= 60) "${m / 60}s ${m % 60} dəq" else "$m dəq"

    private fun dist(a: GeoPoint, c: GeoPoint): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(c.latitude)
        val dp = Math.toRadians(c.latitude - a.latitude)
        val dl = Math.toRadians(c.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun avatar(letter: String): BitmapDrawable {
        val s = 104
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2478F3") }
        canvas.drawCircle(s / 2f, s / 2f - 5, s / 2f - 5, white)
        canvas.drawCircle(s / 2f, s / 2f - 5, s / 2f - 11, blue)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(letter, s / 2f, s / 2f - 5 - (text.ascent() + text.descent()) / 2, text)
        val pointer = Path().apply {
            moveTo(s / 2f - 10, s - 22f)
            lineTo(s / 2f + 10, s - 22f)
            lineTo(s / 2f, s - 4f)
            close()
        }
        canvas.drawPath(pointer, blue)
        return BitmapDrawable(resources, bmp)
    }

    private fun dot(hex: String, size: Int): BitmapDrawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val color = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor(hex) }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, white)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, color)
        return BitmapDrawable(resources, bmp)
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
    }

    override fun onPause() {
        b.map.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private val RAW = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val DISPLAY = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    }
}
