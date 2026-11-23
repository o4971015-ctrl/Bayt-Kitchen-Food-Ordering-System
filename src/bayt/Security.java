package bayt;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Security {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    static String token() { return ENCODER.encodeToString(random(24)); }
    private static byte[] random(int size) { byte[] bytes=new byte[size]; RANDOM.nextBytes(bytes); return bytes; }
    private static byte[] derive(String password,byte[] salt) throws GeneralSecurityException {
        var spec=new PBEKeySpec(password.toCharArray(),salt,310000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    static String hash(String password) throws GeneralSecurityException {
        byte[] salt=random(16);
        return ENCODER.encodeToString(salt)+"."+ENCODER.encodeToString(derive(password,salt));
    }
    static boolean matches(String password,String stored) throws GeneralSecurityException {
        if(password.length()>256)return false;
        String[] parts=stored.split("\\.");
        var decoder=Base64.getUrlDecoder();
        return MessageDigest.isEqual(derive(password,decoder.decode(parts[0])),decoder.decode(parts[1]));
    }
    static boolean equal(String a,String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    }
}
