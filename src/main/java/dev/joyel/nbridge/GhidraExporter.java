package dev.joyel.nbridge;

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GhidraExporter {
    public static final String SCRIPT_NAME = "JByteModJniLabels.java";
    public static final String DATA_NAME = "jbytemod_natives.tsv";
    private static final String SCRIPT_RESOURCE = "/resources/ghidra/JByteModJniLabels.java.txt";

    private GhidraExporter() {
    }

    public record Result(Path directory, List<Path> libraries, Path script, int exportRows, int registerRows) {
    }

    public static String flatName(String path) {
        String name = path.replace('\\', '/').replace("/", "__");
        return name.replaceAll("[^A-Za-z0-9._+-]", "_");
    }

    public static Result export(NativeScan scan, Collection<NativeLibrary> selected, Map<String, byte[]> resources,
                                Path directory) throws IOException {
        Files.createDirectories(directory);
        Set<String> selectedPaths = new HashSet<>();
        List<Path> written = new ArrayList<>();
        for (NativeLibrary library : selected) {
            byte[] data = resources.get(library.path());
            if (data == null) {
                continue;
            }
            Path target = directory.resolve(flatName(library.path()));
            Files.write(target, data);
            written.add(target);
            selectedPaths.add(library.path());
        }

        StringBuilder table = new StringBuilder();
        int exportRows = 0;
        int registerRows = 0;
        for (NativeMethodInfo method : scan.methods()) {
            Set<String> matchedLibraries = new HashSet<>();
            for (NativeMatch match : method.matches()) {
                if (!selectedPaths.contains(match.library().path())) {
                    continue;
                }
                matchedLibraries.add(match.library().path());
                appendRow(table, "EXPORT", flatName(match.library().path()), match.export().name(), method);
                exportRows++;
            }
            for (NativeLibrary library : selected) {
                if (!selectedPaths.contains(library.path()) || !library.jniOnLoad()
                        || matchedLibraries.contains(library.path())) {
                    continue;
                }
                appendRow(table, "REGISTER", flatName(library.path()), "", method);
                registerRows++;
            }
        }
        Files.writeString(directory.resolve(DATA_NAME), table.toString(), StandardCharsets.UTF_8);

        Path script = directory.resolve(SCRIPT_NAME);
        try (InputStream stream = GhidraExporter.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("The bundled Ghidra script is missing from the application.");
            }
            Files.write(script, stream.readAllBytes());
        }
        return new Result(directory, List.copyOf(written), script, exportRows, registerRows);
    }

    private static void appendRow(StringBuilder table, String type, String library, String symbol,
                                  NativeMethodInfo method) {
        boolean isStatic = (method.methodNode().access & Opcodes.ACC_STATIC) != 0;
        table.append(type).append('\t')
                .append(clean(library)).append('\t')
                .append(clean(symbol)).append('\t')
                .append(clean(method.owner().replace('/', '.'))).append('\t')
                .append(clean(method.name())).append('\t')
                .append(clean(method.descriptor())).append('\t')
                .append(isStatic ? "static" : "instance").append('\n');
    }

    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
