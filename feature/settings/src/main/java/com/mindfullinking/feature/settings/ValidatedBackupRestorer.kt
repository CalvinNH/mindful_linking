package com.mindfullinking.feature.settings

/**
 * Establishes the non-destructive boundary for restore: decryption and
 * validation complete before the caller is allowed to replace persisted data.
 */
internal class ValidatedBackupRestorer(
    private val replaceValidatedSnapshot: suspend (BackupSnapshot) -> Unit
) {
    suspend fun restore(container: ByteArray, passphrase: CharArray) {
        val snapshot = EncryptedBackupCodec.decrypt(container, passphrase)
        snapshot.requireValidForRestore()
        replaceValidatedSnapshot(snapshot)
    }
}
