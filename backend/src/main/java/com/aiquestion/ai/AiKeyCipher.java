package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import org.springframework.stereotype.Component;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;

@Component
class AiKeyCipher {
    // Stored format: 12-byte nonce followed by ciphertext and the GCM authentication tag.
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String encryptionKey;
    private final SecureRandom random = new SecureRandom();

    AiKeyCipher(@Value("${AI_SETTINGS_ENCRYPTION_KEY:}") String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    private SecretKeySpec key() {
        byte[] bytes = Base64.getDecoder().decode(encryptionKey);
        if (bytes.length != 32) throw new IllegalArgumentException();
        return new SecretKeySpec(bytes, "AES");
    }

    String decrypt(long owner, byte[] sealed) {
        try {
            if (sealed == null || sealed.length < NONCE_BYTES + TAG_BITS / Byte.SIZE) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES));
            cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(owner).array());
            return new String(cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法读取 AI 密钥，请检查服务端加密配置");
        }
    }

    byte[] encrypt(long owner, String value) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(owner).array());
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "请管理员检查服务端 AI 配置加密密钥");
        }
    }
}
