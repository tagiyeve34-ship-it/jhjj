package com.ailenezareti.nezaretv4.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ailenezareti.nezaretv4.Prefs
import com.ailenezareti.nezaretv4.R
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.FragmentCallsBinding
import com.ailenezareti.nezaretv4.databinding.ItemCallBinding
import com.ailenezareti.nezaretv4.model.CallEntry
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CallsFragment : Fragment() {
    private var _b: FragmentCallsBinding? = null
    private val b get() = _b!!
    private var date = LocalDate.now()
    private var type = "all"
    private val adapter = CallAdapter(
        onCopy = { copyNumber(it) },
        onOpen = { showHistory(it) }
    )

    data class CallGroup(val phone: String, val name: String?, val calls: List<CallEntry>) {
        val totalSeconds: Int get() = calls.sumOf { it.duration_sec.coerceAtLeast(0) }
        val latest: CallEntry get() = calls.maxByOrNull { it.occurred_at } ?: calls.first()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCallsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.tabs.check(R.id.all)
        b.tabs.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                type = when (id) {
                    R.id.incoming -> "incoming"
                    R.id.outgoing -> "outgoing"
                    R.id.missed -> "missed"
                    else -> "all"
                }
                load()
            }
        }
        b.dateBtn.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                date = LocalDate.of(y, m + 1, d)
                load()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        load()
    }

    private fun load() {
        val id = Prefs.child(requireContext())
        if (id < 0) return
        b.dateLabel.text = if (date == LocalDate.now()) "Bu gün" else date.toString()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val day = date.toString()
                val calls = ApiClient.service(requireContext()).calls(id, day, day, type, null, 1000, 0).body()?.calls.orEmpty()
                val groups = calls.groupBy { normalize(it.phone_number) }
                    .map { (_, list) ->
                        val latest = list.maxByOrNull { it.occurred_at } ?: list.first()
                        CallGroup(latest.phone_number, latest.contact_name, list.sortedByDescending { it.occurred_at })
                    }
                    .sortedByDescending { it.latest.occurred_at }
                adapter.items = groups
                b.countLabel.text = "${calls.size} zəng • ${groups.size} nömrə"
                adapter.notifyDataSetChanged()
            } catch (_: Exception) {
                adapter.items = emptyList()
                adapter.notifyDataSetChanged()
                b.countLabel.text = "0 zəng"
                Toast.makeText(requireContext(), "Zənglər yüklənmədi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun normalize(s: String) = s.filter { it.isDigit() || it == '+' }

    private fun copyNumber(number: String) {
        val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Telefon nömrəsi", number))
        Toast.makeText(requireContext(), "$number kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun showHistory(group: CallGroup) {
        val text = buildString {
            append(group.name?.takeIf { it.isNotBlank() } ?: group.phone)
            append("\n").append(group.phone)
            append("\n\nCəmi: ${group.calls.size} zəng • ${formatDuration(group.totalSeconds)}\n\n")
            group.calls.forEach { x ->
                val t = x.call_type.lowercase()
                val kind = when {
                    t.contains("out") || t == "2" -> "Gedən"
                    t.contains("miss") || t == "3" -> "Qaçırılan"
                    else -> "Gələn"
                }
                append("$kind  •  ${displayDateTime(x.occurred_at)}  •  ${formatDuration(x.duration_sec)}\n")
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Zəng tarixçəsi")
            .setMessage(text)
            .setPositiveButton("Bağla", null)
            .setNeutralButton("Nömrəni kopyala") { _, _ -> copyNumber(group.phone) }
            .show()
    }

    private fun formatDuration(sec: Int): String {
        if (sec <= 0) return "0 san"
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "$m dəq ${s} san" else "$s san"
    }

    private fun displayDateTime(raw: String): String = try {
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        LocalDateTime.parse(raw, f).plusHours(1).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
    } catch (_: Exception) { raw }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private class CallAdapter(
        val onCopy: (String) -> Unit,
        val onOpen: (CallGroup) -> Unit
    ) : RecyclerView.Adapter<CallAdapter.H>() {
        var items: List<CallGroup> = emptyList()
        class H(val b: ItemCallBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(p: ViewGroup, v: Int) = H(ItemCallBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: H, p: Int) {
            val g = items[p]
            val x = g.latest
            val t = x.call_type.lowercase()
            h.b.name.text = g.name?.takeIf { it.isNotBlank() } ?: g.phone
            h.b.number.text = if (g.calls.size > 1) "${g.phone}  •  ${g.calls.size} dəfə" else g.phone
            h.b.time.text = displayTime(x.occurred_at)
            h.b.duration.text = if (g.calls.size > 1) "Cəmi ${format(g.totalSeconds)}  ›" else "${format(g.totalSeconds)}  ›"
            h.b.icon.text = when {
                t.contains("out") || t == "2" -> "↗"
                t.contains("miss") || t == "3" -> "×"
                else -> "↙"
            }
            h.b.number.setOnClickListener { onCopy(g.phone) }
            h.b.root.setOnClickListener { onOpen(g) }
            h.b.root.setOnLongClickListener { onCopy(g.phone); true }
        }
        private fun format(sec: Int): String {
            val m = sec / 60; val s = sec % 60
            return if (m > 0) "$m dəq ${s}s" else "$s san"
        }
        private fun displayTime(raw: String): String = try {
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            LocalDateTime.parse(raw, f).plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) { raw.takeLast(8).take(5) }
    }
}
