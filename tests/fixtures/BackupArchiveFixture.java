import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

class BackupArchiveFixture {
    private static final byte[] MAGIC = "DIKCIZB1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static void main(String[] args) throws Exception {
        byte[] input = Files.readAllBytes(Path.of(args[1]));
        char[] password = System.getenv("DIKCIZ_BACKUP_FIXTURE_PASSPHRASE").toCharArray();
        boolean encrypt = args[0].equals("encrypt");
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        if (encrypt) {
            new SecureRandom().nextBytes(salt);
            new SecureRandom().nextBytes(iv);
        } else {
            if (!Arrays.equals(MAGIC, Arrays.copyOfRange(input, 0, 8))) throw new IllegalArgumentException("Invalid header");
            salt = Arrays.copyOfRange(input, 8, 24);
            iv = Arrays.copyOfRange(input, 24, 36);
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, 600000, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        Arrays.fill(password, '\0');
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(MAGIC);
        byte[] content = encrypt ? cipher.doFinal(input) : cipher.doFinal(input, 36, input.length - 36);
        if (encrypt) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            output.write(MAGIC); output.write(salt); output.write(iv); output.write(content);
            content = output.toByteArray();
        }
        Files.write(Path.of(args[2]), content);
        Arrays.fill(key, (byte) 0);
    }
}
