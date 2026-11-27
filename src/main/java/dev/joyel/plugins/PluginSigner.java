package dev.joyel.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PluginSigner {
    private PluginSigner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        switch (args[0]) {
            case "keygen" -> keygen(args);
            case "sign" -> sign(args);
            case "verify" -> verify(args);
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void usage() {
        System.err.println("""
                Usage:
                  keygen <basename>
                      Creates <basename>.private and <basename>.public (Ed25519) and prints the publisher fingerprint.
                  sign <plugin.jar> <basename> <plugin-id> <version>
                      Prints the sha256, publisherKey and signature fields for a plugins.json entry.
                  verify <plugin.jar> <public-key-or-file> <signature> <plugin-id> <version>
                      Checks a signature and exits with 0 when it is valid.
                Keep the .private file secret. Never publish it.""");
    }

    private static void keygen(String[] args) throws Exception {
        if (args.length != 2) {
            usage();
            System.exit(2);
        }
        Path privateFile = Path.of(args[1] + ".private");
        Path publicFile = Path.of(args[1] + ".public");
        if (Files.exists(privateFile) || Files.exists(publicFile)) {
            System.err.println("Refusing to overwrite an existing key file.");
            System.exit(1);
        }
        KeyPair pair = PluginSignatures.generate();
        String publicKey = PluginSignatures.encodePublic(pair.getPublic());
        Files.writeString(privateFile, PluginSignatures.encodePrivate(pair.getPrivate()) + "\n");
        Files.writeString(publicFile, publicKey + "\n");
        try {
            Files.setPosixFilePermissions(privateFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
        System.out.println("Private key: " + privateFile + "  (keep it secret)");
        System.out.println("Public key:  " + publicFile);
        System.out.println("Publisher fingerprint: " + PluginSignatures.fingerprint(publicKey));
    }

    private static void sign(String[] args) throws Exception {
        if (args.length != 5) {
            usage();
            System.exit(2);
        }
        Path jar = Path.of(args[1]);
        String privateKey = Files.readString(Path.of(args[2] + ".private")).trim();
        String publicKey = Files.readString(Path.of(args[2] + ".public")).trim();
        String id = args[3];
        String version = args[4];
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        String signature = PluginSignatures.sign(PluginSignatures.decodePrivate(privateKey), id, version, sha256);
        System.out.println("\"sha256\": \"" + sha256 + "\",");
        System.out.println("\"publisherKey\": \"" + publicKey + "\",");
        System.out.println("\"signature\": \"" + signature + "\"");
        System.err.println("Publisher fingerprint: " + PluginSignatures.fingerprint(publicKey));
    }

    private static void verify(String[] args) throws Exception {
        if (args.length != 6) {
            usage();
            System.exit(2);
        }
        Path jar = Path.of(args[1]);
        String keyArgument = args[2];
        String publicKey = Files.isRegularFile(Path.of(keyArgument)) ? Files.readString(Path.of(keyArgument)).trim() : keyArgument;
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        boolean valid = PluginSignatures.verify(publicKey, args[3], args[4], args[5], sha256);
        System.out.println(valid ? "VALID  (publisher " + PluginSignatures.fingerprint(publicKey) + ")" : "INVALID");
        System.exit(valid ? 0 : 1);
    }
}
