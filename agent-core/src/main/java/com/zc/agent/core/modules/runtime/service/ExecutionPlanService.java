package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 执行计划降级服务。
 * 当 LLM Planner 不可用或计划校验失败时，负责生成兼容现有执行器的最小计划。
 */
@Service
public class ExecutionPlanService {

    private static final String PLAN_STATUS_DRAFT = "draft";
    private static final String SANDBOX_EXECUTE_TOOL_CODE = "SANDBOX_EXECUTE";
    private static final String PDF_SOURCE_PATH = "content.txt";
    private static final String PDF_GENERATION_SCRIPT = """
            from pathlib import Path
            from html import escape
            import sys
            from reportlab.lib import colors
            from reportlab.lib.enums import TA_CENTER
            from reportlab.lib.pagesizes import A4
            from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
            from reportlab.lib.units import mm
            from reportlab.pdfbase import pdfmetrics
            from reportlab.pdfbase.ttfonts import TTFont
            from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

            source = Path("content.txt").read_text(encoding="utf-8")
            source = source.replace("⚠️", "注意：").replace("⭐", "")
            output_filename = sys.argv[1]

            def resolve_title(text):
                for line in text.splitlines():
                    stripped = line.strip()
                    if stripped.startswith("#"):
                        title = stripped.lstrip("#").strip().replace("**", "").replace("`", "")
                        if title and title not in {"最终回复", "当前进度汇报", "任务执行结果"}:
                            return title
                return "Agent 生成文档"

            document_title = resolve_title(source)
            font_path = Path("/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc")
            if not font_path.exists():
                raise RuntimeError(f"中文字体不存在：{font_path}")
            pdfmetrics.registerFont(TTFont("WenQuanYi", str(font_path), subfontIndex=0))

            document = SimpleDocTemplate(
                output_filename,
                pagesize=A4,
                rightMargin=18 * mm,
                leftMargin=18 * mm,
                topMargin=18 * mm,
                bottomMargin=16 * mm,
                title=document_title,
                author="通用 Agent 平台"
            )
            styles = getSampleStyleSheet()
            body = ParagraphStyle("CJKBody", parent=styles["BodyText"], fontName="WenQuanYi", fontSize=10, leading=16, wordWrap="CJK", spaceAfter=6)
            h1 = ParagraphStyle("CJKH1", parent=body, fontSize=20, leading=28, alignment=TA_CENTER, textColor=colors.HexColor("#203c39"), spaceAfter=16)
            h2 = ParagraphStyle("CJKH2", parent=body, fontSize=15, leading=22, textColor=colors.HexColor("#24443f"), spaceBefore=10, spaceAfter=8)
            h3 = ParagraphStyle("CJKH3", parent=body, fontSize=12, leading=18, textColor=colors.HexColor("#315b53"), spaceBefore=7, spaceAfter=5)
            quote = ParagraphStyle("CJKQuote", parent=body, leftIndent=10, rightIndent=10, textColor=colors.HexColor("#60726a"), backColor=colors.HexColor("#eef4f1"), borderPadding=7)
            table_text = ParagraphStyle("CJKTable", parent=body, fontSize=8.5, leading=12, spaceAfter=0)

            def clean(text):
                return escape(text.replace("**", "").replace("`", "").strip())

            def is_separator_row(line):
                cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
                return bool(cells) and all(cell and set(cell) <= {"-", ":"} for cell in cells)

            story = [Paragraph(clean(document_title), h1)]
            lines = source.splitlines()
            index = 0
            while index < len(lines):
                raw = lines[index].strip()
                if not raw:
                    story.append(Spacer(1, 3 * mm))
                    index += 1
                    continue
                if raw.startswith("|") and index + 1 < len(lines) and is_separator_row(lines[index + 1]):
                    rows = []
                    while index < len(lines) and lines[index].strip().startswith("|"):
                        line = lines[index].strip()
                        if not is_separator_row(line):
                            rows.append([Paragraph(clean(cell), table_text) for cell in line.strip("|").split("|")])
                        index += 1
                    column_count = max(len(row) for row in rows)
                    normalized_rows = [row + [Paragraph("", table_text)] * (column_count - len(row)) for row in rows]
                    table = Table(normalized_rows, colWidths=[document.width / column_count] * column_count, repeatRows=1)
                    table.setStyle(TableStyle([
                        ("FONTNAME", (0, 0), (-1, -1), "WenQuanYi"),
                        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#eaf3ef")),
                        ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#24443f")),
                        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#c8d9d1")),
                        ("VALIGN", (0, 0), (-1, -1), "TOP"),
                        ("LEFTPADDING", (0, 0), (-1, -1), 5),
                        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                        ("TOPPADDING", (0, 0), (-1, -1), 5),
                        ("BOTTOMPADDING", (0, 0), (-1, -1), 5)
                    ]))
                    story.extend([table, Spacer(1, 3 * mm)])
                    continue
                if raw.startswith("### "):
                    story.append(Paragraph(clean(raw[4:]), h3))
                elif raw.startswith("## "):
                    story.append(Paragraph(clean(raw[3:]), h2))
                elif raw.startswith("# "):
                    story.append(Paragraph(clean(raw[2:]), h1))
                elif raw.startswith("> "):
                    story.append(Paragraph(clean(raw[2:]), quote))
                elif raw.startswith("- "):
                    story.append(Paragraph(clean(raw[2:]), body, bulletText="•"))
                elif raw == "---":
                    story.append(Spacer(1, 2 * mm))
                else:
                    story.append(Paragraph(clean(raw), body))
                index += 1

            def draw_footer(canvas, doc):
                canvas.saveState()
                canvas.setFont("WenQuanYi", 8)
                canvas.setFillColor(colors.HexColor("#7b8983"))
                canvas.drawRightString(A4[0] - 18 * mm, 9 * mm, f"第 {doc.page} 页")
                canvas.restoreState()

            document.build(story, onFirstPage=draw_footer, onLaterPages=draw_footer)
            print(f"generated {output_filename}")
            """;

