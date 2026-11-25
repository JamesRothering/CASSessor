/*
 * Copyright 2026 James Rothering
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

import com.datastax.montecristo.model.os.Ntp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.Test

internal class NtpParserTest {

    private fun parseResource(name: String): Ntp {
        val lines = this.javaClass.getResourceAsStream("/fileLoaders/parsers/os/chrony/$name").reader().readLines()
        return NtpParser.parse(lines)
    }

    @Test
    fun testNtpqStillParsed() {
        val ntpq = listOf(
            "     remote           refid      st t when poll reach   delay   offset  jitter",
            "==============================================================================",
            "*somewhere.isp.uk .LOCL.           1 u  979 1024  377    0.903   -0.199   0.434",
            "+somewhere.isp.uk .LOCL.           1 u  543 1024  377    1.248   -0.084   0.135"
        )
        assertThat(NtpParser.parse(ntpq)).isEqualTo(Ntp("*somewhere.isp.uk", 1024, -0.199, 0.434))
    }

    @Test
    fun testChronyUbuntu2404() {
        val ntp = parseResource("chronyc-sources-ubuntu-24.04.txt")
        assertThat(ntp).isEqualTo(Ntp("*ntp.netlinkify.com", 64, -2.446, null))
    }

    @Test
    fun testChronyVerboseLegendIgnored() {
        val ntp = parseResource("chronyc-sources-v-ubuntu-24.04.txt")
        assertThat(ntp).isEqualTo(Ntp("*prod-ntp-4.ntp4.ps5.cano>", 64, -0.466, null))
    }

    @Test
    fun testChronyUbuntu2204TruncatedName() {
        val ntp = parseResource("chronyc-sources-ubuntu-22.04.txt")
        assertThat(ntp).isEqualTo(Ntp("*prod-ntp-4.ntp4.ps5.cano>", 1024, -0.038, null))
    }

    @Test
    fun testChronyRhel9() {
        val ntp = parseResource("chronyc-sources-rhel-9.txt")
        assertThat(ntp).isEqualTo(Ntp("*time.cloudflare.com", 256, -1.293, null))
    }

    @Test
    fun testChronyRhel9NanosecondOffset() {
        val ntp = parseResource("chronyc-sources-rhel-9-aws.txt")
        assertThat(ntp.server).isEqualTo("*169.254.169.123")
        assertThat(ntp.poll).isEqualTo(16)
        assertThat(ntp.offset).isCloseTo(-0.000492, Offset.offset(1e-12))
        assertThat(ntp.jitter).isNull()
    }

    @Test
    fun testChronyMillisecondAndSecondOffsets() {
        val lines = listOf(
            "MS Name/IP address         Stratum Poll Reach LastRx Last sample",
            "===============================================================================",
            "^* ntp.example.internal          2   7   377    12    +12ms[  +11ms] +/-   40ms"
        )
        assertThat(NtpParser.parse(lines).offset).isEqualTo(12.0)

        val seconds = listOf("^* ntp.example.internal          2   7   377    12     -3s[   -3s] +/-   40ms")
        assertThat(NtpParser.parse(seconds).offset).isEqualTo(-3000.0)
    }

    @Test
    fun testChronyNegativePollExponent() {
        val lines = listOf("#* PHC0                          0  -2   377     1    +25ns[  +31ns] +/-   48ns",
            "^* ptp-gm.example.internal       1  -1   377     0    -88ns[  -90ns] +/-  112ns")
        val ntp = NtpParser.parse(lines)
        assertThat(ntp.server).isEqualTo("*ptp-gm.example.internal")
        assertThat(ntp.poll).isNull()
    }

    @Test
    fun testChronyNoSelectedSource() {
        assertThat(parseResource("chronyc-sources-not-synchronised.txt")).isEqualTo(Ntp("UNKNOWN_SERVER", null, null, null))
    }

    @Test
    fun testChronyDaemonNotRunning() {
        assertThat(parseResource("chronyc-not-running.txt")).isEqualTo(Ntp("UNKNOWN_SERVER", null, null, null))
    }
}
