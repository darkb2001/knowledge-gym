package com.knowledgegym.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HashUtilsTest {

    @Test
    void sha256_knownValue() {
        // SHA-256("abc") chuẩn RFC
        assertThat(HashUtils.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256_isDeterministic() {
        assertThat(HashUtils.sha256Hex("same")).isEqualTo(HashUtils.sha256Hex("same"));
    }

    @Test
    void sha256_differsForDifferentInput() {
        assertThat(HashUtils.sha256Hex("a")).isNotEqualTo(HashUtils.sha256Hex("b"));
    }

    @Test
    void sha256_hexLength64() {
        assertThat(HashUtils.sha256Hex("anything")).hasSize(64);
    }
}