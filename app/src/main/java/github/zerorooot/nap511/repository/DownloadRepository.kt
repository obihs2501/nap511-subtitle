package github.zerorooot.nap511.repository

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.LocalDownloadItem
import github.zerorooot.nap511.bean.LocalDownloadRecord
import github.zerorooot.nap511.bean.LocalDownloadStatus
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DownloadUtil
import github.zerorooot.nap511.util.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Android built-in transfer service: survives navigation/process exit, no external RPC server. */
class DownloadRepository private constructor() {
    companion object {
        val instance: DownloadRepository by lazy { DownloadRepository() }
    }

    private val context get() = App.instance.applicationContext
    private val manager get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences get() = context.getSharedPreferences("local_downloads", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val gson = Gson()

    private fun records(): List<LocalDownloadRecord> =
        gson.fromJson(preferences.getString("records", "[]"), Array<LocalDownloadRecord>::class.java)?.toList().orEmpty()

    private fun persist(records: List<LocalDownloadRecord>) {
        check(preferences.edit().putString("records", gson.toJson(records)).commit()) { "无法保存下载任务" }
    }

    private fun visibleRecords(): List<LocalDownloadRecord> =
        records().filter { it.ownerUid == UserSessionManager.uid }

    suspend fun list(): List<LocalDownloadItem> = withContext(Dispatchers.IO) {
        mutex.withLock { snapshot(visibleRecords()).sortedByDescending { it.record.createdAt } }
    }

    /** Returns an active duplicate rather than silently downloading a file twice. */
    suspend fun enqueue(file: FileBean): Long = withContext(Dispatchers.IO) {
        mutex.withLock { enqueueLocked(file) }
    }

    private fun enqueueLocked(file: FileBean): Long {
        require(file.fileId.isNotBlank() && file.pickCode.isNotBlank() && !file.isFolder) { "暂不支持下载文件夹" }
        val owner = UserSessionManager.uid
        check(owner.isNotBlank() && UserSessionManager.cookie.isNotBlank()) { "请先登录网盘" }
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("请授予存储权限后重试")
        }
        val all = records()
        snapshot(all.filter { it.ownerUid == owner && it.fileId == file.fileId })
            .firstOrNull { it.active }?.let { return it.record.id }
        val url = FileRepository.getInstance().getDownloadUrl(file.pickCode, file.fileId)
            ?: throw IOException("无法获取下载地址，请检查登录或网盘下载权限")
        check(owner == UserSessionManager.uid) { "账号已切换，请重新下载" }
        val uri = Uri.parse(url)
        require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) { "下载地址无效" }
        val targetName = DownloadUtil.destinationName(file.name, UUID.randomUUID().toString().replace("-", ""))
        val request = DownloadManager.Request(uri)
            .setTitle(file.name)
            .setDescription("nap511 · 保存到 Download/nap511")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "nap511/$targetName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            // The CDN URL is already signed. Do not send the account cookie to download hosts.
            .addRequestHeader("User-Agent", ConfigKeyUtil.USER_AGENT)
        val id = try { manager.enqueue(request) } catch (error: IllegalArgumentException) {
            throw IOException("系统下载服务不可用，请检查系统下载管理器是否被停用", error)
        }
        val record = LocalDownloadRecord(id, file.name, file.fileId, file.pickCode, owner, targetName, System.currentTimeMillis())
        try {
            persist(all + record)
        } catch (error: Exception) {
            manager.remove(id) // Roll back a task that could not be recorded; no orphan transfer.
            throw error
        }
        return id
    }

    private fun snapshot(records: List<LocalDownloadRecord>): List<LocalDownloadItem> {
        if (records.isEmpty()) return emptyList()
        val result = mutableMapOf<Long, LocalDownloadItem>()
        for (batch in records.chunked(200)) {
            val byId = batch.associateBy { it.id }
            manager.query(DownloadManager.Query().setFilterById(*byId.keys.toLongArray()))?.use { cursor ->
                while (cursor.moveToNext()) {
                    fun long(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                    val record = byId[long(DownloadManager.COLUMN_ID)] ?: continue
                    val status = when (long(DownloadManager.COLUMN_STATUS).toInt()) {
                        DownloadManager.STATUS_PENDING -> LocalDownloadStatus.QUEUED
                        DownloadManager.STATUS_RUNNING -> LocalDownloadStatus.RUNNING
                        DownloadManager.STATUS_PAUSED -> LocalDownloadStatus.WAITING
                        DownloadManager.STATUS_SUCCESSFUL -> LocalDownloadStatus.COMPLETE
                        else -> LocalDownloadStatus.FAILED
                    }
                    val reason = reasonText(status, long(DownloadManager.COLUMN_REASON).toInt())
                    result[record.id] = LocalDownloadItem(record, status,
                        long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), reason)
                }
            }
        }
        return records.map { result[it.id] ?: LocalDownloadItem(it, LocalDownloadStatus.MISSING,
            reason = "系统下载记录已移除，可重新下载") }
    }

    private fun reasonText(status: LocalDownloadStatus, reason: Int): String = when (status) {
        LocalDownloadStatus.QUEUED -> "等待下载"
        LocalDownloadStatus.RUNNING -> "下载中"
        LocalDownloadStatus.COMPLETE -> "下载完成"
        LocalDownloadStatus.WAITING -> when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络连接"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi（系统下载限制）"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "网络异常，系统正在自动重试"
            else -> "系统暂停，等待恢复"
        }
        else -> when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "下载存储不可用"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "同名目标已存在，请重试"
            401, 403, 404, 410 -> "下载地址失效或无权限，重试将获取新地址"
            else -> "下载失败（系统代码 $reason），可重试"
        }
    }

    suspend fun retry(id: Long): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = visibleRecords().firstOrNull { it.id == id } ?: error("下载记录不存在")
            val old = snapshot(listOf(record)).single()
            require(!old.active && old.status != LocalDownloadStatus.COMPLETE) { "此任务无需重试" }
            // Create a fresh signed URL and a new unique target, preserving the failed record if retry fails.
            val newId = enqueueLocked(FileBean(fileId = record.fileId, pickCode = record.pickCode, name = record.fileName))
            manager.remove(id)
            persist(records().filterNot { it.id == id })
            newId
        }
    }

    /** Cancel active tasks (including partial data). Completed files are retained when hiding a record. */
    suspend fun removeRecord(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = visibleRecords().firstOrNull { it.id == id } ?: return@withLock
            val item = snapshot(listOf(record)).single()
            if (item.status != LocalDownloadStatus.COMPLETE) manager.remove(id)
            persist(records().filterNot { it.id == id })
        }
    }

    suspend fun openIntent(id: Long): Intent = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = visibleRecords().firstOrNull { it.id == id } ?: error("下载记录不存在")
            check(snapshot(listOf(record)).single().status == LocalDownloadStatus.COMPLETE) { "文件尚未下载完成" }
            val uri = manager.getUriForDownloadedFile(id) ?: error("文件已移动或删除")
            manager.openDownloadedFile(id).use { } // Detect a deleted file before launching another app.
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, manager.getMimeTypeForDownloadedFile(id) ?: "application/octet-stream")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
