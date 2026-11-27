package dev.joyel.plugins;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class PluginSignatures {
    private static final String ALGORITHM = "Ed25519";
    private static final int MAX_KEY_BYTES = 256;
    private static final int MAX_SIGNATURE_BYTES = 256;

    private PluginSignatures() {
    }

    public static byte[] message(String id, String version, String sha256Hex) {
        String text = "JByteMod-Plugin-v1\n" + id + "\n" + version + "\n" + sha256Hex.toLowerCase(Locale.ROOT);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static KeyPair generate() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
    }

    public static String encodePublic(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encodePrivate(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PrivateKey decodePrivate(String base64) throws GeneralSecurityException {
        byte[] bytes = decode(base64, MAX_KEY_BYTES);
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static PublicKey decodePublic(String base64) throws GeneralSecurityException {
        byte[] bytes = decode(base64, MAX_KEY_BYTES);
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(bytes));
    }

    public static String sign(PrivateKey key, String id, String version, String sha256Hex) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initSign(key);
        signature.update(message(id, version, sha256Hex));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public static boolean verify(String publicKeyBase64, String signatureBase64, String id, String version, String sha256Hex) {
        try {
            PublicKey key = decodePublic(publicKeyBase64);
            byte[] signatureBytes = decode(signatureBase64, MAX_SIGNATURE_BYTES);
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(key);
            signature.update(message(id, version, sha256Hex));
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            return false;
        }
    }

    public static boolean isWellFormedKey(String publicKeyBase64) {
        try {
            decodePublic(publicKeyBase64);
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            return false;
        }
    }

    public static String fingerprint(String publicKeyBase64) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(decode(publicKeyBase64, MAX_KEY_BYTES));
            String hex = HexFormat.of().withUpperCase().formatHex(digest, 0, 16);
            StringBuilder out = new StringBuilder();
            for (int index = 0; index < hex.length(); index += 4) {
                if (index > 0) {
                    out.append('-');
                }
                out.append(hex, index, index + 4);
            }
            return out.toString();
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            return null;
        }
    }

    private static byte[] decode(String base64, int limit) {
        if (base64 == null || base64.isBlank() || base64.length() > limit * 2) {
            throw new IllegalArgumentException("Invalid base64 value");
        }
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        if (bytes.length > limit) {
            throw new IllegalArgumentException("Value too large");
        }
        return bytes;
    }
}
