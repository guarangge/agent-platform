package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.modules.tool.dto.ToolInstallRequest;
import com.zc.agent.registry.modules.tool.repository.ToolLifecycleRepository;
import com.zc.agent.registry.modules.tool.repository.ToolRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolLifecycleServiceTest {

    @Test
    void shouldCreateRuntimeBindingWhenInstallingCatalogTool() {
        ToolLifecycleRepository lifecycleRepository = mock(ToolLifecycleRepository.class);
        ToolRepository toolRepository = mock(ToolRepository.class);
        ToolRemoteDiscoveryService discoveryService = mock(ToolRemoteDiscoveryService.class);
        ToolSecretCryptoService cryptoService = mock(ToolSecretCryptoService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ToolLifecycleService service = new ToolLifecycleService(
                lifecycleRepository, toolRepository, discoveryService, cryptoService, objectMapper);
        when(lifecycleRepository.findCatalogItem(9L)).thenReturn(Optional.of(Map.of(
                "id", 9L,
                "catalog_id", 3L,
                "remote_name", "read_file",
                "description", "读取文件",
                "input_schema", "{\"type\":\"object\"}"
        )));
        when(lifecycleRepository.insertInstalledTool(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(21L);
        when(lifecycleRepository.insertVersion(
                org.mockito.ArgumentMatchers.eq(21L), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(22L);
        when(lifecycleRepository.insertDeployment(
                org.mockito.ArgumentMatchers.eq(21L), org.mockito.ArgumentMatchers.eq(22L), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.isNull(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(23L);
        when(cryptoService.encryptJsonText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(toolRepository.upsertToolProvider(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(31L);
        when(toolRepository.upsertToolProviderInstance(
                org.mockito.ArgumentMatchers.eq(31L), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.isNull(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(32L);

        var result = service.install(new ToolInstallRequest(
                9L, "FS_READ_FILE", "读取文件", "file", "general", "default", "1.0.0",
                "mcp", "stdio", null, "none", "local", objectMapper.createObjectNode(),
                objectMapper.createObjectNode(), objectMapper.createObjectNode()
                        .put("command", "mcp-server-filesystem")
        ), 1L);

        assertThat(result.path("toolId").asLong()).isEqualTo(21L);
        verify(toolRepository).upsertToolBinding(
                21L, 31L, 32L, "read_file", "mcp_tool", "{\"command\":\"mcp-server-filesystem\"}",
                "{}", "{}", "enabled", 1L);
        verify(lifecycleRepository).insertInstalledTool(
                "FS_READ_FILE", "读取文件", "file", "general", "读取文件", "{\"type\":\"object\"}",
                "default", "[\"filesystem.read\"]", "none",
                "{\"catalogItemId\":9,\"remoteName\":\"read_file\"}", 1L);
    }
}
