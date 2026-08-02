package dev.yeonwoo.chipthrone.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ContainerResourceMetrics {

    private static final Path DEFAULT_CGROUP_ROOT = Path.of("/sys/fs/cgroup");

    private final Path cgroupRoot;

    @Autowired
    public ContainerResourceMetrics(MeterRegistry registry) {
        this(DEFAULT_CGROUP_ROOT, registry);
    }

    ContainerResourceMetrics(Path cgroupRoot, MeterRegistry registry) {
        this.cgroupRoot = cgroupRoot;
        Gauge.builder("chipthrone.container.memory.current.bytes", this, ContainerResourceMetrics::memoryBytes)
                .description("Current container cgroup memory usage")
                .register(registry);
        Gauge.builder("chipthrone.container.cpu.usage.seconds", this, ContainerResourceMetrics::cpuSeconds)
                .description("Cumulative container cgroup CPU usage")
                .register(registry);
    }

    double memoryBytes() {
        return readNumber(
                cgroupRoot.resolve("memory.current"),
                cgroupRoot.resolve("memory/memory.usage_in_bytes"),
                1.0
        );
    }

    double cpuSeconds() {
        Path v2 = cgroupRoot.resolve("cpu.stat");
        if (Files.isRegularFile(v2)) {
            try {
                return Files.readAllLines(v2).stream()
                        .filter(line -> line.startsWith("usage_usec "))
                        .map(line -> line.substring("usage_usec ".length()).trim())
                        .mapToDouble(Double::parseDouble)
                        .findFirst()
                        .orElse(Double.NaN) / 1_000_000.0;
            } catch (IOException | NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        return readNumber(
                cgroupRoot.resolve("cpuacct/cpuacct.usage"),
                cgroupRoot.resolve("cpuacct.usage"),
                1_000_000_000.0
        );
    }

    private double readNumber(Path primary, Path fallback, double divisor) {
        Path source = Files.isRegularFile(primary) ? primary : fallback;
        try {
            return Double.parseDouble(Files.readString(source).trim()) / divisor;
        } catch (IOException | NumberFormatException ignored) {
            return Double.NaN;
        }
    }
}
