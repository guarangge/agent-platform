package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

/**
 * 分数分析 Tool 执行器。
 */
@Component
public class ScoreAnalysisToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "SCORE_ANALYSIS";

    private final ScoreAnalysisTool scoreAnalysisTool;

    public ScoreAnalysisToolExecutor(ScoreAnalysisTool scoreAnalysisTool) {
        this.scoreAnalysisTool = scoreAnalysisTool;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText) {
        return scoreAnalysisTool.execute(executionContext, tool, toolInputText);
    }
}
