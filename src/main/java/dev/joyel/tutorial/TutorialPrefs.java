package dev.joyel.tutorial;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public final class TutorialPrefs {

    private static final String FILE_NAME  = "tutorial.prefs";
    private static final String KEY_SEEN   = "tutorial_seen";
    private static final String KEY_STEP   = "last_step";

    private TutorialPrefs() {}

    private static File getPrefsFile() {
        return new File(System.getProperty("user.home"),
                ".jbytemod" + File.separator + FILE_NAME);
    }

    private static Properties load() {
        Properties p = new Properties();
        File f = getPrefsFile();
        if (!f.exists()) return p;
        try {
            FileInputStream fis = new FileInputStream(f);
            p.load(fis);
            fis.close();
        } catch (Exception ignored) {}
        return p;
    }

    private static void save(Properties p) {
        try {
            File f = getPrefsFile();
            f.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(f);
            p.store(fos, "JByteMod tutorial preferences");
            fos.close();
        } catch (Exception ignored) {}
    }

    public static boolean hasSeenTutorial() {
        return "true".equals(load().getProperty(KEY_SEEN, "false"));
    }

    public static void markSeen() {
        Properties p = load();
        p.setProperty(KEY_SEEN, "true");
        save(p);
    }

    public static void reset() {
        Properties p = load();
        p.setProperty(KEY_SEEN, "false");
        p.remove(KEY_STEP);
        save(p);
    }

    public static int getSavedStep() {
        try {
            return Integer.parseInt(load().getProperty(KEY_STEP, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void saveStep(int step) {
        Properties p = load();
        p.setProperty(KEY_STEP, String.valueOf(step));
        save(p);
    }
}
