package com.mindfullinking.feature.settings

import com.mindfullinking.core.database.BudgetStateEntity
import com.mindfullinking.core.database.LinkEntity
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.RecommendationSessionEntity
import com.mindfullinking.core.model.DurationOption
import com.mindfullinking.core.model.CreatedFrom
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.MetadataFetchStatus
import com.mindfullinking.core.model.ResolutionReason
import com.mindfullinking.core.model.BudgetState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.time.LocalDate
import org.json.JSONArray
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** Complete local database snapshot, intentionally excluding no user data. */
data class BackupSnapshot(
    val exportedAtEpochMs: Long,
    val links: List<LinkEntity>,
    val budgetState: BudgetStateEntity?,
    val events: List<LinkEventEntity>,
    val sessions: List<RecommendationSessionEntity>
)

/** Rejects malformed backups before any destructive restore transaction begins. */
fun BackupSnapshot.requireValidForRestore() {
    require(links.map { it.id }.distinct().size == links.size) { "Backup contains duplicate link IDs." }
    require(events.map { it.id }.distinct().size == events.size) { "Backup contains duplicate event IDs." }
    require(sessions.map { it.id }.distinct().size == sessions.size) { "Backup contains duplicate session IDs." }
    links.forEach { link ->
        require(link.id.isNotBlank() && link.canonicalUrl.isNotBlank() && link.displayUrl.isNotBlank()) { "Backup has an incomplete link." }
        require(link.priority in 1..5) { "Backup has an invalid link priority." }
        require(link.estimatedDurationMinutes in DurationOption.VALID_MINUTES) { "Backup has an invalid duration." }
        require(link.status in LinkStatus.entries.map { it.name }) { "Backup has an invalid link status." }
        require(link.createdFrom in CreatedFrom.entries.map { it.name }) { "Backup has an invalid creation source." }
        require(link.metadataFetchStatus in MetadataFetchStatus.entries.map { it.name }) { "Backup has an invalid metadata status." }
        require(link.resolutionReason == null || link.resolutionReason in ResolutionReason.entries.map { it.name }) { "Backup has an invalid resolution reason." }
        require(link.skipCount >= 0 && link.recommendationCount >= 0) { "Backup has invalid link counters." }
        when {
            link.deadlineAtEpochMs == null -> require(!link.deadlineHasTime && !link.deadlineNotificationsEnabled && link.deadlineDateOnly == null) {
                "Backup has an incomplete deadline."
            }
            link.deadlineHasTime -> require(link.deadlineDateOnly == null) { "Timed deadlines cannot include a date-only value." }
            link.deadlineDateOnly != null -> LocalDate.parse(link.deadlineDateOnly)
        }
    }
    events.forEach { event ->
        require(event.id.isNotBlank() && event.linkId.isNotBlank()) { "Backup has an incomplete event." }
        require(event.eventType in LinkEventType.entries.map { it.name }) { "Backup has an invalid event type." }
    }
    sessions.forEach { session ->
        require(session.id.isNotBlank() && session.availableMinutes in DurationOption.VALID_MINUTES) { "Backup has an invalid recommendation session." }
        JSONArray(session.recommendedLinkIdsJson)
    }
    budgetState?.let { budget ->
        require(budget.id == 1 && budget.standardBudgetCents == BudgetState.STANDARD_BUDGET_CENTS) { "Backup has an invalid budget contract." }
        require(
            budget.temporaryOverageCents == 0 ||
                (budget.temporaryOverageCents in 100..5_000 && budget.temporaryOverageCents % 100 == 0)
        ) { "Backup has an invalid temporary overage." }
        if (budget.temporaryOverageCents == 0) {
            require(budget.temporaryOverageExpiresAtEpochMs == null && budget.lastOverageReminderAtEpochMs == null) { "Inactive overage must not have reminder state." }
        } else {
            val expiresAt = requireNotNull(budget.temporaryOverageExpiresAtEpochMs) { "Active overage needs an expiry." }
            val lastReminderAt = budget.lastOverageReminderAtEpochMs
            require(
                lastReminderAt == null || lastReminderAt <= expiresAt
            ) { "Backup has an incoherent overage reminder." }
        }
    }
}

/**
 * Password-based, versioned backup container. The passphrase is never stored;
 * PBKDF2 derives a per-file AES-GCM key from a random salt.
 */
object EncryptedBackupCodec {
    private const val MAGIC = 0x4D4C424B // MLBK
    private const val FORMAT_VERSION = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000
    private const val MAX_CONTAINER_BYTES = 128 * 1024 * 1024