    private static final List<String> PDF_REFERENCE_TERMS = List.of(
            "路线", "计划", "清单", "方案", "报告", "总结", "分析", "表格", "代码", "简历"
    );

    private final ConversationRepository conversationRepository;

    public ExecutionPlanService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    /**
     * 根据上下文和决策生成执行计划。
     *
     * @param executionContext  运行时上下文
     * @param executionDecision 执行决策
     * @return 执行计划
     */
    public ExecutionPlan buildPlan(ExecutionContext executionContext, ExecutionDecision executionDecision) {
        List<ExecutionPlan.ExecutionPlanStep> steps = new ArrayList<>();
        int stepNo = 1;
        // Planner 不可用时只执行排序最高的候选，避免把路由候选集误当成串行执行清单。
        for (ExecutionDecision.SelectedTool selectedTool : executionDecision.selectedTools().stream().limit(1).toList()) {
            Map<String, Object> toolInputs = buildFallbackToolInputs(executionContext, selectedTool);
            steps.add(new ExecutionPlan.ExecutionPlanStep(
                    stepNo++,
                    buildStepId("tool", stepNo - 1),
                    "tool",
                    resolveToolName(executionContext, selectedTool),
                    buildToolObjective(selectedTool),
                    List.of(),
                    selectedTool.toolCode(),
                    null,
                    toolInputs,
                    "返回 " + resolveToolName(executionContext, selectedTool) + " 的执行结果",
                    "fail_fast",
                    Boolean.TRUE,
                    PLAN_STATUS_DRAFT,
                    executionDecision.reason()
            ));
        }
        for (ExecutionDecision.SelectedWorkflow selectedWorkflow : executionDecision.selectedWorkflows().stream().limit(1).toList()) {
            steps.add(new ExecutionPlan.ExecutionPlanStep(
                    stepNo++,
                    buildStepId("workflow", stepNo - 1),
                    "workflow",
                    resolveWorkflowName(executionContext, selectedWorkflow),
                    buildWorkflowObjective(selectedWorkflow),
                    List.of(),
                    null,
                    selectedWorkflow.workflowId(),
                    Map.of("workflowId", selectedWorkflow.workflowId()),
                    "返回 Workflow 执行结果",
                    "replan",
                    Boolean.TRUE,
                    PLAN_STATUS_DRAFT,
                    executionDecision.reason()
            ));
        }
        if (steps.isEmpty()) {
            steps.add(new ExecutionPlan.ExecutionPlanStep(
                    1,
                    buildStepId("reply", 1),
                    "reply",
                    "最小响应生成",
                    "基于当前上下文直接生成回复",
                    List.of(),
                    null,
                    null,
                    Map.of(),
                    "输出最终回复",
                    "replan",
                    Boolean.TRUE,
                    PLAN_STATUS_DRAFT,
                    executionDecision.reason()
            ));
        } else if (!isUserReplyWorkflowStep(steps.get(steps.size() - 1), executionContext)) {
            steps.add(new ExecutionPlan.ExecutionPlanStep(
                    stepNo,
                    buildStepId("reply", stepNo),
                    "reply",
                    "输出最终方案",
                    "基于前序步骤结果向用户输出最终回复",
                    List.of(steps.get(steps.size() - 1).stepId()),
                    null,
                    null,
                    Map.of(),
                    "输出最终回复",
                    "replan",
                    Boolean.TRUE,
                    PLAN_STATUS_DRAFT,
                    "收口前序 Tool / Workflow 结果，生成用户可消费的最终答复"
            ));
        }
        return new ExecutionPlan(
                "PLAN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                resolvePlanType(executionDecision),
                resolveGoal(executionContext),
                PLAN_STATUS_DRAFT,
                List.of("当前为规则降级生成计划"),
                List.of("步骤按顺序串行执行"),
                List.of("未经过 LLM 任务拆解，复杂任务可能拆分不足"),
                steps
        );
    }

