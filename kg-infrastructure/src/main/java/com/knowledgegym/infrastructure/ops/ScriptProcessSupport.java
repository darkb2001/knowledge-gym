package com.knowledgegym.infrastructure.ops;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

final class ScriptProcessSupport {
    private ScriptProcessSupport() {}

    static Result run(Path script, Duration timeout, String... args) {
        if (script == null || !Files.isExecutable(script)) {
            return new Result(false, -1, "Script is not configured or not executable: " + script);
        }
        try {
            var command = new java.util.ArrayList<String>();
            command.add(script.toString());
            command.addAll(java.util.Arrays.asList(args));
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    output.write(process.getInputStream().readAllBytes());
                } catch (Exception ignored) {
                    // Best effort capture; timeout below still applies.
                }
            });
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            reader.join(Math.min(timeout.toMillis(), 5_000L));
            if (!finished) {
                process.destroyForcibly();
                return new Result(false, -1, truncate("Script timed out after " + timeout));
            }
            int exit = process.exitValue();
            String text = truncate(output.toString(StandardCharsets.UTF_8));
            return new Result(exit == 0, exit, text);
        } catch (Exception e) {
            return new Result(false, -1, truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static String truncate(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 4000 ? trimmed : trimmed.substring(0, 4000) + "…";
    }

    record Result(boolean success, int exitCode, String output) {}
}
