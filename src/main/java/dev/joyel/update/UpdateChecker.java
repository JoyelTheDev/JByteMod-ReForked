package dev.joyel.update;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class UpdateChecker {

    private static final String RELEASES_URL = "https://github.com/JoyelTheDev/JByteMod-ReForked/releases";
    private static final String RELEASES_API =
            "https://api.github.com/repos/JoyelTheDev/JByteMod-ReForked/releases?per_page=30";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS    = 6000;

    private UpdateChecker() {}

    public static UpdateRelease findUpdate(String currentVersionStr, String responseBody) {
        SemanticVersion current = SemanticVersion.parse(currentVersionStr);
        if (current == null || responseBody == null || responseBody.trim().isEmpty()) return null;

        JsonArray releases;
        try {
            JsonElement el = new JsonParser().parse(responseBody);
            if (!el.isJsonArray()) return null;
            releases = el.getAsJsonArray();
        } catch (Exception e) {
            return null;
        }

        boolean includePrereleases = current.isPrerelease();
        List<Candidate> candidates = new ArrayList<Candidate>();

        for (JsonElement el : releases) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            boolean isDraft = obj.has("draft") && obj.get("draft").getAsBoolean();
            if (isDraft) continue;

            boolean isPrerelease = obj.has("prerelease") && obj.get("prerelease").getAsBoolean();
            String tagName = obj.has("tag_name") ? obj.get("tag_name").getAsString() : null;
            String htmlUrl = obj.has("html_url") ? obj.get("html_url").getAsString() : RELEASES_URL;
            String body    = obj.has("body")     ? obj.get("body").getAsString()     : "";

            SemanticVersion version = SemanticVersion.parse(tagName);
            if (version == null) continue;

            if (!includePrereleases && (isPrerelease || version.isPrerelease())) continue;

            if (version.compareTo(current) > 0) {
                candidates.add(new Candidate(version, htmlUrl, body));
            }
        }

        if (candidates.isEmpty()) return null;

        Collections.sort(candidates, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                return a.version.compareTo(b.version);
            }
        });

        Candidate best = candidates.get(candidates.size() - 1);
        String url = (best.url == null || best.url.trim().isEmpty()) ? RELEASES_URL : best.url;
        return new UpdateRelease(best.version.toString(), url, best.changelog);
    }

    public static UpdateRelease checkForUpdate(String currentVersion) {
        String body = fetchReleasesJson();
        if (body == null) return null;
        return findUpdate(currentVersion, body);
    }

    private static String fetchReleasesJson() {
        try {
            URL url = new URL(RELEASES_API);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "JByteMod-ReForked-Update-Checker");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.connect();
            InputStream is = conn.getInputStream();
            InputStreamReader reader = new InputStreamReader(is, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Candidate {
        final SemanticVersion version;
        final String url;
        final String changelog;

        Candidate(SemanticVersion version, String url, String changelog) {
            this.version   = version;
            this.url       = url;
            this.changelog = changelog;
        }
    }
}
