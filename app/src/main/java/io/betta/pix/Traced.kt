package io.betta.pix

import android.os.Trace

/**
 * [block], shown as a section called [name] in a system trace (Perfetto) of a build that may be profiled. In any
 * other build, a trace doesn't see it.
 */
inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
