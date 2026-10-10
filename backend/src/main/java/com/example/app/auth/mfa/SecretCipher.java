package com.example.app.auth.mfa;

import com.example.app.config.AppProperties;
import com.example.app.shared.Hashing;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Criptografia em repouso (AES-256-GCM) dos segredos TOTP. A chave vem de MFA_ENCRYPTION_KEY, nunca do banco. */
@Component
public class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;

    public SecretCipher(AppProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.mfa().encryptionKey());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.mfa.encryption-key deve estar em base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("app.mfa.encryption-key deve ter 32 bytes (AES-256). Gere com: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(byte[] plain) {
        try {
            byte[] iv = Hashing.randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar", e);
        }
    }

    public byte[] decrypt(String encoded) {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao descriptografar (chave errada ou dado corrompido)", e);
        }
    }
}
