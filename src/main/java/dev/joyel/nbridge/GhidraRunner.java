package dev.joyel.nbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class GhidraRunner {
    private GhidraRunner() {
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static Path headlessScript(Path home) {
        return home.resolve("support").resolve(windows() ? "analyzeHeadless.bat" : "analyzeHeadless");
    }

    public static Path guiScript(Path home) {
        return home.resolve(windows() ? "ghidraRun.bat" : "ghidraRun");
    }

    public static boolean isValidHome(Path home) {
        return home != null && Files.isRegularFile(headlessScript(home)) && Files.isRegularFile(guiScript(home));
    }

    public static Path projectFile(Path projectDirectory, String projectName) {
        return projectDirectory.resolve(projectName + ".gpr");
    }

    public static int analyze(Path home, GhidraExporter.Result export, Path projectDirectory, String projectName,
                              Consumer<String> output) throws IOException, InterruptedException {
        if (!isValidHome(home)) {
            throw new IOException("Not a Ghidra installation folder: " + home);
        }
        if (export.libraries().isEmpty()) {
            throw new IOException("There are no libraries to import.");
        }
        Files.createDirectories(projectDirectory);
        List<String> command = new ArrayList<>();
        if (windows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(headlessScript(home).toString());
        command.add(projectDirectory.toString());
        command.add(projectName);
        command.add("-import");
        for (Path library : export.libraries()) {
            command.add(library.toString());
        }
        command.add("-overwrite");
        command.add("-scriptPath");
        command.add(export.directory().toString());
        command.add("-postScript");
        command.add(GhidraExporter.SCRIPT_NAME);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.accept(line);
            }
        }
        return process.waitFor();
    }

    public static void launchGui(Path home, Path projectFile) throws IOException {
        if (!isValidHome(home)) {
            throw new IOException("Not a Ghidra installation folder: " + home);
        }
        List<String> command = new ArrayList<>();
        if (windows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(guiScript(home).toString());
        if (projectFile != null && Files.isRegularFile(projectFile)) {
            command.add(projectFile.toString());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.directory(home.toFile());
        builder.start();
    }
}
