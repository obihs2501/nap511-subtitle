package github.zerorooot.nap511.bean

/** Persist only task metadata, never cookies or expiring download URLs. */
data class LocalDownloadRecord(
    val id: Long = 0,
    val fileName: String = "",
    val fileId: String = "",
    val pickCode: String = "",
    val ownerUid: String = "",
    val targetName: String = "",
    val createdAt: Long = 0
)

enum class LocalDownloadStatus { QUEUED, RUNNING, WAITING, COMPLETE, FAILED, MISSING }

data class LocalDownloadItem(
    val record: LocalDownloadRecord,
    val status: LocalDownloadStatus,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val reason: String = ""
) {
    val active: Boolean get() = status in setOf(
        LocalDownloadStatus.QUEUED, LocalDownloadStatus.RUNNING, LocalDownloadStatus.WAITING
    )
}
