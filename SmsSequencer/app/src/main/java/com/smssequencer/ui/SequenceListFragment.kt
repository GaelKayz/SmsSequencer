package com.smssequencer.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.smssequencer.R
import com.smssequencer.data.AppDatabase
import com.smssequencer.data.Sequence
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SequenceListFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_sequence_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<TextView>(R.id.tv_empty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab)

        val adapter = SequenceAdapter { seq ->
            startActivity(Intent(requireContext(), SequenceEditActivity::class.java).apply {
                putExtra("seq_id", seq.id)
            })
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.get(requireContext()).sequenceDao().getAll().collect { list ->
                adapter.submitList(list)
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(requireContext(), SequenceEditActivity::class.java))
        }
    }
}

class SequenceAdapter(private val onClick: (Sequence) -> Unit) :
    RecyclerView.Adapter<SequenceAdapter.VH>() {

    private var items = listOf<Sequence>()

    fun submitList(list: List<Sequence>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_name)
        val sub: TextView = v.findViewById(R.id.tv_sub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sequence, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val seq = items[position]
        holder.name.text = seq.name
        holder.sub.text = "Appuyer pour modifier"
        holder.itemView.setOnClickListener { onClick(seq) }
    }

    override fun getItemCount() = items.size
}
