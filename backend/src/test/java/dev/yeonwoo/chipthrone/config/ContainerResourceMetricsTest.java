package dev.yeonwoo.chipthrone.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerResourceMetricsTest {

    @TempDir
    Path cgroupRoot;

    @Test
    void readsCgroupV2MemoryAndCpu() throws Exception {
        Files.writeString(cgroupRoot.resolve("memory.current"), "104857600\n");
        Files.writeString(cgroupRoot.resolve("cpu.stat"), "usage_usec 2500000\nuser_usec 2000000\n");

        ContainerResourceMetrics metrics = new ContainerResourceMetrics(cgroupRoot, new SimpleMeterRegistry());

        assertThat(metrics.memoryBytes()).isEqualTo(104_857_600);
        assertThat(metrics.cpuSeconds()).isEqualTo(2.5);
    }

    @Test
    void readsCgroupV1FallbackFiles() throws Exception {
        Files.createDirectories(cgroupRoot.resolve("memory"));
        Files.createDirectories(cgroupRoot.resolve("cpuacct"));
        Files.writeString(cgroupRoot.resolve("memory/memory.usage_in_bytes"), "52428800\n");
        Files.writeString(cgroupRoot.resolve("cpuacct/cpuacct.usage"), "1250000000\n");

        ContainerResourceMetrics metrics = new ContainerResourceMetrics(cgroupRoot, new SimpleMeterRegistry());

        assertThat(metrics.memoryBytes()).isEqualTo(52_428_800);
        assertThat(metrics.cpuSeconds()).isEqualTo(1.25);
    }
}
