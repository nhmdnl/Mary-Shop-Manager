package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseBackupManager {

    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    /**
     * Flushes Room database WAL and exports the SQLite database file to the user-chosen SAF Uri.
     */
    suspend fun backupDatabase(context: Context, destinationUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 1. Force WAL checkpoint to ensure all data is written to the primary DB file
            AppDatabase.checkpointDatabase()

            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Database file does not exist yet."))
            }

            val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: return@withContext Result.failure(IllegalStateException("Could not open destination file for writing."))

            var bytesCopied = 0L
            outputStream.use { out ->
                FileInputStream(dbFile).use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesCopied += read
                    }
                    out.flush()
                }
            }

            Result.success(bytesCopied)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validates and restores the Room SQLite database from the user-chosen SAF Uri.
     */
    suspend fun restoreDatabase(context: Context, sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Verify SQLite Header Magic Bytes
            val headerBytes = ByteArray(16)
            val headerValid = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val read = input.read(headerBytes)
                read == 16 && String(headerBytes) == SQLITE_HEADER
            } ?: false

            if (!headerValid) {
                return@withContext Result.failure(
                    IllegalArgumentException("The selected file is not a valid SQLite database backup.")
                )
            }

            // 2. Close active Room Database connections cleanly
            AppDatabase.closeDatabase()

            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val parentDir = dbFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            val tempFile = File(parentDir, "${AppDatabase.DATABASE_NAME}_restore.tmp")

            // 3. Copy source into a temporary file
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to open selected backup file."))

            inputStream.use { input ->
                FileOutputStream(tempFile).use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
            }

            // 4. Overwrite main database file and remove stale WAL / SHM files
            val walFile = File(parentDir, "${AppDatabase.DATABASE_NAME}-wal")
            val shmFile = File(parentDir, "${AppDatabase.DATABASE_NAME}-shm")

            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (dbFile.exists()) dbFile.delete()

            val renamed = tempFile.renameTo(dbFile)
            if (!renamed) {
                // Fallback copy if rename failed across filesystems
                tempFile.copyTo(dbFile, overwrite = true)
                tempFile.delete()
            }

            // 5. Reinitialize and verify database
            val restoredDb = AppDatabase.getDatabase(context)
            // Query party count as sanity check
            restoredDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM parties").use { cursor ->
                cursor.moveToFirst()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
