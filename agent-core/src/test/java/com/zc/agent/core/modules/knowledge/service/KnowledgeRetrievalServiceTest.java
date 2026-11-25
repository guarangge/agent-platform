package com.zc.agent.core.modules.knowledge.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.knowledge.client.RegistryKnowledgeClient;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import com.zc.agent.core.modules.knowledge.repository.KnowledgeRuntimeRepository;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Knowledge 权限过滤、引用和审计闭环测试。
 */
class KnowledgeRetrievalServiceTest {

    @Test
    void shouldSearchAuthorizedKnowledgeAndWriteAuditLog() {
        RegistryKnowledgeClient client = mock(RegistryKnowledgeClient.class);
        KnowledgeRuntimeRepository repository = mock(KnowledgeRuntimeRepository.class);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(client, repository, new KnowledgeEmbeddingService());
        SecurityUser user = new SecurityUser(7L, "tester", "测试用户", List.of("USER"), List.of("knowledge"));
        KnowledgeModels.RegistryKnowledge knowledge = new KnowledgeModels.RegistryKnowledge(10L, "KB", "平台知识",
                null, 800, 120, 5, 0.2D, null, LocalDateTime.now(), List.of());
        when(client.getBundle(3L, List.of("USER"))).thenReturn(new KnowledgeModels.RegistryBundle(3L, List.of(knowledge)));
        KnowledgeModels.Citation citation = new KnowledgeModels.Citation(10L, "KB", 20L, "DOC", "架构说明",
                1, "https://example.test/doc", "双服务边界", 0.5D, 0.8D, 0.7D);
        when(repository.search(anyList(), any(), anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(citation));

        KnowledgeModels.SearchResponse response = service.search(
                new KnowledgeModels.SearchRequest(3L, 10L, "服务边界是什么", 3, 1L, 2L, "TRACE_1"), user);

        assertThat(response.hitCount()).isEqualTo(1);
        assertThat(response.promptContext()).contains("[K1]", "架构说明", "双服务边界");
        verify(repository).logRetrieval(anyString(), anyLong(), any(), anyList(), anyInt(), anyLong(), anyList());
    }

    @Test
    void shouldRejectKnowledgeOutsideAgentAndRoleScope() {
        RegistryKnowledgeClient client = mock(RegistryKnowledgeClient.class);
        KnowledgeRuntimeRepository repository = mock(KnowledgeRuntimeRepository.class);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(client, repository, new KnowledgeEmbeddingService());
        SecurityUser user = new SecurityUser(7L, "tester", "测试用户", List.of("USER"), List.of("knowledge"));
        when(client.getBundle(3L, List.of("USER"))).thenReturn(new KnowledgeModels.RegistryBundle(3L, List.of()));

        assertThatThrownBy(() -> service.search(
                new KnowledgeModels.SearchRequest(3L, 99L, "越权检索", 3, null, null, null), user))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无权访问");
    }
}
