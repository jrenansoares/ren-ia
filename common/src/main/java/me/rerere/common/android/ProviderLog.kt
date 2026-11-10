package me.rerere.common.android

/** Provider payloads, error bodies and throwable messages must never reach logcat. */
@Suppress("UNUSED_PARAMETER")
class ContentFreeLogger(private val emit: (Int, String, String) -> Int) {
    fun v(tag: String?, message: String?, error: Throwable? = null): Int = 0
    fun d(tag: String?, message: String?, error: Throwable? = null): Int = 0
    fun i(tag: String?, message: String?, error: Throwable? = null): Int = 0
    fun w(tag: String?, message: String?, error: Throwable? = null): Int =
        emit(5, "ProjetoIA.Provider", "Provider warning; private details omitted")
    fun e(tag: String?, message: String?, error: Throwable? = null): Int =
        emit(6, "ProjetoIA.Provider", "Provider error; private details omitted")
}

val ProviderLog = ContentFreeLogger { priority, tag, event -> android.util.Log.println(priority, tag, event) }
