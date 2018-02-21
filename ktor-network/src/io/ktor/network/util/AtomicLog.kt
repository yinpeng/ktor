package io.ktor.network.util

import java.io.*
import java.util.concurrent.atomic.*

public class AtomicLog(size: Int = 128) {
    private val mask = size - 1
    private val a = arrayOfNulls<String>(size)
    private val index = AtomicInteger(0)

    init {
        check(size and mask == 0) { "size must be power of 2: $size"}
    }

    public operator fun invoke(text: String) {
        val i = index.getAndIncrement()
        val thread = Thread.currentThread().name
        a[i and mask] = "$i [$thread] $text"
    }

    public fun dump(out: PrintStream) {
        val start = index.get() and mask
        var i = start
        do {
            a[i]?.let { out.println(it) }
            i = (i + 1) and mask
        } while (i != start)
    }
}
