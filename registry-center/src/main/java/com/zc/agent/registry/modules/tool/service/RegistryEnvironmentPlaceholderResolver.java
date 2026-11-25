package com.zc.agent.registry.modules.tool.service;

import com.zc.agent.registry.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry MCP 发现配置环境变量解析器，确保密钥和连接串不必明文入库。
 */
@Component
public class RegistryEnvironmentPlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");
    private final Function<String, String> environment;

    public RegistryEnvironmentPlaceholderResolver() {
        this(System::getenv);
    }

    RegistryEnvironmentPlaceholderResolver(Function<String, String> environment) {
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
                throw new BusinessException("MCP 配置所需环境变量未设置：" + name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
