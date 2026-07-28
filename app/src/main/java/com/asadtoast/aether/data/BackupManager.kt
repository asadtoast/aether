package com.asadtoast.aether.data

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {
    fun createBackupZip(): RequestBody? {
        val file = File(context.filesDir, "hydrus_wiki.json")
        if (!file.exists()) return null

        val zipFile = File(context.filesDir, "hydrus_wiki.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry(file.name))
            FileInputStream(file).use { fis -> fis.copyTo(zos) }
            zos.closeEntry()
        }

        val mediaType = "application/zip".toMediaTypeOrNull()
        return zipFile.asRequestBody(mediaType)
    }
}