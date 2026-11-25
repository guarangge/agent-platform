package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Tool 配置中的环境变量占位符延迟解析，避免敏感值进入注册中心数据库。
 */
@Component
public class EnvironmentPlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");
    private final Function<String, String> environment;

    public EnvironmentPlaceholderResolver() {
        this(System::getenv);
    }

    EnvironmentPlaceholderResolver(Function<String, String> environment) {
        this.environment = environment;
    }

    public String resolve(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String resolved = environment.apply(name);
            if (resolved == null || resolved.isBlank()) {
                throw new ToolExecutionException("Tool 配置所需环境变量未设置：" + name,
                        "TOOL_ENVIRONMENT_VARIABLE_MISSING", "configuration", false, null,
                        Map.of("environmentVariable", name));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
