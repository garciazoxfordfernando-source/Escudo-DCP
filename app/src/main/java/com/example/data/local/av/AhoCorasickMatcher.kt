package com.example.data.local.av

import java.io.InputStream
import java.util.ArrayDeque

/**
 * Autómata Aho-Corasick basado en arreglos primitivos de bytes (0..255).
 * Permite detectar decenas de firmas de malware, heurísticas YARA y SDKs de ads
 * en una única pasada lineal O(n) sobre classes*.dex sin asignaciones en el bucle.
 */
class AhoCorasickMatcher<T>(patterns: List<Pair<ByteArray, T>>) {

    private val transitions = ArrayList<IntArray>(256)
    private val fail = ArrayList<Int>(256)
    private val outputs = ArrayList<MutableList<T>?>(256)

    init {
        createNode()
        for ((bytes, payload) in patterns) {
            if (bytes.isNotEmpty()) {
                insertPattern(bytes, payload)
            }
        }
        buildFailureLinks()
    }

    private fun createNode(): Int {
        transitions.add(IntArray(256) { -1 })
        fail.add(0)
        outputs.add(null)
        return transitions.size - 1
    }

    private fun insertPattern(bytes: ByteArray, payload: T) {
        var state = 0
        for (b in bytes) {
            val idx = b.toInt() and 0xFF
            var next = transitions[state][idx]
            if (next == -1) {
                next = createNode()
                transitions[state][idx] = next
            }
            state = next
        }
        val list = outputs[state] ?: mutableListOf<T>().also { outputs[state] = it }
        list.add(payload)
    }

    private fun buildFailureLinks() {
        val queue = ArrayDeque<Int>()
        val rootTrans = transitions[0]
        for (c in 0 until 256) {
            val next = rootTrans[c]
            if (next != -1) {
                fail[next] = 0
                queue.add(next)
            } else {
                rootTrans[c] = 0
            }
        }

        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            val trans = transitions[state]
            for (c in 0 until 256) {
                val next = trans[c]
                if (next != -1) {
                    var f = fail[state]
                    while (transitions[f][c] == -1) {
                        f = fail[f]
                    }
                    val failTarget = transitions[f][c]
                    fail[next] = failTarget
                    val failOut = outputs[failTarget]
                    if (failOut != null) {
                        val dest = outputs[next] ?: mutableListOf<T>().also { outputs[next] = it }
                        dest.addAll(failOut)
                    }
                    queue.add(next)
                }
            }
        }
    }

    fun scanStream(
        stream: InputStream,
        buffer: ByteArray,
        onMatch: (T) -> Unit
    ) {
        var state = 0
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            for (i in 0 until read) {
                val c = buffer[i].toInt() and 0xFF
                while (transitions[state][c] == -1) {
                    state = fail[state]
                }
                state = transitions[state][c]
                val matches = outputs[state]
                if (matches != null) {
                    for (m in matches) {
                        onMatch(m)
                    }
                }
            }
        }
    }
}
