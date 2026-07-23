package com.websarva.wings.android.slevo.data.backup.pending

/**
 * durable な COMPLETED marker に続く success result と pending cleanup を統括する。
 *
 * result write と cleanup はそれぞれ独立した retry point とし、失敗を startup failure handler
 * へ渡さず、COMPLETED marker と再試行に必要な artifact を保持する。
 */
internal class PendingRestoreCompletionFinalizer(
    private val fileStore: PendingRestoreFileStore,
    private val nowProvider: () -> String,
    private val currentDbVersion: Int,
    private val logWarning: (String, Throwable?) -> Unit,
) {
    /** success result を durable にしてから marker-last cleanup を再試行可能な形で行う。 */
    fun complete(marker: PendingRestoreMarker, message: String) {
        // --- Durable success result ---
        try {
            fileStore.writeResult(
                success = true,
                message = message,
                timestamp = nowProvider(),
                backupDatabaseVersion = marker.databaseVersion,
                currentDatabaseVersion = currentDbVersion,
                migrationRequired = marker.databaseVersion < currentDbVersion,
                migrationCompleted = true,
            )
        } catch (error: Exception) {
            // result 未公開なら cleanup を開始せず、COMPLETED から次回 retry する。
            logWarning("pending restore success result write failed", error)
            return
        }

        // --- Marker-last cleanup ---
        try {
            if (!fileStore.cleanupPending()) {
                logWarning("pending restore cleanup did not complete", null)
            }
        } catch (error: Exception) {
            // success result と active marker を保持し、次回 recovery に委ねる。
            logWarning("pending restore cleanup failed", error)
        }
    }
}
