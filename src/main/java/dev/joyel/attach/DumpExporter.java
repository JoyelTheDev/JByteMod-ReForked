package dev.joyel.attach;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DumpExporter {
    public static final String INDEX_FILE = "dump-index.csv";

    private DumpExporter() {
    }

    public record Result(int written, int variants, int identicalSkipped, int missing) {
    }

    private record Layout(Map<String, byte[]> files, int variants, int identicalSkipped, int missing) {
    }

    public static Result writeJar(Path target, List<CapturedClassInfo> infos, Map<Long, byte[]> bytes) throws IOException {
        Layout layout = layout(infos, bytes);
        try (OutputStream stream = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(stream)) {
            for (Map.Entry<String, byte[]> file : layout.files().entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return result(layout);
    }

    public static Result writeFolder(Path directory, List<CapturedClassInfo> infos, Map<Long, byte[]> bytes) throws IOException {
        Layout layout = layout(infos, bytes);
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        for (Map.Entry<String, byte[]> file : layout.files().entrySet()) {
            Path destination = root.resolve(file.getKey()).normalize();
            if (!destination.startsWith(root)) {
                throw new IOException("Refusing to write outside the export folder: " + file.getKey());
            }
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(destination, file.getValue());
        }
        return result(layout);
    }

    private static Result result(Layout layout) {
        int written = (int) layout.files().keySet().stream().filter(path -> path.endsWith(".class")).count();
        return new Result(written, layout.variants(), layout.identicalSkipped(), layout.missing());
    }

    private static Layout layout(List<CapturedClassInfo> infos, Map<Long, byte[]> bytes) {
        List<CapturedClassInfo> ordered = new ArrayList<>(infos);
        ordered.sort(Comparator.comparingLong(CapturedClassInfo::id));
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, Set<String>> hashesByName = new HashMap<>();
        Map<String, Integer> variantCounters = new HashMap<>();
        Map<Long, String> paths = new HashMap<>();
        int variants = 0;
        int identical = 0;
        int missing = 0;
        for (CapturedClassInfo info : ordered) {
            byte[] data = bytes.get(info.id());
            if (data == null) {
                missing++;
                continue;
            }
            String relative = safePath(info.name(), info.id());
            Set<String> seen = hashesByName.computeIfAbsent(relative, key -> new HashSet<>());
            if (!seen.isEmpty() && seen.contains(info.sha256())) {
                identical++;
                paths.put(info.id(), "(identical to an earlier entry)");
                continue;
            }
            String path = relative;
            if (!seen.isEmpty()) {
                int variant = variantCounters.merge(relative, 1, Integer::sum);
                path = "_variants/" + variant + "/" + relative;
                variants++;
            }
            seen.add(info.sha256());
            files.put(path, data);
            paths.put(info.id(), path);
        }
        files.put(INDEX_FILE, indexCsv(ordered, paths).getBytes(StandardCharsets.UTF_8));
        return new Layout(files, variants, identical, missing);
    }

    static String safePath(String internalName, long id) {
        if (internalName == null || internalName.isBlank() || internalName.startsWith("/")
                || internalName.contains("\\") || internalName.contains(":") || internalName.contains("\0")) {
            return "_invalid/" + id + ".class";
        }
        for (String segment : internalName.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return "_invalid/" + id + ".class";
            }
        }
        return internalName + ".class";
    }

    static String indexCsv(List<CapturedClassInfo> infos, Map<Long, String> paths) {
        StringBuilder csv = new StringBuilder("id,class,path,loader,loader_type,origin,source,thread,size,sha256,seen,time\n");
        for (CapturedClassInfo info : infos) {
            csv.append(info.id()).append(',')
                    .append(quote(info.name().replace('/', '.'))).append(',')
                    .append(quote(paths.getOrDefault(info.id(), "(not exported)"))).append(',')
                    .append(quote(info.loaderName())).append(',')
                    .append(quote(info.loaderType())).append(',')
                    .append(quote(info.origin() == null ? "" : info.origin())).append(',')
                    .append(quote(info.source())).append(',')
                    .append(quote(info.thread() == null ? "" : info.thread())).append(',')
                    .append(info.size()).append(',')
                    .append(info.sha256()).append(',')
                    .append(info.seen()).append(',')
                    .append(Instant.ofEpochMilli(info.time())).append('\n');
        }
        return csv.toString();
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
