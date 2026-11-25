/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datastax.montecristo.fileLoaders.parsers.os

import com.datastax.montecristo.fileLoaders.parsers.IFileParser
import com.datastax.montecristo.model.os.Ntp

/**
 * Parses either `ntpq -p` or `chronyc sources` output, returning the currently selected time source.
 */
object NtpParser : IFileParser<Ntp> {

    private val chronySelectedSource = Regex(
        "^\\^\\*\\s+(\\S+)\\s+\\d+\\s+(-?\\d+)\\s+[0-7]+\\s+\\S+\\s+([+-]?[0-9.]+)(ns|us|ms|s)\\s*\\["
    )

    override fun parse(data: List<String>): Ntp {

        val regex = "^\\*[^ ]+".toRegex()
        if (data.isNotEmpty()) {
            data.forEach {
                if (regex.containsMatchIn(it)) {
                    val serverName = it.split(" ").getOrNull(0) ?: "UNKNOWN_SERVER"
                    return parseOffsetAndJitter(serverName, it)
                }
                val chronyMatch = chronySelectedSource.find(it)
                if (chronyMatch != null) {
                    return parseChronySource(chronyMatch)
                }
            }
        }
        // if we get here, we had no data, or we could not parse what we had
        return Ntp("UNKNOWN_SERVER", null, null, null)
    }

    /**
     * chronyc reports poll as a log2 exponent of seconds and the offset with a unit suffix,
     * whereas the Ntp model follows ntpq: poll in seconds and offset in milliseconds.
     * `chronyc sources` has no jitter column, so jitter is left unknown.
     */
    private fun parseChronySource(match: MatchResult): Ntp {
        val (name, pollExponent, offsetValue, offsetUnit) = match.destructured
        val exponent = pollExponent.toInt()
        // sub-second polling intervals (negative exponents) cannot be expressed in whole seconds
        val poll = if (exponent in 0..30) 1 shl exponent else null
        val offset = offsetValue.toDouble()
        val offsetMs = when (offsetUnit) {
            "ns" -> offset / 1_000_000.0
            "us" -> offset / 1_000.0
            "ms" -> offset
            else -> offset * 1_000.0
        }
        return Ntp("*$name", poll, offsetMs, null)
    }


    private fun parseOffsetAndJitter(serverName: String, line: String?): Ntp {
        val r = Regex("([0-9]+)\\s+[0-9]+\\s+-?[0-9]+\\.[0-9]+\\s+(-?[0-9]+\\.[0-9]+)\\s+(-?[0-9]+\\.[0-9]+)$")
        val groups = r.find(line ?: "")?.groups
        val poll = groups?.get(1)?.value?.toInt()
        val offset = groups?.get(2)?.value?.toDouble()
        val jitter = groups?.get(3)?.value?.toDouble()
        return Ntp(serverName, poll, offset, jitter)
    }
}
