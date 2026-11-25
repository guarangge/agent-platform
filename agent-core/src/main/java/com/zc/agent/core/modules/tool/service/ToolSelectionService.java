package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.eval.service.EvalFeedbackService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.repository.ToolHealthCheckRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 运行时 Tool 选择服务。
 */
@Service
public class ToolSelectionService {

    private final ToolHealthCheckRepository toolHealthCheckRepository;
    private final EvalFeedbackService evalFeedbackService;

    public ToolSelectionService(ToolHealthCheckRepository toolHealthCheckRepository,
                                EvalFeedbackService evalFeedbackService) {
        this.toolHealthCheckRepository = toolHealthCheckRepository;
        this.evalFeedbackService = evalFeedbackService;
    }

    public List<RegistryAgentTool> selectCandidates(ExecutionContext executionContext,
                                                    String targetToolCode) {
        EvalFeedbackService.EvalFeedbackSummary evalFeedback = evalFeedbackService.summarizeAgentFeedback(executionContext.agentContext().agentId());
        List<RegistryAgentTool> exactMatches = executionContext.availableTools().stream()
                .filter(item -> targetToolCode.equals(item.toolCode()))
                .sorted(candidateComparator(evalFeedback))
                .toList();
        if (!exactMatches.isEmpty()) {
            RegistryAgentTool anchor = exactMatches.get(0);
            List<RegistryAgentTool> candidates = new ArrayList<>(exactMatches);
            List<RegistryAgentTool> fallbacks = new ArrayList<>();
            for (RegistryAgentTool tool : executionContext.availableTools()) {
                if (containsTool(candidates, tool)) {
                    continue;
                }
                if (sameCapability(anchor, tool)) {
                    fallbacks.add(tool);
                }
            }
            fallbacks.sort(candidateComparator(evalFeedback));
            candidates.addAll(fallbacks);
            return candidates;
        }
        return List.of();
    }

    private Comparator<RegistryAgentTool> candidateComparator(EvalFeedbackService.EvalFeedbackSummary evalFeedback) {
        return Comparator.<RegistryAgentTool>comparingInt(tool -> evalScore(tool, evalFeedback)).reversed()
                .thenComparing(Comparator.comparingInt(this::statusScore).reversed())
                .thenComparingInt(this::healthScore).reversed()
                .thenComparingInt(this::timeoutScore).reversed();
    }

    private int evalScore(RegistryAgentTool tool, EvalFeedbackService.EvalFeedbackSummary summary) {
        if (summary == null) {
            return 0;
        }
        return summary.preferredToolCodes().contains(tool.toolCode()) ? 100 : 0;
    }

    private int statusScore(RegistryAgentTool tool) {
        return "enabled".equalsIgnoreCase(tool.status()) ? 100 : 0;
    }

    private int healthScore(RegistryAgentTool tool) {
        if (tool.toolId() == null) {
            return 0;
        }
        return toolHealthCheckRepository.findLatestByToolId(tool.toolId())
                .map(item -> Boolean.TRUE.equals(item.success()) ? 100 : -50)
                .orElse(0);
    }

    private int timeoutScore(RegistryAgentTool tool) {
        int timeoutSeconds = tool.timeoutSeconds() == null ? 30 : tool.timeoutSeconds();
        return Math.max(0, 120 - timeoutSeconds);
    }

    private boolean sameCapability(RegistryAgentTool anchor, RegistryAgentTool candidate) {
        boolean sameOperation = equalsIgnoreCase(anchor.operationName(), candidate.operationName());
        boolean sameFallbackGroup = equalsIgnoreCase(metadataText(anchor, "fallbackGroup"),
                metadataText(candidate, "fallbackGroup"));
        return (sameOperation || sameFallbackGroup) && acceptsAnchorArguments(anchor, candidate);
    }

    private boolean acceptsAnchorArguments(RegistryAgentTool anchor, RegistryAgentTool candidate) {
        Set<String> anchorProperties = propertyNames(anchor);
        Set<String> candidateRequired = requiredNames(candidate);
        return anchorProperties.containsAll(candidateRequired);
    }

    private Set<String> propertyNames(RegistryAgentTool tool) {
        if (tool.inputSchema() == null || !tool.inputSchema().path("properties").isObject()) {
            return Set.of();
        }
        Set<String> properties = new HashSet<>();
        tool.inputSchema().path("properties").fieldNames().forEachRemaining(properties::add);
        return properties;
    }

    private Set<String> requiredNames(RegistryAgentTool tool) {
        if (tool.inputSchema() == null || !tool.inputSchema().path("required").isArray()) {
            return Set.of();
        }
        Set<String> required = new HashSet<>();
        tool.inputSchema().path("required").forEach(item -> {
            String field = item.asText("");
            if (!field.isBlank()) {
                required.add(field);
            }
        });
        return required;
    }

    private String metadataText(RegistryAgentTool tool, String field) {
        return tool.metadata() == null ? "" : tool.metadata().path(field).asText("");
    }

    private boolean containsTool(List<RegistryAgentTool> candidates, RegistryAgentTool tool) {
        return candidates.stream().anyMatch(item -> item.toolId() != null && item.toolId().equals(tool.toolId()));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim().toLowerCase(Locale.ROOT);
        String normalizedRight = right == null ? "" : right.trim().toLowerCase(Locale.ROOT);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }
}
