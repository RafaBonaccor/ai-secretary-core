package com.assistantcore.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleOAuthTokenCipher {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecureRandom secureRandom = new SecureRandom();
  private final String encryptionSecret;

  public GoogleOAuthTokenCipher(
    @Value("${app.google.oauth.token-encryption-secret:}") String encryptionSecret
  ) {
    this.encryptionSecret = encryptionSecret;
  }

  public void ensureConfigured() {
    if (encryptionSecret == null || encryptionSecret.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Google OAuth token encryption is not configured. Set GOOGLE_OAUTH_TOKEN_ENCRYPTION_SECRET."
      );
    }
  }

  public String encrypt(String plainText) {
    if (plainText == null || plainText.isBlank()) {
      return null;
    }

    ensureConfigured();

    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      ByteBuffer payload = ByteBuffer.allocate(iv.length + cipherText.length);
      payload.put(iv);
      payload.put(cipherText);

      return Base64.getEncoder().encodeToString(payload.array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to encrypt Google OAuth token", exception);
    }
  }

  public String decrypt(String encryptedText) {
    if (encryptedText == null || encryptedText.isBlank()) {
      return null;
    }

    ensureConfigured();

    try {
      byte[] payload = Base64.getDecoder().decode(encryptedText);
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      byte[] iv = new byte[IV_LENGTH_BYTES];
      buffer.get(iv);
      byte[] cipherText = new byte[buffer.remaining()];
      buffer.get(cipherText);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to decrypt Google OAuth token", exception);
    }
  }

  private SecretKeySpec key() throws GeneralSecurityException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] keyBytes = digest.digest(encryptionSecret.getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(keyBytes, "AES");
  }
}
