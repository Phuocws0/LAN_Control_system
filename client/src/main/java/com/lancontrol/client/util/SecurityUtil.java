package com.lancontrol.client.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class SecurityUtil {
    private static final String AES_ALGO = "AES";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
// 32 chars = 256 bits
    public static String encrypt(String data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), AES_ALGO);
        Cipher c = Cipher.getInstance(AES_ALGO);
        c.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(c.doFinal(data.getBytes()));
    }

    public static String decrypt(String encryptedData) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), AES_ALGO);
        Cipher c = Cipher.getInstance(AES_ALGO);
        c.init(Cipher.DECRYPT_MODE, key);
        return new String(c.doFinal(Base64.getDecoder().decode(encryptedData)));
    }

    public static String generateHMAC(String data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), HMAC_ALGO);
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(key);
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes()));
    }
}