    fun readContainer(input: InputStream): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        return ByteArrayOutputStream().use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_CONTAINER_BYTES) { "Backup is too large." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    fun encrypt(snapshot: BackupSnapshot, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "A backup passphrase is required." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val plaintext = serialize(snapshot)
        val ciphertext = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, iv).doFinal(plaintext)
        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.writeInt(FORMAT_VERSION)
                data.writeInt(salt.size)
                data.write(salt)
                data.writeInt(iv.size)
                data.write(iv)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
            }
            output.toByteArray()
        }
    }

    fun decrypt(container: ByteArray, passphrase: CharArray): BackupSnapshot {
        require(passphrase.isNotEmpty()) { "A backup passphrase is required." }
        val plaintext = DataInputStream(ByteArrayInputStream(container)).use { data ->
            require(data.readInt() == MAGIC) { "Not a Mindful Linking backup." }
            require(data.readInt() == FORMAT_VERSION) { "Unsupported backup version." }
            val salt = data.readBytes(maxLength = 64)
            val iv = data.readBytes(maxLength = 32)
            val ciphertext = data.readBytes(maxLength = 128 * 1024 * 1024)
            cipher(Cipher.DECRYPT_MODE, passphrase, salt, iv).doFinal(ciphertext)
        }
        return deserialize(plaintext)
    }

    private fun cipher(mode: Int, passphrase: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, javax.crypto.spec.SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
    }

    private fun serialize(snapshot: BackupSnapshot): ByteArray = ByteArrayOutputStream().use { output ->
        DataOutputStream(output).use { data ->
            data.writeLong(snapshot.exportedAtEpochMs)
            data.writeList(snapshot.links) { value -> writeLink(value) }
            data.writeBoolean(snapshot.budgetState != null)
            snapshot.budgetState?.let { data.writeBudget(it) }
            data.writeList(snapshot.events) { value -> writeEvent(value) }
            data.writeList(snapshot.sessions) { value -> writeSession(value) }
        }
        output.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): BackupSnapshot = DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        BackupSnapshot(
            exportedAtEpochMs = data.readLong(),
            links = data.readList { readLink() },
            budgetState = if (data.readBoolean()) data.readBudget() else null,
            events = data.readList { readEvent() },
            sessions = data.readList { readSession() }
        )
    }

    private fun DataOutputStream.writeLink(value: LinkEntity) = with(value) {
        writeString(id); writeString(canonicalUrl); writeString(displayUrl); writeNullableString(title); writeNullableString(note)
        writeInt(priority); writeInt(estimatedDurationMinutes); writeNullableLong(deadlineAtEpochMs); writeNullableString(deadlineDateOnly)
        writeBoolean(deadlineHasTime); writeBoolean(deadlineNotificationsEnabled); writeString(status); writeNullableString(resolutionReason)
        writeLong(savedAtEpochMs); writeNullableLong(resolvedAtEpochMs); writeNullableLong(lastOpenedAtEpochMs); writeNullableLong(snoozedUntilEpochMs)
        writeInt(skipCount); writeNullableLong(lastSkippedAtEpochMs); writeInt(recommendationCount); writeNullableLong(lastRecommendedAtEpochMs)
        writeString(createdFrom); writeString(metadataFetchStatus); writeLong(updatedAtEpochMs)
    }

    private fun DataInputStream.readLink() = LinkEntity(
        id = readString(), canonicalUrl = readString(), displayUrl = readString(), title = readNullableString(), note = readNullableString(),
        priority = readInt(), estimatedDurationMinutes = readInt(), deadlineAtEpochMs = readNullableLong(), deadlineDateOnly = readNullableString(),
        deadlineHasTime = readBoolean(), deadlineNotificationsEnabled = readBoolean(), status = readString(), resolutionReason = readNullableString(),
        savedAtEpochMs = readLong(), resolvedAtEpochMs = readNullableLong(), lastOpenedAtEpochMs = readNullableLong(), snoozedUntilEpochMs = readNullableLong(),
        skipCount = readInt(), lastSkippedAtEpochMs = readNullableLong(), recommendationCount = readInt(), lastRecommendedAtEpochMs = readNullableLong(),
        createdFrom = readString(), metadataFetchStatus = readString(), updatedAtEpochMs = readLong()
    )

    private fun DataOutputStream.writeBudget(value: BudgetStateEntity) = with(value) {
        writeInt(id); writeInt(standardBudgetCents); writeInt(temporaryOverageCents); writeNullableLong(temporaryOverageExpiresAtEpochMs); writeNullableLong(lastOverageReminderAtEpochMs)
    }
    private fun DataInputStream.readBudget() = BudgetStateEntity(readInt(), readInt(), readInt(), readNullableLong(), readNullableLong())

    private fun DataOutputStream.writeEvent(value: LinkEventEntity) = with(value) { writeString(id); writeString(linkId); writeString(eventType); writeLong(timestampEpochMs); writeNullableString(details) }
    private fun DataInputStream.readEvent() = LinkEventEntity(readString(), readString(), readString(), readLong(), readNullableString())
    private fun DataOutputStream.writeSession(value: RecommendationSessionEntity) = with(value) { writeString(id); writeInt(availableMinutes); writeLong(startedAtEpochMs); writeString(recommendedLinkIdsJson) }
    private fun DataInputStream.readSession() = RecommendationSessionEntity(readString(), readInt(), readLong(), readString())

    private fun <T> DataOutputStream.writeList(values: List<T>, writer: DataOutputStream.(T) -> Unit) { writeInt(values.size); values.forEach { writer(this, it) } }
    private fun <T> DataInputStream.readList(reader: DataInputStream.() -> T): List<T> { val count = readInt(); require(count in 0..1_000_000); return List(count) { reader(this) } }
    private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null); if (value != null) writeString(value) }
    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null
    private fun DataOutputStream.writeString(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.readString(): String = readBytes(maxLength = 16 * 1024 * 1024).toString(Charsets.UTF_8)
    private fun DataOutputStream.writeNullableLong(value: Long?) { writeBoolean(value != null); value?.let(::writeLong) }
    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null
    private fun DataInputStream.readBytes(maxLength: Int): ByteArray { val size = readInt(); require(size in 0..maxLength); return ByteArray(size).also(::readFully) }
}
