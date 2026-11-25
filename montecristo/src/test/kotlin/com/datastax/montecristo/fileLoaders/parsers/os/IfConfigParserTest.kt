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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class IfConfigParserTest {

    @Test
    fun testBasicFileParse() {
        val ifConfigTxt = this.javaClass.getResourceAsStream("/fileLoaders/parsers/os/ifconfig.txt").reader().readLines()
        val ifConfig = IfConfigParser.parse (ifConfigTxt)
        assertThat(ifConfig.bindings.size).isEqualTo(6)
        assertThat(ifConfig.bindings.containsKey("bond0")).isTrue()
        assertThat(ifConfig.bindings.get("bond0")).isEqualTo("10.102.98.71")

        assertThat(ifConfig.bindings.containsKey("enp179s0f0")).isTrue()
        assertThat(ifConfig.bindings.get("enp179s0f0")).isNull()

        assertThat(ifConfig.bindings.containsKey("enp179s0f1")).isTrue()
        assertThat(ifConfig.bindings.get("enp179s0f1")).isNull()

        assertThat(ifConfig.bindings.containsKey("enp27s0f0")).isTrue()
        assertThat(ifConfig.bindings.get("enp27s0f0")).isEqualTo("10.102.98.7")

        assertThat(ifConfig.bindings.containsKey("enp27s0f1")).isTrue()
        assertThat(ifConfig.bindings.get("enp27s0f1")).isEqualTo("10.102.254.104")

        assertThat(ifConfig.bindings.containsKey("lo")).isTrue()
        assertThat(ifConfig.bindings.get("lo")).isEqualTo("127.0.0.1")
    }

    private fun parseResource(name: String) =
        IfConfigParser.parse(this.javaClass.getResourceAsStream("/fileLoaders/parsers/os/$name").reader().readLines())

    @Test
    fun testIpAddrRhel9() {
        val ifConfig = parseResource("ip_addr_rhel9.txt")
        assertThat(ifConfig.bindings).containsOnlyKeys("lo", "ens192", "ens192:1", "ens224", "ens256")
        assertThat(ifConfig.get("lo")).isEqualTo("127.0.0.1")
        // the primary address wins over the secondary one, which is exposed under its alias label
        assertThat(ifConfig.get("ens192")).isEqualTo("10.20.30.41")
        assertThat(ifConfig.get("ens192:1")).isEqualTo("10.20.30.50")
        assertThat(ifConfig.get("ens224")).isEqualTo("192.168.50.41")
        // ipv6 only
        assertThat(ifConfig.bindings).containsEntry("ens256", null)
    }

    @Test
    fun testIpAddrUbuntu2204() {
        val ifConfig = parseResource("ip_addr_ubuntu2204.txt")
        assertThat(ifConfig.bindings).containsOnlyKeys("lo", "ens5", "ens6")
        assertThat(ifConfig.get("lo")).isEqualTo("127.0.0.1")
        assertThat(ifConfig.get("ens5")).isEqualTo("172.31.22.114")
        assertThat(ifConfig.get("ens6")).isEqualTo("172.31.40.7")
    }

    @Test
    fun testIpAddrUbuntu2404() {
        val ifConfig = parseResource("ip_addr_ubuntu2404.txt")
        assertThat(ifConfig.bindings).containsOnlyKeys("lo", "enp0s3", "docker0")
        assertThat(ifConfig.get("lo")).isEqualTo("127.0.0.1")
        assertThat(ifConfig.get("enp0s3")).isEqualTo("172.30.0.2")
        assertThat(ifConfig.get("docker0")).isEqualTo("172.17.0.1")
    }

    @Test
    fun testIpAddrIpv6SeenBeforeIpv4() {
        val ifConfig = IfConfigParser.parse(listOf(
            "2: eth0    inet6 fe80::1/64 scope link \\       valid_lft forever preferred_lft forever",
            "2: eth0    inet 10.0.0.5/24 brd 10.0.0.255 scope global eth0\\       valid_lft forever preferred_lft forever"
        ))
        assertThat(ifConfig.get("eth0")).isEqualTo("10.0.0.5")
    }

    @Test
    fun testEmptyInput() {
        assertThat(IfConfigParser.parse(emptyList()).get("eth0")).isNull()
    }

}