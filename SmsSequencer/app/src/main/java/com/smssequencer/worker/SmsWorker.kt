package com.smssequencer.worker

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.*
import com.smssequencer.data.AppDatabase
import com.smssequencer.data.SendLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SmsWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "SmsWorker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsWorker>()
                .setInitialDelay(0, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(applicationContext)
        val logDao = db.sendLogDao()
        val stepDao = db.smsStepDao()
        val contactDao = db.contactDao()
        val now = System.currentTimeMillis()

        // Build send log entries for all active contacts if not already done
        val contacts = contactDao.getActiveSync()
        for (contact in contacts) {
            val steps = stepDao.getBySequenceSync(contact.sequenceId)
            for (step in steps) {
                val existing = logDao.find(contact.id, step.id)
                if (existing == null) {
                    val delayMs = (step.delayDays * 24L + step.delayHours) * 3600_000L
                    val scheduledAt = contact.enrolledAt + delayMs
                    logDao.insert(SendLog(
                        contactId = contact.id,
                        stepId = step.id,
                        scheduledAt = scheduledAt,
                        status = "PENDING"
                    ))
                }
            }
        }

        // Send all due pending logs
        val dueLogs = logDao.getDuePending(now)
        for (log in dueLogs) {
            val contact = contactDao.getActiveSync().find { it.id == log.contactId } ?: continue
            val steps = stepDao.getBySequenceSync(contact.sequenceId)
            val step = steps.find { it.id == log.stepId } ?: continue

            try {
                val smsManager = applicationContext.getSystemService(SmsManager::class.java)
                val parts = smsManager.divideMessage(step.message)
                if (parts.size == 1) {
                    smsManager.sendTextMessage(contact.phone, null, step.message, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null)
                }
                logDao.update(log.copy(sentAt = System.currentTimeMillis(), status = "SENT"))
                Log.d(TAG, "SMS sent to ${contact.phone} — step ${step.position + 1}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.phone}: ${e.message}")
                logDao.update(log.copy(status = "FAILED"))
            }
        }

        Result.success()
    }
}
