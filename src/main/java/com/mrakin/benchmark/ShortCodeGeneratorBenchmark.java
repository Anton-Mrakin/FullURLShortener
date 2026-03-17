package com.mrakin.benchmark;

import com.mrakin.usecases.generator.Base62ShortCodeGenerator;
import com.mrakin.usecases.generator.RandomStringShortCodeGenerator;
import com.mrakin.usecases.generator.Sha256ShortCodeGenerator;
import com.mrakin.usecases.generator.ShortCodeGenerator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Threads(Threads.MAX)
public class ShortCodeGeneratorBenchmark {

    private ShortCodeGenerator sha256Generator;
    private ShortCodeGenerator randomStringGenerator;
    private ShortCodeGenerator base62Generator;
    private String testUrl = "https://very-long-original-url-for-benchmarking-purposes.com/path/to/resource?query=123";

    @Setup
    public void setup() {
        sha256Generator = new Sha256ShortCodeGenerator();
        setField(sha256Generator, "shortCodeLength", 8);

        randomStringGenerator = new RandomStringShortCodeGenerator();
        setField(randomStringGenerator, "shortCodeLength", 8);

        base62Generator = new Base62ShortCodeGenerator();
        setField(base62Generator, "shortCodeLength", 8);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    @Benchmark
    public String testSha256() {
        return sha256Generator.generate(testUrl);
    }

    @Benchmark
    public String testRandomString() {
        return randomStringGenerator.generate(testUrl);
    }

    @Benchmark
    public String testBase62() {
        return base62Generator.generate(testUrl);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ShortCodeGeneratorBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
