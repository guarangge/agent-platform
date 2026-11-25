package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LlmInteractiveLoopActionDeciderTest {

    private final LlmInteractiveLoopActionDecider decider = new LlmInteractiveLoopActionDecider(
            mock(RuntimeLlmGatewayService.class), mock(CostGovernanceService.class), new ObjectMapper());

    @Test
    void shouldParseOnlyFrozenStructuredActions() {
        InteractiveLoopAction tool = decider.parseAction(
                "{\"action\":\"tool_call\",\"toolCode\":\"SEARCH\",\"arguments\":{\"keyword\":\"agent\"}}");
        InteractiveLoopAction answer = decider.parseAction(
                "{\"action\":\"final_answer\",\"content\":\"已完成\"}");

        assertThat(tool.type()).isEqualTo(InteractiveLoopAction.Type.TOOL_CALL);
        assertThat(tool.toolCode()).isEqualTo("SEARCH");
        assertThat(tool.arguments()).containsEntry("keyword", "agent");
        assertThat(answer.type()).isEqualTo(InteractiveLoopAction.Type.FINAL_ANSWER);
        assertThat(answer.content()).isEqualTo("已完成");
    }

    @Test
    void shouldRejectFreeTextAndInvalidPayload() {
        assertThatThrownBy(() -> decider.parseAction("请调用搜索工具"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decider.parseAction(
                "{\"action\":\"tool_call\",\"toolCode\":\"SEARCH\",\"arguments\":\"keyword\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arguments object");
        assertThatThrownBy(() -> decider.parseAction(
                "{\"action\":\"reasoning\",\"content\":\"hidden\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }
}
