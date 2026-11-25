package com.zc.agent.core.modules.runtime.protocol.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeEventTypeContractTest {

    @Test
    void shouldKeepM1EventCodesStableAndUnique() {
        Set<String> expectedCodes = Set.of(
                "message.reset", "message.delta", "run.progress",
                "run.queued", "run.started", "run.mode.selected", "run.loop.stopped", "run.waiting", "run.completed", "run.failed", "run.cancelled",
                "step.started", "step.completed", "step.failed", "step.waiting",
                "tool.call.requested", "tool.call.started", "tool.call.completed", "tool.call.failed",
                "policy.decided",
                "approval.requested", "approval.resolved", "approval.revoked",
                "artifact.created", "artifact.published",
                "input.attachment.resolved", "input.attachment.analyzed",
                "checkpoint.created", "checkpoint.restored",
                "plan.created", "plan.replanned"
        );

        Set<String> actualCodes = Stream.of(RuntimeEventType.values())
                .map(RuntimeEventType::code)
                .collect(Collectors.toSet());

        assertThat(actualCodes).containsExactlyInAnyOrderElementsOf(expectedCodes);
        assertThat(actualCodes).hasSameSizeAs(RuntimeEventType.values());
        assertThat(actualCodes).allMatch(code -> code.matches("^[a-z]+(?:\\.[a-z]+)+$"));
    }

    @Test
    void shouldKeepLegacyMessageProjectionCompatible() {
        assertThat(RuntimeEventType.fromStoredCode("reset")).isEqualTo(RuntimeEventType.MESSAGE_RESET);
        assertThat(RuntimeEventType.fromStoredCode("delta")).isEqualTo(RuntimeEventType.MESSAGE_DELTA);
        assertThat(RuntimeEventType.fromStoredCode("progress")).isEqualTo(RuntimeEventType.RUN_PROGRESS);
    }
}
