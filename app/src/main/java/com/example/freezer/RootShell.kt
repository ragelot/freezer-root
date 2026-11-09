package com.example.freezer

import java.io.BufferedReader
import java.io.InputStreamReader

/** Exécution de commandes avec les droits root (via `su`). */
object RootShell {

    data class Result(val code: Int, val out: String, val err: String) {
        val ok get() = code == 0
    }

    fun exec(command: String): Result = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
        val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
        Result(p.waitFor(), out.trim(), err.trim())
    } catch (e: Exception) {
        Result(-1, "", e.message ?: "su introuvable")
    }

    fun isRootAvailable(): Boolean = exec("id").let { it.ok && it.out.contains("uid=0") }
}
