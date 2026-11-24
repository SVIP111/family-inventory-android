package com.family.inventory

import android.app.Application
import android.content.Context
import java.io.File

/**
 * 极简崩溃记录：拦截未捕获异常，把堆栈写到应用私有目录；
 * 下次启动时可以读出来展示，避免真机闪退时拿不到 logcat。
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val text = buildString {
                    append("时间：").append(System.currentTimeMillis()).append('\n')
                    append("线程：").append(thread.name).append('\n')
                    append(throwable.stackTraceToString())
                }
                File(appContext.filesDir, FILE_NAME).writeText(text)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 读取并清除上一次崩溃记录；没有则返回 null。 */
    fun consume(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@runCatching null
        val text = file.readText()
        file.delete()
        text.ifBlank { null }
    }.getOrNull()
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
