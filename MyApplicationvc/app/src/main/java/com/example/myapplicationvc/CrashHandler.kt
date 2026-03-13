package com.example.myapplicationvc

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Get crash data
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        val stackTrace = stringWriter.toString()
        val crashData = ""$timestamp","${throwable.javaClass.simpleName}","${throwable.message}","$stackTrace"\n"

        // Write to CSV file
        try {
            val file = File(context.filesDir, "crashes.csv")
            val fileWriter = FileWriter(file, true) // Append to file
            if (!file.exists()) {
                 fileWriter.write(""Timestamp","Exception","Message","Stack Trace"\n")
            }
            fileWriter.write(crashData)
            fileWriter.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Call the default handler to terminate the app
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