    private boolean isUserReplyWorkflowStep(ExecutionPlan.ExecutionPlanStep step,
                                            ExecutionContext executionContext) {
        if (!"workflow".equals(step.stepType()) || step.targetWorkflowId() == null) return false;
        return executionContext.availableWorkflows().stream()
                .filter(workflow -> step.targetWorkflowId().equals(workflow.workflowId()))
                .map(RegistryAgentWorkflow::metadata)
                .filter(java.util.Objects::nonNull)
                .map(metadata -> metadata.path("routing").path("outputMode").asText(""))
                .anyMatch("user_reply"::equalsIgnoreCase);
    }

    private String resolveToolName(ExecutionContext executionContext, ExecutionDecision.SelectedTool selectedTool) {
        if (selectedTool.toolName() != null && !selectedTool.toolName().isBlank()) {
            return selectedTool.toolName();
        }
        return executionContext.availableTools().stream()
                .filter(item -> selectedTool.toolCode().equals(item.toolCode()))
                .map(item -> item.toolName())
                .findFirst()
                .orElse(selectedTool.toolCode());
    }

    private String resolveWorkflowName(ExecutionContext executionContext, ExecutionDecision.SelectedWorkflow selectedWorkflow) {
        if (selectedWorkflow.workflowName() != null && !selectedWorkflow.workflowName().isBlank()) {
            return selectedWorkflow.workflowName();
        }
        return executionContext.availableWorkflows().stream()
                .filter(item -> selectedWorkflow.workflowId().equals(item.workflowId()))
                .map(item -> item.workflowName())
                .findFirst()
                .orElse("Workflow执行");
    }

    private String resolvePlanType(ExecutionDecision executionDecision) {
        if (executionDecision.hasToolSelection() && executionDecision.hasWorkflowSelection()) {
            return "hybrid";
        }
        if (executionDecision.hasWorkflowSelection()) {
            return "workflow";
        }
        if (executionDecision.hasToolSelection()) {
            return executionDecision.selectedTools().size() > 1 ? "tool_sequence" : "tool";
        }
        if (executionDecision.isHybridDecision()) {
            return "hybrid";
        }
        if (executionDecision.isWorkflowDecision()) {
            return "workflow";
        }
        if (executionDecision.isToolDecision()) {
            return executionDecision.selectedTools().size() > 1 ? "tool_sequence" : "tool";
        }
        return "direct_reply";
    }

    private String resolveGoal(ExecutionContext executionContext) {
        return executionContext.userMessage();
    }

    private String buildStepId(String stepType, int stepNo) {
        return stepType + "_" + stepNo;
    }

    private String buildToolObjective(ExecutionDecision.SelectedTool selectedTool) {
        return "调用 Tool[" + selectedTool.toolCode() + "] 获取完成任务所需结果";
    }

    private String buildWorkflowObjective(ExecutionDecision.SelectedWorkflow selectedWorkflow) {
        return "执行 Workflow[" + selectedWorkflow.workflowId() + "] 推进任务";
    }

    private Map<String, Object> buildFallbackToolInputs(ExecutionContext executionContext,
                                                        ExecutionDecision.SelectedTool selectedTool) {
        if (!SANDBOX_EXECUTE_TOOL_CODE.equalsIgnoreCase(selectedTool.toolCode())
                || !isPdfGenerationRequest(executionContext.userMessage())) {
            return Map.of("toolCode", selectedTool.toolCode());
        }
        String sourceContent = resolvePdfSourceContent(executionContext);
        String artifactPath = ArtifactFilenameResolver.resolvePdfFilename(
                executionContext.userMessage(), sourceContent);
        return Map.of(
                "profileCode", "DEFAULT_ISOLATED",
                "command", List.of("python3", "-c", PDF_GENERATION_SCRIPT, artifactPath),
                "inputFiles", List.of(Map.of(
                        "path", PDF_SOURCE_PATH,
                        "contentBase64", Base64.getEncoder().encodeToString(sourceContent.getBytes(StandardCharsets.UTF_8))
                )),
                "artifactPaths", List.of(artifactPath)
        );
    }

