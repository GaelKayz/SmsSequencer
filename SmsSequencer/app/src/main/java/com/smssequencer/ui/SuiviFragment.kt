package com.smssequencer.ui

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smssequencer.R
import com.smssequencer.data.AppDatabase
import com.smssequencer.data.SendLog
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SuiviFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_suivi, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<TextView>(R.id.tv_empty)
        val db = AppDatabase.get(requireContext())
        val adapter = SuiviAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        lifecycleScope.launch {
            db.sendLogDao().getAll().collect { logs ->
                val contacts = db.contactDao().getActiveSync()
                val steps = contacts.flatMap { db.smsStepDao().getBySequenceSync(it.sequenceId) }
                val items = logs.map { log ->
                    val contact = contacts.find { it.id == log.contactId }
                    val step = steps.find { it.id == log.stepId }
                    SuiviItem(
                        contactName = contact?.name ?: "Inconnu",
                        phone = contact?.phone ?: "",
                        message = step?.message?.take(50) ?: "",
                        scheduledAt = log.scheduledAt,
                        status = log.status
                    )
                }.sortedByDescending { it.scheduledAt }
                adapter.submitList(items)
                empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

data class SuiviItem(
    val contactName: String,
    val phone: String,
    val message: String,
    val scheduledAt: Long,
    val status: String
)

class SuiviAdapter : RecyclerView.Adapter<SuiviAdapter.VH>() {
    private var items = listOf<SuiviItem>()
    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    fun submitList(list: List<SuiviItem>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val contact: TextView = v.findViewById(R.id.tv_contact)
        val msg: TextView = v.findViewById(R.id.tv_message)
        val date: TextView = v.findViewById(R.id.tv_date)
        val status: TextView = v.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_suivi, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.contact.text = "${item.contactName} · ${item.phone}"
        holder.msg.text = item.message
        holder.date.text = sdf.format(Date(item.scheduledAt))
        holder.status.text = when (item.status) {
            "SENT" -> "✓ Envoyé"
            "FAILED" -> "✗ Échec"
            else -> "⏳ Planifié"
        }
        val color = when (item.status) {
            "SENT" -> 0xFF2E7D32.toInt()
            "FAILED" -> 0xFFC62828.toInt()
            else -> 0xFFE65100.toInt()
        }
        holder.status.setTextColor(color)
    }

    override fun getItemCount() = items.size
}
