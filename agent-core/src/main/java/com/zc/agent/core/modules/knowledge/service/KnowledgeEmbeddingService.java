package com.zc.agent.core.modules.knowledge.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Knowledge 向量服务。
 *
 * 当前使用稳定的特征哈希生成 384 维向量作为无外部 Embedding 模型时的降级实现，
 * 其存储和检索接口保持 pgvector 标准，后续可直接替换为注册模型调用。
 */
@Service
public class KnowledgeEmbeddingService {

    private static final int DIMENSION = 384;

    /** 将文本转换为归一化向量。 */
    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return vector;
        }
        for (int index = 0; index < normalized.length(); index++) {
            int end = Math.min(normalized.length(), index + 3);
            byte[] bytes = normalized.substring(index, end).getBytes(StandardCharsets.UTF_8);
            int hash = 1;
            for (byte value : bytes) {
                hash = 31 * hash + value;
            }
            int bucket = Math.floorMod(hash, DIMENSION);
            vector[bucket] += (hash & 1) == 0 ? 1F : -1F;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            float scale = (float) (1D / Math.sqrt(norm));
            for (int index = 0; index < vector.length; index++) {
                vector[index] *= scale;
            }
        }
        return vector;
    }

    /** 转换为 PostgreSQL pgvector 字面量。 */
    public String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }
}
