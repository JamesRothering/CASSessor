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
import com.datastax.montecristo.model.os.IfConfig

/**
 * Parses either `ifconfig` output or `ip -o addr show` output (the format is detected automatically),
 * mapping each interface name to its first IPv4 address, or null if it has none.
 */
object IfConfigParser : IFileParser<IfConfig> {

    // e.g. "2: ens192    inet 10.20.30.41/24 brd 10.20.30.255 scope global noprefixroute ens192\       valid_lft forever preferred_lft forever"
    private val ipAddrLineRegex = Regex("^\\d+:\\s+(\\S+)\\s+(inet6?)\\s+(\\S+)(.*)$")

    override fun parse(data: List<String>): IfConfig {
        return if (data.any { ipAddrLineRegex.matches(it.trim()) }) {
            parseIpAddr(data)
        } else {
            parseIfConfig(data)
        }
    }

    private fun parseIfConfig(data: List<String>): IfConfig {
        // ifconfig is split into interfaces separated by a blank line, so the first job is to split the
        // overall list<string> into a set of strings, 1 per interface so they can be processed in turn.
        // rejoin to a single string then split by the double new line which occurs due to the blank line
        val splitRegex = Regex("\\n{2}")
        val interfaceSections = data.joinToString("\n").split(splitRegex)

        val bindingData =  // per interface the ipv4 inetAddress is not guaranteed
            // If the regex has found a match, it has an entry like this :  inet 10.102.98.71
            interfaceSections.associate {
                val interfaceName = it.split(":")[0]
                val inetRegex = Regex("inet (?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
                // per interface the ipv4 inetAddress is not guaranteed
                // If the regex has found a match, it has an entry like this :  inet 10.102.98.71
                val inetAddress = inetRegex.find(it)?.groups?.get(0)?.value?.split(" ")?.get(1)
                Pair(interfaceName, inetAddress)
            }
        return IfConfig(bindingData)
    }

    private fun parseIpAddr(data: List<String>): IfConfig {
        // one line per address; an interface with several addresses appears on several lines, and an
        // interface without any address does not appear at all.
        val bindingData = LinkedHashMap<String, String?>()
        data.mapNotNull { ipAddrLineRegex.matchEntire(it.trim()) }.forEach { match ->
            val (interfaceName, family, address, rest) = match.destructured
            if (family == "inet") {
                val ipv4 = address.substringBefore("/")
                if (bindingData[interfaceName] == null) {
                    bindingData[interfaceName] = ipv4
                }
                // the address label is the last token before the "\" separator. An alias such as "ens192:1"
                // is reported as its own interface by ifconfig, so expose it the same way here.
                val label = rest.substringBefore("\\").trim().split(Regex("\\s+")).last()
                if (label.startsWith("$interfaceName:") && bindingData[label] == null) {
                    bindingData[label] = ipv4
                }
            } else if (!bindingData.containsKey(interfaceName)) {
                bindingData[interfaceName] = null
            }
        }
        return IfConfig(bindingData)
    }
}
