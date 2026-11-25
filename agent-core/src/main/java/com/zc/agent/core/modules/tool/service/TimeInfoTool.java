package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 平台默认时间信息 Tool。
 */
@Service
public class TimeInfoTool {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;

    public TimeInfoTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 执行时间信息查询。
     *
     * @param tool Tool 定义
     * @return 执行结果
     */
    public ToolExecutionResult execute(RegistryAgentTool tool) {
        return execute(tool, PLATFORM_ZONE_ID.getId());
    }

    /**
     * 按指定 IANA 时区查询时间。
     *
     * @param tool Tool 定义
     * @param requestedZoneId IANA 时区，空值使用平台默认时区
     * @return 执行结果
     */
    public ToolExecutionResult execute(RegistryAgentTool tool, String requestedZoneId) {
        ZoneId zoneId;
        try {
            zoneId = requestedZoneId == null || requestedZoneId.isBlank()
                    ? PLATFORM_ZONE_ID : ZoneId.of(requestedZoneId.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException("无效时区：" + requestedZoneId);
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate today = now.toLocalDate();
        LocalDateTime localDateTime = now.toLocalDateTime();

        String responseText = "已获取当前时间信息。"
                + "\n当前本地时间：" + localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + "\n当前日期：" + today
                + "\n当前时区：" + zoneId;

        String outputSnapshot = toJsonText(new TimeInfoSnapshot(
                now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                today.toString(),
                zoneId.toString()
        ));
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                "已执行时间信息查询",
                responseText,
                outputSnapshot
        );
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("时间信息结果序列化失败");
        }
    }

    private record TimeInfoSnapshot(
            String zonedDateTime,
            String localDateTime,
            String currentDate,
            String zoneId
    ) {
    }
}
