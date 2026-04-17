package com.smssequencer.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.smssequencer.R
import com.smssequencer.data.AppDatabase
import com.smssequencer.data.Contact
import com.smssequencer.data.Sequence
import com.smssequencer.worker.SmsWorker
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─── Fragment ────────────────────────────────────────────────────────────────

class ContactListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_contact_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<TextView>(R.id.tv_empty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab)
        val db = AppDatabase.get(requireContext())

        val adapter = ContactAdapter(emptyList()) { contact ->
            startActivity(Intent(requireContext(), ContactEditActivity::class.java).apply {
                putExtra("contact_id", contact.id)
            })
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        lifecycleScope.launch {
            db.contactDao().getAll().collect { contacts ->
                val seqs = db.sequenceDao().getAllSync()
                adapter.update(contacts, seqs)
                empty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(requireContext(), ContactEditActivity::class.java))
        }
    }
}

// ─── Adapter ─────────────────────────────────────────────────────────────────

class ContactAdapter(
    private var contacts: List<Contact>,
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    private var sequences = listOf<Sequence>()
    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    fun update(c: List<Contact>, s: List<Sequence>) { contacts = c; sequences = s; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_name)
        val phone: TextView = v.findViewById(R.id.tv_phone)
        val seq: TextView = v.findViewById(R.id.tv_seq)
        val date: TextView = v.findViewById(R.id.tv_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = contacts[position]
        val seq = sequences.find { it.id == c.sequenceId }
        holder.name.text = c.name
        holder.phone.text = c.phone
        holder.seq.text = seq?.name ?: "Séquence inconnue"
        holder.date.text = "Inscrit le ${sdf.format(Date(c.enrolledAt))}"
        holder.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = contacts.size
}

// ─── Edit Activity ────────────────────────────────────────────────────────────

class ContactEditActivity : AppCompatActivity() {

    private var contactId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        contactId = intent.getLongExtra("contact_id", 0)
        val etName = findViewById<EditText>(R.id.et_name)
        val etPhone = findViewById<EditText>(R.id.et_phone)
        val spinnerSeq = findViewById<Spinner>(R.id.spinner_seq)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnDelete = findViewById<Button>(R.id.btn_delete)

        val db = AppDatabase.get(this)
        var sequences = listOf<Sequence>()

        lifecycleScope.launch {
            sequences = db.sequenceDao().getAllSync()
            val names = sequences.map { it.name }
            spinnerSeq.adapter = ArrayAdapter(this@ContactEditActivity, android.R.layout.simple_spinner_dropdown_item, names)

            if (contactId > 0) {
                supportActionBar?.title = "Modifier le contact"
                btnDelete.visibility = View.VISIBLE
                val contact = db.contactDao().getActiveSync().find { it.id == contactId }
                contact?.let {
                    etName.setText(it.name)
                    etPhone.setText(it.phone)
                    val idx = sequences.indexOfFirst { s -> s.id == it.sequenceId }
                    if (idx >= 0) spinnerSeq.setSelection(idx)
                }
            } else {
                supportActionBar?.title = "Nouveau contact"
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val selectedSeq = sequences.getOrNull(spinnerSeq.selectedItemPosition)
                ?: run { Toast.makeText(this, "Créez d'abord une séquence", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            lifecycleScope.launch {
                if (contactId > 0) {
                    val existing = db.contactDao().getActiveSync().find { it.id == contactId }
                    existing?.let { db.contactDao().update(it.copy(name = name, phone = phone, sequenceId = selectedSeq.id)) }
                } else {
                    db.contactDao().insert(Contact(name = name, phone = phone, sequenceId = selectedSeq.id))
                }
                SmsWorker.scheduleOnce(this@ContactEditActivity)
                Toast.makeText(this@ContactEditActivity, "Contact enregistré", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnDelete.setOnClickListener {
            lifecycleScope.launch {
                val contact = db.contactDao().getActiveSync().find { it.id == contactId }
                contact?.let { db.contactDao().delete(it) }
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
