package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayEngineTest {

    @Test
    void shouldRouteSimulationReplayToRegisteredHandler() {
        ReplayEngine engine = new ReplayEngine(List.of(new SimulationReplayModeHandler()));
        RuntimeEventEnvelope event = new RuntimeEventEnvelope(
                "EVT_1", 1L, 10L, null, "RUN_1", 20L, null, "message.delta",
                LocalDateTime.now(), 1, Map.of("content", "ok"), Map.of()
        );

        var result = engine.execute(new ReplayExecutionInput(
                ReplayMode.SIMULATION_REPLAY, "RUN_1", 1L, 1L, List.of(event), List.of()
        ));

        assertThat(result.output()).containsEntry("assembledMessage", "ok");
    }

    @Test
    void shouldRejectExecutionReplayThatIsNotEnabled() {
        ReplayEngine engine = new ReplayEngine(List.of(new SimulationReplayModeHandler()));

        assertThatThrownBy(() -> engine.execute(new ReplayExecutionInput(
                ReplayMode.EXECUTION_REPLAY, "RUN_1", 1L, 1L, List.of(), List.of()
        ))).hasMessage("Replay Mode 尚未开放");
    }
}
