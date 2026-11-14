package io.github.mangi.eta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessPolicyTest {
    @Test
    fun `仅主进程初始化完整 Runtime 依赖`() {
        assertTrue(AppProcessPolicy.shouldInitializeFullRuntime("io.github.mangi.etak", "io.github.mangi.etak"))
        assertFalse(AppProcessPolicy.shouldInitializeFullRuntime("io.github.mangi.etak:voice", "io.github.mangi.etak"))
        assertFalse(AppProcessPolicy.shouldInitializeFullRuntime("io.github.mangi.etak:voice_session", "io.github.mangi.etak"))
    }
}
