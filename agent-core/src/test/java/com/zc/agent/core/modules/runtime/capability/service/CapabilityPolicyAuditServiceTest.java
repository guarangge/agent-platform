package com.zc.agent.core.modules.runtime.capability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.capability.dto.CapabilityPolicyAuditQueryRequest;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditBucketRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditMetricsRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditScope;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyCorrelatedEventRecord;
import com.zc.agent.core.modules.runtime.capability.repository.CapabilityPolicyAuditRepository;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityPolicyAuditServiceTest {

    private CapabilityPolicyAuditRepository repository;
    private CapabilityPolicyAuditService service;
    private SecurityUser user;

    @BeforeEach
    void setUp() {
        repository = mock(CapabilityPolicyAuditRepository.class);
        service = new CapabilityPolicyAuditService(repository, new ObjectMapper());
        user = new SecurityUser(7L, "user", "User", List.of("USER"), List.of("tracing"));
    }

    @Test
    void shouldPageNormalizedPolicyDecisionsWithinCurrentUser() {
        when(repository.count(any(CapabilityPolicyAuditScope.class), any())).thenReturn(1L);
        when(repository.page(any(CapabilityPolicyAuditScope.class), any(), eq(20), eq(20))).thenReturn(List.of(record()));

        var page = service.page(request(2, 20, " approval ", " filesystem.write "), user);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).singleElement().satisfies(item -> {
            assertThat(item.toolCallId()).isEqualTo("TOOL_CALL_1234567890ABCDEF1234");
            assertThat(item.runtimeCapabilities()).containsExactly("filesystem.write");
            assertThat(item.approvalGranted()).isTrue();
        });
        verify(repository).page(eq(scope()), any(), eq(20), eq(20));
    }

    @Test
    void shouldSummarizeDecisionCapabilityAndToolBuckets() {
        CapabilityPolicyAuditMetricsRecord summary = new CapabilityPolicyAuditMetricsRecord(
                5L, 1L, 1L, 2L, 1L, 1L, 3L, 2L);
        when(repository.metrics(eq(scope()), any())).thenReturn(summary);
        when(repository.decisionBuckets(eq(scope()), any()))
                .thenReturn(List.of(new CapabilityPolicyAuditBucketRecord("approval", "approval", 2L)));
        when(repository.capabilityBuckets(eq(scope()), any()))
                .thenReturn(List.of(new CapabilityPolicyAuditBucketRecord("filesystem.write", "filesystem.write", 3L)));
        when(repository.toolBuckets(eq(scope()), any()))
                .thenReturn(List.of(new CapabilityPolicyAuditBucketRecord("FILE_WRITE", "文件写入", 3L)));

        var metrics = service.metrics(request(1, 20, null, null), user);

        assertThat(metrics.totalCount()).isEqualTo(5L);
        assertThat(metrics.approvalCount()).isEqualTo(2L);
        assertThat(metrics.capabilityBuckets()).singleElement()
                .extracting(item -> item.key()).isEqualTo("filesystem.write");
    }

    @Test
    void shouldReturnCorrelatedSafeRuntimeEvents() {
        when(repository.findByEventId(scope(), 12L)).thenReturn(Optional.of(record()));
        when(repository.findCorrelatedEvents(scope(), "RUN_1", "TOOL_CALL_1234567890ABCDEF1234"))
                .thenReturn(List.of(new CapabilityPolicyCorrelatedEventRecord(
                        12L, 8L, "policy.decided", "{\"decision\":\"approval\"}",
                        "{\"traceId\":\"TRACE_1\"}", 1, LocalDateTime.now())));

        var detail = service.detail(12L, user);

        assertThat(detail.decision().eventId()).isEqualTo(12L);
        assertThat(detail.correlatedEvents()).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("EVT_12");
            assertThat(event.payload()).containsEntry("decision", "approval");
            assertThat(event.metadata()).containsEntry("traceId", "TRACE_1");
        });
    }

    @Test
    void shouldRejectUnknownFiltersAndInvalidTimeRange() {
        assertThatThrownBy(() -> service.page(request(1, 20, "redact", null), user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("决策动作无效");
        assertThatThrownBy(() -> service.page(request(1, 20, null, "unknown.capability"), user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Runtime Capability");
        CapabilityPolicyAuditQueryRequest invalidTime = new CapabilityPolicyAuditQueryRequest(
                1, 20, null, null, null, null, null, null, null, null,
                LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 1, 10, 0));
        assertThatThrownBy(() -> service.metrics(invalidTime, user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始时间不能晚于结束时间");
    }

    private CapabilityPolicyAuditQueryRequest request(Integer pageNum,
                                                        Integer pageSize,
                                                        String decision,
                                                        String capability) {
        return new CapabilityPolicyAuditQueryRequest(
                pageNum, pageSize, decision, " FILE ", capability, " irreversible ",
                " policy ", 9L, " RUN_1 ", " TRACE_1 ", null, null);
    }

    private CapabilityPolicyAuditRecord record() {
        return new CapabilityPolicyAuditRecord(
                12L, 8L, "RUN_1", 3L, "CONV_1", 4L, "TASK_1", 9L, "Agent",
                "TRACE_1", "TOOL_CALL_1234567890ABCDEF1234", "FILE_WRITE", "文件写入",
                "approval", "approval", "POLICY_1", 2, "[\"filesystem.write\"]", "irreversible",
                "a".repeat(64), "b".repeat(64), "capability:node", true, LocalDateTime.now());
    }

    private CapabilityPolicyAuditScope scope() {
        return new CapabilityPolicyAuditScope(7L, 1L, 1L, 1L, 1L);
    }
}
