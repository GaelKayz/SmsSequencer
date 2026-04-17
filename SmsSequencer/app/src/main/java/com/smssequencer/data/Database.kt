package com.smssequencer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ───────────────────────────────────────────────────────────────

@Entity(tableName = "sequences")
data class Sequence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_steps", foreignKeys = [
    ForeignKey(entity = Sequence::class, parentColumns = ["id"], childColumns = ["sequenceId"], onDelete = ForeignKey.CASCADE)
])
data class SmsStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequenceId: Long,
    val position: Int,
    val delayDays: Int,       // delay in days after contact enroll date
    val delayHours: Int = 0,
    val message: String
)

@Entity(tableName = "contacts", foreignKeys = [
    ForeignKey(entity = Sequence::class, parentColumns = ["id"], childColumns = ["sequenceId"], onDelete = ForeignKey.CASCADE)
])
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val sequenceId: Long,
    val enrolledAt: Long = System.currentTimeMillis(), // timestamp when enrolled
    val active: Boolean = true
)

@Entity(tableName = "send_log")
data class SendLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val stepId: Long,
    val scheduledAt: Long,
    val sentAt: Long? = null,
    val status: String = "PENDING" // PENDING, SENT, FAILED
)

// ─── DAOs ────────────────────────────────────────────────────────────────────

@Dao
interface SequenceDao {
    @Query("SELECT * FROM sequences ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Sequence>>

    @Query("SELECT * FROM sequences")
    suspend fun getAllSync(): List<Sequence>

    @Query("SELECT * FROM sequences WHERE id = :id")
    suspend fun getById(id: Long): Sequence?

    @Insert suspend fun insert(seq: Sequence): Long
    @Update suspend fun update(seq: Sequence)
    @Delete suspend fun delete(seq: Sequence)
}

@Dao
interface SmsStepDao {
    @Query("SELECT * FROM sms_steps WHERE sequenceId = :seqId ORDER BY position ASC")
    fun getBySequence(seqId: Long): Flow<List<SmsStep>>

    @Query("SELECT * FROM sms_steps WHERE sequenceId = :seqId ORDER BY position ASC")
    suspend fun getBySequenceSync(seqId: Long): List<SmsStep>

    @Insert suspend fun insert(step: SmsStep): Long
    @Insert suspend fun insertAll(steps: List<SmsStep>)
    @Update suspend fun update(step: SmsStep)
    @Delete suspend fun delete(step: SmsStep)

    @Query("DELETE FROM sms_steps WHERE sequenceId = :seqId")
    suspend fun deleteAllForSequence(seqId: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY enrolledAt DESC")
    fun getAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE active = 1")
    suspend fun getActiveSync(): List<Contact>

    @Query("SELECT * FROM contacts WHERE sequenceId = :seqId")
    suspend fun getBySequence(seqId: Long): List<Contact>

    @Insert suspend fun insert(contact: Contact): Long
    @Update suspend fun update(contact: Contact)
    @Delete suspend fun delete(contact: Contact)
}

@Dao
interface SendLogDao {
    @Query("SELECT * FROM send_log WHERE contactId = :contactId ORDER BY scheduledAt ASC")
    fun getByContact(contactId: Long): Flow<List<SendLog>>

    @Query("SELECT * FROM send_log WHERE status = 'PENDING' AND scheduledAt <= :now")
    suspend fun getDuePending(now: Long): List<SendLog>

    @Query("SELECT * FROM send_log WHERE contactId = :contactId AND stepId = :stepId LIMIT 1")
    suspend fun find(contactId: Long, stepId: Long): SendLog?

    @Insert suspend fun insert(log: SendLog): Long
    @Update suspend fun update(log: SendLog)

    @Query("SELECT * FROM send_log ORDER BY scheduledAt ASC")
    fun getAll(): Flow<List<SendLog>>
}
