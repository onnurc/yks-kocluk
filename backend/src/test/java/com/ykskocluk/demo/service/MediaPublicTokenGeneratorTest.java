package com.ykskocluk.demo.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPublicTokenGeneratorTest {
    @Test
    void generatesOpaque256BitTokens() {
        MediaPublicTokenGenerator generator = new MediaPublicTokenGenerator();
        var tokens = IntStream.range(0, 100).mapToObj(ignored -> generator.generate()).toList();

        assertThat(tokens).allMatch(token -> token.matches("[0-9a-f]{64}"));
        assertThat(new HashSet<>(tokens)).hasSize(100);
    }
}
