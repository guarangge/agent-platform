package com.zc.agent.registry.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

    @Test
    void shouldAcceptCanonicalConditionGraph() throws Exception {
        var report = validator.validate(mapper.readTree("""
                {"nodes":[
                  {"id":"start_1","type":"start"},{"id":"condition_1","type":"condition"},
                  {"id":"reply_yes","type":"reply"},{"id":"reply_no","type":"reply"},{"id":"end_1","type":"end"}
                ],"edges":[
                  {"source":"start_1","target":"condition_1"},
                  {"source":"condition_1","target":"reply_yes","branch":"true"},
                  {"source":"condition_1","target":"reply_no","branch":"false"},
                  {"source":"reply_yes","target":"end_1"},{"source":"reply_no","target":"end_1"}
                ]}
                """));
        assertThat(report.valid()).isTrue();
    }

    @Test
    void shouldRejectLegacyFieldsCycleAndMissingConditionBranch() throws Exception {
        var report = validator.validate(mapper.readTree("""
                {"nodes":[{"nodeId":"start","nodeType":"start"},{"id":"c","type":"condition"},{"id":"end","type":"end"}],
                 "edges":[{"source":"c","target":"c","branch":"true"},{"source":"c","target":"end","branch":"true"}]}
                """));
        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).extracting(WorkflowDefinitionValidator.ValidationIssue::code)
                .contains("node.id.required", "start.count", "condition.branches", "graph.cycle");
    }
}
