package com.health.ai;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
@Component
public class AiKeyCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public AiKeyCipher(@Value("${ai.key-encryption-key}") String encoded) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("AI_KEY_ENCRYPTION_KEY 必须为 Base64 编码的 32 字节密钥"); }
        if (bytes.length != 32) throw new IllegalStateException("AI_KEY_ENCRYPTION_KEY 必须为 Base64 编码的 32 字节密钥");
        key = new SecretKeySpec(bytes, "AES");
    }
    public String encrypt(Long userId, String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, userId, iv);
            return "v1." + Base64.getEncoder().encodeToString(iv) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("AI 密钥加密失败"); }
    }
    public String decrypt(Long userId, String value) {
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, userId, Base64.getDecoder().decode(parts[1]))
                    .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("AI 密钥解密失败，请重新配置"); }
    }
    private Cipher cipher(int mode, Long userId, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(("health-ai:" + userId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
