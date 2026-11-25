package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.config.ToolSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSecretCryptoServiceTest {

    private ToolSecretCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        ToolSecurityProperties properties = new ToolSecurityProperties();
        properties.setEncryptionKey("test-encryption-key-for-agent-platform");
        cryptoService = new ToolSecretCryptoService(new ObjectMapper(), properties);
    }

    @Test
    void shouldEncryptDecryptAndMaskModelSecret() {
        String encrypted = cryptoService.encryptSecret("sk-test-1234567890");

        assertThat(encrypted).startsWith("enc:v1:").doesNotContain("sk-test-1234567890");
        assertThat(cryptoService.decryptSecret(encrypted)).isEqualTo("sk-test-1234567890");
        assertThat(cryptoService.maskStoredSecret(encrypted)).isEqualTo("sk****90");
    }

    @Test
    void shouldKeepEncryptedSecretIdempotently() {
        String encrypted = cryptoService.encryptSecret("secret-value");

        assertThat(cryptoService.encryptSecret(encrypted)).isEqualTo(encrypted);
    }
}
