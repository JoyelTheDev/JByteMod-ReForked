package de.xbrowniecodez.jbytemod;

import dev.joyel.update.UpdateService;
import lombok.Getter;
import de.xbrowniecodez.jbytemod.discord.Discord;
import me.grax.jbytemod.logging.Logging;
import me.grax.jbytemod.utils.FileUtils;
import org.apache.commons.cli.*;

import javax.swing.*;
import java.io.File;
import java.lang.System.Logger.Level;

@Getter
public enum Main {
    INSTANCE;

    private JByteMod jByteMod;
    private Logging logger;
    private Discord discord;

    public static void main(String[] args) {
        Main.INSTANCE.start(args);
    }

    private void start(String[] args) {
        CommandLine cmd = parseCommandLine(args);
        this.logger = new Logging();
        try {
            this.jByteMod = new JByteMod(false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JByteMod", e);
        }
        if (cmd.hasOption("help")) {
            printHelpAndExit();
        }
        this.discord = new Discord("1184572566795468881");
        SwingUtilities.invokeLater(() -> {
            this.jByteMod.setVisible(true);
            loadFileIfNeeded(cmd, jByteMod);
        });
        String currentVersion = this.jByteMod.getVersion().toString();
        UpdateService.checkAsync(currentVersion);
    }

    private CommandLine parseCommandLine(String[] args) {
        Options options = buildCommandLineOptions();
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            throw new RuntimeException("An error occurred while parsing the commandline", e);
        }
    }

    private Options buildCommandLineOptions() {
        Options options = new Options();
        options.addOption("f", "file", true, "File to open");
        options.addOption("d", "dir", true, "Working directory");
        options.addOption("c", "config", true, "Config file name");
        options.addOption("?", "help", false, "Prints this help");
        return options;
    }

    private void printHelpAndExit() {
        new HelpFormatter().printHelp(Main.INSTANCE.getJByteMod().getTitle(), buildCommandLineOptions());
        System.exit(0);
    }

    private void loadFileIfNeeded(CommandLine cmd, JByteMod frame) {
        if (cmd.hasOption("f")) {
            File input = new File(cmd.getOptionValue("f"));
            if (FileUtils.exists(input) && FileUtils.isType(input, ".jar", ".class")) {
                frame.loadFile(input);
                Main.INSTANCE.getLogger().log("Specified file loaded");
            } else {
                Main.INSTANCE.getLogger().err("Specified file not found");
            }
        }
    }
}
