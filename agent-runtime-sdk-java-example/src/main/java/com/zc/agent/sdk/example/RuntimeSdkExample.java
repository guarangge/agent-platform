package com.zc.agent.sdk.example;

import com.zc.agent.sdk.runtime.CreateRuntimeRunOptions;
import com.zc.agent.sdk.runtime.CreateRuntimeRunRequest;
import com.zc.agent.sdk.runtime.CreateRuntimeThreadRequest;
import com.zc.agent.sdk.runtime.RuntimeApprovalActionRequest;
import com.zc.agent.sdk.runtime.RuntimeClient;
import com.zc.agent.sdk.runtime.RuntimeEventEnvelope;
import com.zc.agent.sdk.runtime.RuntimeEventListener;
import com.zc.agent.sdk.runtime.RuntimeEventStreamOptions;
import com.zc.agent.sdk.runtime.RuntimeEventStreamResult;
import com.zc.agent.sdk.runtime.RuntimeRun;
import com.zc.agent.sdk.runtime.RuntimeThread;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime Java SDK 独立示例：创建 Thread/Run、消费事件，并在审批挂起时沿原 Task 自动继续。
 */
public final class RuntimeSdkExample {

    private static final int MAX_APPROVAL_ROUNDS = 3;

    private RuntimeSdkExample() {
    }

    /** 从命令行参数和环境变量读取配置并执行示例。 */
    public static void main(String[] args) {
        ExampleConfig config = ExampleConfig.parse(args, System.getenv());
        ExampleResult result = execute(config);
        System.out.printf(
                "Java Runtime SDK 示例完成: threadId=%d, runCode=%s, status=%s, taskId=%s, lastSequenceNo=%d%n",
                result.threadId(), result.runCode(), result.status(), result.taskId(), result.lastSequenceNo());
    }

    /** 执行完整 SDK 主链，供 Contract Test 和外部宿主复用。 */
    public static ExampleResult execute(ExampleConfig config) {
        RuntimeClient client = RuntimeClient.builder(config.baseUrl())
                .accessTokenProvider(config::accessToken)
                .requestTimeout(config.requestTimeout())
                .build();
        RuntimeThread thread = client.createThread(new CreateRuntimeThreadRequest(config.agentId(), config.title()));
        RuntimeRun run = client.createRun(
                thread.conversationId(),
                new CreateRuntimeRunRequest(config.message()),
                new CreateRuntimeRunOptions(config.idempotencyKey(), config.priority()));

        long cursor = 0;
        StringBuilder answer = new StringBuilder();
        for (int round = 0; round <= MAX_APPROVAL_ROUNDS; round++) {
            AtomicReference<ApprovalContext> approval = new AtomicReference<>();
            RuntimeEventStreamResult streamResult = client.streamEvents(
                    run.runCode(),
                    listener(answer, approval),
                    new RuntimeEventStreamOptions(cursor, config.streamTimeout(), Duration.ofMillis(200), 3));
            cursor = streamResult.lastSequenceNo();
            if (!"waiting_approval".equals(streamResult.status())) {
                if ("failed".equals(streamResult.status()) || "cancelled".equals(streamResult.status())) {
                    throw new IllegalStateException("Run 未成功完成: status=" + streamResult.status()
                            + ", error=" + streamResult.errorMessage());
                }
                verifyExpectedAnswer(config.expectedAnswer(), answer.toString());
                return new ExampleResult(thread.conversationId(), run.runCode(), streamResult.status(),
                        streamResult.taskId(), cursor, answer.toString());
            }
            if (!config.autoApprove()) {
                return new ExampleResult(thread.conversationId(), run.runCode(), streamResult.status(),
                        streamResult.taskId(), cursor, answer.toString());
            }
            ApprovalContext context = approval.get();
            if (context == null || context.approvalId() <= 0 || streamResult.taskId() == null) {
                throw new IllegalStateException("收到 waiting_approval，但事件缺少 taskId 或 approvalId");
            }
            client.approveTask(thread.conversationId(), streamResult.taskId(), context.approvalId(),
                    new RuntimeApprovalActionRequest(config.approvalComment(), null));
        }
        throw new IllegalStateException("审批轮次超过安全上限 " + MAX_APPROVAL_ROUNDS);
    }

