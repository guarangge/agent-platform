package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 安全十进制算术 Tool，不执行脚本或任意代码。
 */
@Component
public class CalculatorToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "CALCULATOR";
    private final ObjectMapper objectMapper;

    public CalculatorToolExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        String expression = BuiltinToolSupport.text(request, "expression", request.toolInputText());
        if (expression == null || expression.isBlank()) {
            throw BuiltinToolSupport.failure("计算表达式不能为空", "CALCULATOR_EXPRESSION_REQUIRED", Map.of());
        }
        if (expression.length() > 2048) {
            throw BuiltinToolSupport.failure("计算表达式长度不能超过 2048 个字符", "CALCULATOR_EXPRESSION_TOO_LONG", Map.of());
        }
        BigDecimal result = new DecimalExpressionParser(expression).parse().stripTrailingZeros();
        String value = result.scale() < 0 ? result.setScale(0).toPlainString() : result.toPlainString();
        return new ToolExecutionResult(tool.toolCode(), tool.toolName(), "计算完成", value,
                BuiltinToolSupport.json(objectMapper, Map.of("expression", expression, "result", value)));
    }

    /** 仅支持确定性的十进制四则运算、取模和括号。 */
    private static final class DecimalExpressionParser {
        private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
        private final String expression;
        private int position;

        private DecimalExpressionParser(String expression) {
            this.expression = expression;
        }

        private BigDecimal parse() {
            try {
                BigDecimal value = parseExpression();
                skipSpaces();
                if (position != expression.length()) {
                    throw invalid();
                }
                return value;
            } catch (ArithmeticException exception) {
                throw BuiltinToolSupport.failure("计算失败：" + exception.getMessage(),
                        "CALCULATOR_ARITHMETIC_ERROR", Map.of());
            }
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                if (consume('+')) {
                    value = value.add(parseTerm(), MATH_CONTEXT);
                } else if (consume('-')) {
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (true) {
                if (consume('*')) {
                    value = value.multiply(parseFactor(), MATH_CONTEXT);
                } else if (consume('/')) {
                    value = value.divide(parseFactor(), MATH_CONTEXT);
                } else if (consume('%')) {
                    value = value.remainder(parseFactor(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parseFactor() {
            if (consume('+')) {
                return parseFactor();
            }
            if (consume('-')) {
                return parseFactor().negate(MATH_CONTEXT);
            }
            if (consume('(')) {
                BigDecimal value = parseExpression();
                if (!consume(')')) {
                    throw invalid();
                }
                return value;
            }
            skipSpaces();
            int start = position;
            boolean dotSeen = false;
            while (position < expression.length()) {
                char current = expression.charAt(position);
                if (Character.isDigit(current)) {
                    position++;
                } else if (current == '.' && !dotSeen) {
                    dotSeen = true;
                    position++;
                } else {
                    break;
                }
            }
            if (start == position || ".".equals(expression.substring(start, position))) {
                throw invalid();
            }
            try {
                return new BigDecimal(expression.substring(start, position), MATH_CONTEXT);
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private boolean consume(char expected) {
            skipSpaces();
            if (position < expression.length() && expression.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
        }

        private RuntimeException invalid() {
            return BuiltinToolSupport.failure("计算表达式格式错误，位置：" + position,
                    "CALCULATOR_EXPRESSION_INVALID", Map.of("position", position));
        }
    }
}
