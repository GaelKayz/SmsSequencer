package com.smssequencer.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smssequencer.R
import com.smssequencer.data.AppDatabase
import com.smssequencer.data.Sequence
import com.smssequencer.data.SmsStep
import kotlinx.coroutines.launch

class SequenceEditActivity : AppCompatActivity() {

    private val steps = mutableListOf<StepData>()
    private var sequenceId: Long = 0

    data class StepData(
        var delayDays: Int = 0,
        var delayHours: Int = 0,
        var message: String = "",
        var dbId: Long = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sequence_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sequenceId = intent.getLongExtra("seq_id", 0)
        val etName = findViewById<EditText>(R.id.et_seq_name)
        val container = findViewById<LinearLayout>(R.id.steps_container)
        val btnAddStep = findViewById<Button>(R.id.btn_add_step)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnDelete = findViewById<Button>(R.id.btn_delete)

        if (sequenceId > 0) {
            supportActionBar?.title = "Modifier la séquence"
            btnDelete.visibility = android.view.View.VISIBLE
            lifecycleScope.launch {
                val db = AppDatabase.get(this@SequenceEditActivity)
                val seq = db.sequenceDao().getById(sequenceId)
                etName.setText(seq?.name ?: "")
                val dbSteps = db.smsStepDao().getBySequenceSync(sequenceId)
                dbSteps.forEach { s ->
                    steps.add(StepData(s.delayDays, s.delayHours, s.message, s.id))
                    addStepView(container, steps.last())
                }
            }
        } else {
            supportActionBar?.title = "Nouvelle séquence"
            addNewStep(container)
        }

        btnAddStep.setOnClickListener { addNewStep(container) }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "Entrez un nom", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            syncStepsFromViews(container)
            val validSteps = steps.filter { it.message.isNotBlank() }
            if (validSteps.isEmpty()) { Toast.makeText(this, "Ajoutez au moins un SMS", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            lifecycleScope.launch {
                val db = AppDatabase.get(this@SequenceEditActivity)
                val id = if (sequenceId > 0) {
                    db.sequenceDao().update(Sequence(sequenceId, name))
                    db.smsStepDao().deleteAllForSequence(sequenceId)
                    sequenceId
                } else {
                    db.sequenceDao().insert(Sequence(name = name))
                }
                validSteps.forEachIndexed { idx, s ->
                    db.smsStepDao().insert(SmsStep(sequenceId = id, position = idx, delayDays = s.delayDays, delayHours = s.delayHours, message = s.message))
                }
                Toast.makeText(this@SequenceEditActivity, "Séquence enregistrée", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnDelete.setOnClickListener {
            lifecycleScope.launch {
                val db = AppDatabase.get(this@SequenceEditActivity)
                db.sequenceDao().getById(sequenceId)?.let { db.sequenceDao().delete(it) }
                finish()
            }
        }
    }

    private fun addNewStep(container: LinearLayout) {
        steps.add(StepData())
        addStepView(container, steps.last())
    }

    private fun addStepView(container: LinearLayout, data: StepData) {
        val view = layoutInflater.inflate(R.layout.item_step_edit, container, false)
        val etDays = view.findViewById<EditText>(R.id.et_days)
        val etHours = view.findViewById<EditText>(R.id.et_hours)
        val etMsg = view.findViewById<EditText>(R.id.et_message)
        val btnRemove = view.findViewById<ImageButton>(R.id.btn_remove)
        val idx = steps.size - 1
        view.tag = idx

        etDays.setText(if (data.delayDays > 0) data.delayDays.toString() else "")
        etHours.setText(if (data.delayHours > 0) data.delayHours.toString() else "")
        etMsg.setText(data.message)

        val label = view.findViewById<TextView>(R.id.tv_step_label)
        label.text = "SMS n°${container.childCount + 1}"

        btnRemove.setOnClickListener {
            syncStepsFromViews(container)
            container.removeView(view)
            if (idx < steps.size) steps.removeAt(idx)
        }
        container.addView(view)
    }

    private fun syncStepsFromViews(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            val idx = v.tag as? Int ?: continue
            if (idx >= steps.size) continue
            steps[idx].delayDays = v.findViewById<EditText>(R.id.et_days).text.toString().toIntOrNull() ?: 0
            steps[idx].delayHours = v.findViewById<EditText>(R.id.et_hours).text.toString().toIntOrNull() ?: 0
            steps[idx].message = v.findViewById<EditText>(R.id.et_message).text.toString()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