    private static RuntimeEventListener listener(StringBuilder answer,
                                                 AtomicReference<ApprovalContext> approval) {
        return new RuntimeEventListener() {
            @Override
            public void onDelta(String content) {
                answer.append(content);
            }

            @Override
            public void onApprovalRequested(RuntimeEventEnvelope event) {
                approval.set(new ApprovalContext(resolveApprovalId(event)));
            }
        };
    }

    private static long resolveApprovalId(RuntimeEventEnvelope event) {
        Object value = event.payload() == null ? null : event.payload().get("approvalId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        String candidate = value == null ? event.itemId() : String.valueOf(value);
        if (candidate == null || candidate.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(candidate);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void verifyExpectedAnswer(String expectedAnswer, String answer) {
        if (expectedAnswer != null && !expectedAnswer.isBlank() && !answer.contains(expectedAnswer)) {
            throw new IllegalStateException("最终消息未包含预期内容: " + expectedAnswer);
        }
    }

    /** 示例运行参数。 */
    public record ExampleConfig(
            String baseUrl,
            String accessToken,
            long agentId,
            String title,
            String message,
            String idempotencyKey,
            Integer priority,
            boolean autoApprove,
            String approvalComment,
            String expectedAnswer,
            Duration requestTimeout,
            Duration streamTimeout
    ) {
        /** 解析命令行参数；Token 优先从 AGENT_RUNTIME_TOKEN 环境变量读取，避免出现在进程参数中。 */
        public static ExampleConfig parse(String[] args, Map<String, String> environment) {
            Map<String, String> options = parseOptions(args);
            String token = firstNonBlank(environment.get("AGENT_RUNTIME_TOKEN"), options.get("token"));
            return new ExampleConfig(
                    required(options, "base-url"),
                    required(token, "AGENT_RUNTIME_TOKEN"),
                    positiveLong(required(options, "agent-id"), "agent-id"),
                    options.getOrDefault("title", "Java Runtime SDK 示例"),
                    options.getOrDefault("message", "请介绍当前 Agent Runtime。"),
                    options.get("idempotency-key"),
                    integer(options.getOrDefault("priority", "50"), "priority"),
                    Boolean.parseBoolean(options.getOrDefault("auto-approve", "true")),
                    options.getOrDefault("approval-comment", "Java SDK 示例自动审批"),
                    options.get("expected-answer"),
                    Duration.ofSeconds(positiveLong(options.getOrDefault("request-timeout-seconds", "60"),
                            "request-timeout-seconds")),
                    Duration.ofSeconds(positiveLong(options.getOrDefault("stream-timeout-seconds", "180"),
                            "stream-timeout-seconds")));
        }

        private static Map<String, String> parseOptions(String[] args) {
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < args.length; index++) {
                String key = args[index];
                if (!key.startsWith("--") || index + 1 >= args.length) {
                    throw new IllegalArgumentException("参数必须使用 --key value 格式: " + key);
                }
                result.put(key.substring(2), args[++index]);
            }
            return result;
        }

        private static String required(Map<String, String> values, String key) {
            return required(values.get(key), key);
        }

        private static String required(String value, String key) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(key + " 不能为空");
            }
            return value.trim();
        }

        private static long positiveLong(String value, String key) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed <= 0) {
                    throw new NumberFormatException();
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " 必须为正整数", exception);
            }
        }

        private static Integer integer(String value, String key) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0 || parsed > 100) {
                    throw new NumberFormatException();
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " 必须在 0 到 100 之间", exception);
            }
        }

        private static String firstNonBlank(String first, String second) {
            return first != null && !first.isBlank() ? first : second;
        }
    }

    /** 示例执行结果。 */
    public record ExampleResult(
            long threadId,
            String runCode,
            String status,
            Long taskId,
            long lastSequenceNo,
            String answer
    ) {
    }

    private record ApprovalContext(long approvalId) {
    }
}