    private boolean isPdfGenerationRequest(String userMessage) {
        return ArtifactFilenameResolver.isPdfGenerationRequest(userMessage);
    }

    private String resolvePdfSourceContent(ExecutionContext executionContext) {
        String inlineContent = extractInlinePdfContent(executionContext.userMessage());
        if (inlineContent != null) {
            return inlineContent;
        }
        String assistantContent = resolvePdfSourceFromFullHistory(executionContext);
        if (assistantContent == null) {
            assistantContent = selectReferencedAssistantContent(
                    executionContext.userMessage(),
                    executionContext.recentConversationMessages() == null
                            ? List.of()
                            : executionContext.recentConversationMessages().stream()
                            .filter(java.util.Objects::nonNull)
                            .filter(message -> "assistant".equalsIgnoreCase(message.messageType()))
                            .map(message -> new PdfSourceCandidate(message.sequenceNo(), message.content()))
                            .toList()
            );
        }
        if (assistantContent != null) {
            return assistantContent;
        }
        return "PDF 文件生成请求\n\n" + executionContext.userMessage();
    }

    private String resolvePdfSourceFromFullHistory(ExecutionContext executionContext) {
        if (executionContext.conversationContext() == null
                || executionContext.conversationContext().conversationId() == null) {
            return null;
        }
        List<MessageRecord> messages = conversationRepository.findMessagesByConversationId(
                executionContext.conversationContext().conversationId());
        Long currentMessageId = executionContext.messageContext() == null
                ? null : executionContext.messageContext().userMessageId();
        Integer currentSequenceNo = messages.stream()
                .filter(message -> currentMessageId != null && currentMessageId.equals(message.messageId()))
                .map(MessageRecord::sequenceNo)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
        List<PdfSourceCandidate> candidates = messages.stream()
                .filter(message -> message.sequenceNo() != null && message.sequenceNo() < currentSequenceNo)
                .filter(message -> "assistant".equalsIgnoreCase(message.messageType()))
                .filter(message -> "success".equalsIgnoreCase(message.responseStatus()))
                .map(message -> new PdfSourceCandidate(message.sequenceNo(), message.content()))
                .toList();
        return selectReferencedAssistantContent(executionContext.userMessage(), candidates);
    }

    private String selectReferencedAssistantContent(String userMessage, List<PdfSourceCandidate> candidates) {
        List<String> requestedTerms = PDF_REFERENCE_TERMS.stream()
                .filter(term -> userMessage != null && userMessage.contains(term))
                .toList();
        return candidates.stream()
                .filter(candidate -> isBusinessPdfSource(candidate.content()))
                .max(Comparator
                        .comparingInt((PdfSourceCandidate candidate) -> referenceScore(candidate.content(), requestedTerms))
                        .thenComparingInt(candidate -> candidate.sequenceNo() == null ? 0 : candidate.sequenceNo()))
                .filter(candidate -> requestedTerms.isEmpty() || referenceScore(candidate.content(), requestedTerms) > 0)
                .map(PdfSourceCandidate::content)
                .orElseGet(() -> candidates.stream()
                        .filter(candidate -> isBusinessPdfSource(candidate.content()))
                        .max(Comparator.comparingInt(candidate -> candidate.sequenceNo() == null ? 0 : candidate.sequenceNo()))
                        .map(PdfSourceCandidate::content)
                        .orElse(null));
    }

    private int referenceScore(String content, List<String> requestedTerms) {
        return (int) requestedTerms.stream().filter(content::contains).count();
    }

    private boolean isBusinessPdfSource(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return !normalized.contains("高风险tool人工审批")
                && !normalized.startsWith("pdf文件已生成")
                && !normalized.contains("pdf文件|未成功交付")
                && !normalized.contains("未返回对外可访问的下载url")
                && !normalized.startsWith("审批节点")
                && !normalized.startsWith("抱歉，本次任务未能完成")
                && !normalized.startsWith("抱歉，这次没能给你交付pdf文件")
                && !normalized.startsWith("我已结合你当前会话里的已有信息继续处理");
    }

    private String extractInlinePdfContent(String userMessage) {
        if (userMessage == null || userMessage.length() < 120) {
            return null;
        }
        int newlineIndex = userMessage.indexOf('\n');
        if (newlineIndex < 0 || newlineIndex >= userMessage.length() - 40) {
            return null;
        }
        String content = userMessage.substring(newlineIndex + 1).trim();
        return content.length() >= 40 ? content : null;
    }

    private record PdfSourceCandidate(Integer sequenceNo, String content) {
    }
}
