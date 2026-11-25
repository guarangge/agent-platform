package com.zc.agent.core.modules.knowledge.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Knowledge 降级向量稳定性测试。
 */
class KnowledgeEmbeddingServiceTest {

    private final KnowledgeEmbeddingService service = new KnowledgeEmbeddingService();

    @Test
    void shouldGenerateStableNormalizedVector() {
        float[] first = service.embed("PostgreSQL 向量检索与知识引用");
        float[] second = service.embed("PostgreSQL 向量检索与知识引用");

        assertThat(first).hasSize(384).containsExactly(second);
        double norm = 0;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(norm).isBetween(0.999D, 1.001D);
        assertThat(service.vectorLiteral(first)).startsWith("[").endsWith("]");
    }
}
