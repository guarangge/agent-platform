package com.zc.agent.registry.modules.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.knowledge.dto.KnowledgeContracts;
import com.zc.agent.registry.modules.knowledge.repository.KnowledgeRepository;
import com.zc.agent.registry.modules.model.service.ModelService;
import com.zc.agent.registry.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    @Test
    void shouldRejectUnavailableEmbeddingModelBeforeWritingKnowledge() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ModelService modelService = mock(ModelService.class);
        KnowledgeService service = new KnowledgeService(repository, new ObjectMapper(), modelService);
        when(modelService.existsEnabledEmbeddingModelCode("chat_model")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("chat_model"), user()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Embedding 模型不存在、未启用或模型类型不正确");

        verify(repository, never()).insertKnowledge(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private KnowledgeContracts.SaveKnowledgeRequest request(String embeddingModelCode) {
        return new KnowledgeContracts.SaveKnowledgeRequest(
                "knowledge_test",
                "测试知识库",
                null,
                embeddingModelCode,
                800,
                120,
                5,
                0.25,
                "draft",
                JsonNodeFactory.instance.objectNode(),
                null
        );
    }

    private SecurityUser user() {
        return new SecurityUser(1L, "tester", "测试用户", List.of("ADMIN"), List.of("knowledge"));
    }
}
