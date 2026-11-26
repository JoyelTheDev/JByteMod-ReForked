package dev.joyel.plugins;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public final class PluginRepositoryService {
    private static final long MAX_REGISTRY_SIZE = 2L * 1024 * 1024;
    private static final long MAX_PLUGIN_SIZE = 100L * 1024 * 1024;
    private static final int MAX_PLUGINS_PER_REPOSITORY = 2000;

    public record RepositorySource(String url, boolean builtinDefault) {
        public String displayName() {
            return url;
        }
    }

    public record RepositoryPlugin(String id, String name, String version, String author, String description,
                                   String minimumJByteModVersion, String website, String downloadUrl, String sha256,
                                   String fileName, String repositoryName, String repositoryUrl) {
        public boolean downloadable() {
            return downloadUrl != null && !downloadUrl.isBlank() && sha256 != null && sha256.matches("(?i)[0-9a-f]{64}");
        }

        public boolean compatibleWith(String applicationVersion) {
            return minimumJByteModVersion == null || minimumJByteModVersion.isBlank()
                    || VersionCompare.compare(applicationVersion, minimumJByteModVersion) >= 0;
        }
    }

    public record Catalog(List<RepositoryPlugin> plugins, List<String> messages) {
    }

    private record RegistryDocument(int schemaVersion, String name, List<RegistryPlugin> plugins) {
    }

    private record RegistryPlugin(String id, String name, String version, String author, String description,
                                  String minimumJByteModVersion, String website, String downloadUrl, String sha256,
                                  String fileName) {
    }

    private record LoadedRegistry(RegistryDocument document, boolean cached) {
    }

    private final PluginSettings settings;
    private final Path cacheFolder;
    private final Path downloadFolder;
    private final List<String> defaultSources;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Gson gson = new Gson();

    public PluginRepositoryService(PluginSettings settings, Path cacheFolder, Path downloadFolder,
                                   List<String> defaultSources) {
        this.settings = settings;
        this.cacheFolder = cacheFolder;
        this.downloadFolder = downloadFolder;
        this.defaultSources = defaultSources == null ? List.of() : List.copyOf(defaultSources);
    }

    public List<RepositorySource> sources() {
        Set<String> seen = new LinkedHashSet<>();
        List<RepositorySource> result = new ArrayList<>();
        for (String url : defaultSources) {
            if (!url.isBlank() && seen.add(url)) {
                result.add(new RepositorySource(url, true));
            }
        }
        for (String url : settings.repositories()) {
            if (seen.add(url)) {
                result.add(new RepositorySource(url, false));
            }
        }
        return List.copyOf(result);
    }

    public void addSource(String repository) {
        String normalized = normalizeSource(repository);
        Set<String> custom = new LinkedHashSet<>(settings.repositories());
        custom.add(normalized);
        settings.saveRepositories(custom);
    }

    public void removeSource(RepositorySource source) {
        if (source.builtinDefault()) {
            throw new IllegalArgumentException("The default plugin repository cannot be removed");
        }
        Set<String> custom = new LinkedHashSet<>(settings.repositories());
        custom.remove(source.url());
        settings.saveRepositories(custom);
    }

    public Catalog loadCatalog() {
        Map<String, RepositoryPlugin> plugins = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();
        List<RepositorySource> sources = sources();
        if (sources.isEmpty()) {
            messages.add("No plugin repository is configured. Use \"Repositories...\" to add one.");
        }
        for (RepositorySource source : sources) {
            try {
                LoadedRegistry registry = loadRegistry(source);
                String repositoryName = registry.document().name();
                if (registry.cached()) {
                    messages.add(repositoryName + ": offline, showing cached data");
                }
                int accepted = 0;
                for (RegistryPlugin raw : registry.document().plugins()) {
                    if (++accepted > MAX_PLUGINS_PER_REPOSITORY) {
                        messages.add(repositoryName + ": too many entries, the rest were ignored");
                        break;
                    }
                    try {
                        RepositoryPlugin entry = validate(raw, repositoryName, source.url());
                        if (plugins.putIfAbsent(entry.id(), entry) != null) {
                            messages.add("Ignored duplicate " + entry.id() + " from " + repositoryName);
                        }
                    } catch (IllegalArgumentException invalid) {
                        messages.add(repositoryName + ": skipped an entry (" + invalid.getMessage() + ")");
                    }
                }
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                messages.add(source.displayName() + ": " + conciseMessage(failure));
            }
        }
        List<RepositoryPlugin> sorted = new ArrayList<>(plugins.values());
        sorted.sort(Comparator.comparing(plugin -> plugin.name().toLowerCase(Locale.ROOT)));
        return new Catalog(List.copyOf(sorted), List.copyOf(messages));
    }

    public Path downloadPlugin(RepositoryPlugin plugin, IntConsumer progress) throws IOException, InterruptedException {
        if (!plugin.downloadable()) {
            throw new IOException("This plugin does not provide a verified download");
        }
        URI uri = checkedUri(plugin.downloadUrl());
        Files.createDirectories(downloadFolder);
        Path temporary = Files.createTempFile(downloadFolder, "plugin-", ".download");
        MessageDigest digest = sha256();
        try {
            long copied = 0;
            long total = -1;
            InputStream source;
            if (uri.getScheme().equalsIgnoreCase("file")) {
                Path path = Path.of(uri);
                total = Files.size(path);
                source = Files.newInputStream(path);
            } else {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException("The download returned HTTP " + response.statusCode());
                }
                total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                source = response.body();
            }
            if (total > MAX_PLUGIN_SIZE) {
                source.close();
                throw new IOException("The plugin exceeds the 100 MB download limit");
            }
            try (InputStream input = source; OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    copied += read;
                    if (copied > MAX_PLUGIN_SIZE) {
                        throw new IOException("The plugin exceeds the 100 MB download limit");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    if (total > 0) {
                        progress.accept((int) Math.min(99, copied * 100 / total));
                    }
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(plugin.sha256())) {
                throw new IOException("SHA-256 mismatch: the registry lists " + plugin.sha256() + " but the download is " + actual
                        + ". The file was discarded.");
            }
            if (!looksLikeZip(temporary)) {
                throw new IOException("The download is not a JAR file");
            }
            progress.accept(100);
            return temporary;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    public void deleteDownload(Path file) {
        try {
            if (file != null && file.toAbsolutePath().normalize().startsWith(downloadFolder.toAbsolutePath().normalize())) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean looksLikeZip(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] magic = input.readNBytes(4);
            return magic.length == 4 && magic[0] == 'P' && magic[1] == 'K';
        }
    }

    private LoadedRegistry loadRegistry(RepositorySource source) throws IOException, InterruptedException {
        Exception lastFailure = null;
        for (URI candidate : registryCandidates(source.url())) {
            try {
                byte[] bytes = fetch(candidate, MAX_REGISTRY_SIZE);
                RegistryDocument document = parseRegistry(bytes, candidate.toString());
                Files.createDirectories(cacheFolder);
                Files.write(cachePath(source.url()), bytes);
                return new LoadedRegistry(document, false);
            } catch (IOException | RuntimeException failure) {
                lastFailure = failure;
            }
        }
        Path cached = cachePath(source.url());
        if (Files.isRegularFile(cached)) {
            return new LoadedRegistry(parseRegistry(Files.readAllBytes(cached), source.url()), true);
        }
        if (lastFailure instanceof IOException io) {
            throw io;
        }
        throw new IOException(lastFailure == null ? "Could not load the repository" : conciseMessage(lastFailure), lastFailure);
    }

    private byte[] fetch(URI uri, long limit) throws IOException, InterruptedException {
        if (uri.getScheme().equalsIgnoreCase("file")) {
            Path path = Path.of(uri);
            if (Files.size(path) > limit) {
                throw new IOException("The repository index exceeds 2 MB");
            }
            return Files.readAllBytes(path);
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri);
        }
        if (response.body().length > limit) {
            throw new IOException("The repository index exceeds 2 MB");
        }
        return response.body();
    }

    private RegistryDocument parseRegistry(byte[] bytes, String source) throws IOException {
        try {
            RegistryDocument document = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), RegistryDocument.class);
            if (document == null || document.schemaVersion() != 1) {
                throw new IOException("Unsupported or missing schemaVersion in " + source);
            }
            String name = document.name() == null || document.name().isBlank() ? "Unnamed repository" : clip(document.name().trim(), 100);
            List<RegistryPlugin> plugins = document.plugins() == null ? List.of() : document.plugins();
            return new RegistryDocument(1, name, plugins);
        } catch (JsonParseException failure) {
            throw new IOException("Invalid repository JSON from " + source, failure);
        }
    }

    private RepositoryPlugin validate(RegistryPlugin plugin, String repositoryName, String repositoryUrl) {
        if (plugin == null) {
            throw new IllegalArgumentException("empty entry");
        }
        String id = required(plugin.id(), "id");
        if (!id.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("invalid id " + clip(id, 40));
        }
        String name = clip(required(plugin.name(), "name"), 100);
        String version = clip(required(plugin.version(), "version"), 50);
        String author = clip(required(plugin.author(), "author"), 100);
        String website = blank(plugin.website());
        if (!website.isEmpty() && !website.toLowerCase(Locale.ROOT).startsWith("http://") && !website.toLowerCase(Locale.ROOT).startsWith("https://")) {
            website = "";
        }
        String downloadUrl = blank(plugin.downloadUrl());
        if (!downloadUrl.isEmpty()) {
            try {
                checkedUri(downloadUrl);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(id + ": " + invalid.getMessage());
            }
        }
        return new RepositoryPlugin(id, name, version, author, clip(blank(plugin.description()), 2000),
                blank(plugin.minimumJByteModVersion()), website, downloadUrl,
                blank(plugin.sha256()).toLowerCase(Locale.ROOT), clip(blank(plugin.fileName()), 120), repositoryName, repositoryUrl);
    }

    private List<URI> registryCandidates(String source) {
        URI uri = checkedUri(source);
        if (uri.getScheme().equalsIgnoreCase("file")) {
            Path path = Path.of(uri);
            return List.of(Files.isDirectory(path) ? path.resolve("plugins.json").toUri() : uri);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        if (host.equals("github.com")) {
            String[] parts = Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).toArray(String[]::new);
            if (parts.length == 2) {
                return List.of(
                        URI.create("https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/main/plugins.json"),
                        URI.create("https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/master/plugins.json"));
            }
        }
        if (path.endsWith(".json")) {
            return List.of(uri);
        }
        return List.of(checkedUri(source.replaceAll("/+$", "") + "/plugins.json"));
    }

    private static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("localhost") || lower.equals("::1") || lower.equals("[::1]") || lower.startsWith("127.");
    }

    static URI checkedUri(String value) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid URL: " + value);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("file")) {
            return uri;
        }
        if (scheme.equals("https") && uri.getHost() != null) {
            return uri;
        }
        if (scheme.equals("http") && isLoopback(uri.getHost())) {
            return uri;
        }
        if (scheme.equals("http")) {
            throw new IllegalArgumentException("Plain HTTP is not allowed for remote hosts. Use HTTPS.");
        }
        throw new IllegalArgumentException("Unsupported URL: " + value);
    }

    static String normalizeSource(String repository) {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("Enter a repository URL or a local folder");
        }
        String text = repository.trim();
        if (!text.matches("(?i)^[a-z][a-z0-9+.-]*:.*") || text.matches("(?i)^[a-z]:[\\\\/].*")) {
            Path local = Path.of(text).toAbsolutePath().normalize();
            if (!Files.exists(local)) {
                throw new IllegalArgumentException("That path does not exist: " + text);
            }
            return local.toUri().toString().replaceAll("/+$", "");
        }
        URI uri = checkedUri(text);
        String normalized = uri.toString().replaceAll("/+$", "");
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private Path cachePath(String source) {
        byte[] bytes = sha256().digest(source.getBytes(StandardCharsets.UTF_8));
        return cacheFolder.resolve(HexFormat.of().formatHex(bytes) + ".json");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.trim();
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int limit) {
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private static String conciseMessage(Throwable failure) {
        if (failure instanceof java.net.ConnectException) {
            return "Could not connect to the server";
        }
        if (failure instanceof java.net.http.HttpTimeoutException) {
            return "The server did not respond in time";
        }
        if (failure instanceof java.net.UnknownHostException) {
            return "Unknown host " + failure.getMessage();
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
