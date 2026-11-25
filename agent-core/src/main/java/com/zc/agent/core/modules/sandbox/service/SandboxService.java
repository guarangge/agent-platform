package com.zc.agent.core.modules.sandbox.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.sandbox.dto.ExecuteSandboxRequest;
import com.zc.agent.core.modules.sandbox.dto.SandboxExecutionQueryRequest;
import com.zc.agent.core.modules.sandbox.dto.SandboxProfileQueryRequest;
import com.zc.agent.core.modules.sandbox.dto.SandboxProfileRequest;
import com.zc.agent.core.modules.sandbox.model.SandboxExecutionRecord;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import com.zc.agent.core.modules.sandbox.repository.SandboxRepository;
import com.zc.agent.core.modules.sandbox.vo.SandboxArtifactResponse;
import com.zc.agent.core.modules.sandbox.vo.SandboxExecutionResponse;
import com.zc.agent.core.security.SecurityUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sandbox 配置管理、容器执行和审计编排服务。
 */
@Slf4j
@Service
public class SandboxService {

    private final SandboxRepository repository;
    private final SandboxPolicyValidator policyValidator;
    private final SandboxWorkspaceService workspaceService;
    private final DockerSandboxExecutor executor;
    private final CostGovernanceService costGovernanceService;

    public SandboxService(SandboxRepository repository, SandboxPolicyValidator policyValidator,
                          SandboxWorkspaceService workspaceService, DockerSandboxExecutor executor,
                          CostGovernanceService costGovernanceService) {
        this.repository = repository;
        this.policyValidator = policyValidator;
        this.workspaceService = workspaceService;
        this.executor = executor;
        this.costGovernanceService = costGovernanceService;
    }

    public PageResponse<SandboxProfileRecord> pageProfiles(SandboxProfileQueryRequest request) {
        int pageNum = request.pageNum() == null ? 1 : request.pageNum();
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        long total = repository.countProfiles(request.keyword(), request.status());
        List<SandboxProfileRecord> records = repository.pageProfiles(request.keyword(), request.status(),
                (pageNum - 1) * pageSize, pageSize);
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public SandboxProfileRecord getProfile(Long profileId) {
        return repository.findProfile(profileId).orElseThrow(() -> new BusinessException("Sandbox 配置不存在"));
    }

    /**
     * 按稳定编码查询 Sandbox 配置，供 Tool 运行时使用。
     *
     * @param profileCode Sandbox 配置编码
     * @return Sandbox 配置
     */
    public SandboxProfileRecord getProfileByCode(String profileCode) {
        return repository.findProfileByCode(profileCode)
                .orElseThrow(() -> new BusinessException("Sandbox 配置不存在：" + profileCode));
    }

    @Transactional
    public SandboxProfileRecord createProfile(SandboxProfileRequest request, SecurityUser user) {
        SandboxProfileRecord profile = toProfile(null, request);
        policyValidator.validateProfile(profile);
        try {
            Long id = repository.insertProfile(profile, user.userId());
            return getProfile(id);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Sandbox 配置编码已存在");
        }
    }

    @Transactional
    public SandboxProfileRecord updateProfile(Long profileId, SandboxProfileRequest request, SecurityUser user) {
        SandboxProfileRecord existing = getProfile(profileId);
        if (!existing.profileCode().equals(request.profileCode())) {
            throw new BusinessException("Sandbox 配置编码不允许修改");
        }
        SandboxProfileRecord profile = toProfile(profileId, request);
        policyValidator.validateProfile(profile);
        repository.updateProfile(profileId, profile, user.userId());
        return getProfile(profileId);
    }

    /**
     * 同步执行独立容器；无论成功、失败或超时都先完成审计，再清理工作区。
     */
    public SandboxExecutionResponse execute(ExecuteSandboxRequest request, SecurityUser user) {
        SandboxProfileRecord profile = getProfile(request.profileId());
        costGovernanceService.assertAllowed("sandbox", profile.profileCode(), 0, 0, java.math.BigDecimal.valueOf(profile.timeoutSeconds()));
        long meteringStartedAt = System.nanoTime();
        policyValidator.validateExecution(profile, request.command(), request.inputFiles(), request.artifactPaths());
        String code = "SBOX_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        SandboxWorkspaceService.Workspace workspace = workspaceService.create(code, request.inputFiles(), profile.maxWorkspaceBytes());
        Long executionId = repository.insertExecution(code, profile, request.command(), workspace.inputBytes(), user.userId(), user.username());
        Path workspacePath = workspace.path();
        try {
            DockerSandboxExecutor.ExecutionResult result = executor.execute(executionId, profile, request.command(), workspacePath,
                    containerId -> repository.markRunning(executionId, containerId));
            long workspaceBytes = workspaceService.calculateSize(workspacePath);
            String status = result.timedOut() ? "timed_out"
                    : result.outputTruncated() || result.workspaceExceeded() ? "failed"
                    : Integer.valueOf(0).equals(result.exitCode()) ? "succeeded" : "failed";
            String error = result.timedOut() ? "Sandbox 执行超时"
                    : result.outputTruncated() ? "Sandbox 输出超过限制，容器已终止"
                    : result.workspaceExceeded() ? "Sandbox 工作区超过限制，容器已终止"
                    : Integer.valueOf(0).equals(result.exitCode()) ? null : "Sandbox 进程返回非零退出码";
            repository.completeExecution(executionId, status, result.exitCode(), result.timedOut(), result.outputTruncated(),
                    result.stdout(), result.stderr(), error, result.outputBytes(), workspaceBytes, result.durationMs());
            SandboxExecutionRecord execution = getExecution(executionId);
            List<SandboxArtifactResponse> artifacts = "succeeded".equals(execution.status())
                    ? workspaceService.collectArtifacts(workspacePath, request.artifactPaths(), profile.maxWorkspaceBytes()) : List.of();
            log.info("Sandbox 执行完成 executionCode={}, profileCode={}, status={}, durationMs={}",
                    code, profile.profileCode(), execution.status(), execution.durationMs());
            costGovernanceService.recordSandbox(profile.profileCode(), execution.durationMs(), execution.status());
            return new SandboxExecutionResponse(execution, artifacts);
        } catch (RuntimeException exception) {
            SandboxExecutionRecord current = repository.findExecution(executionId).orElse(null);
            if (current == null || !"cancelled".equals(current.status())) {
                repository.completeExecution(executionId, "failed", null, false, false, "", "",
                        safeMessage(exception), 0, workspaceService.calculateSize(workspacePath), 0);
            }
            costGovernanceService.recordSandbox(profile.profileCode(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - meteringStartedAt), "failed");
            throw exception;
        } finally {
            workspaceService.cleanup(workspacePath);
        }
    }

    /**
     * 按配置编码执行 Sandbox，避免 Tool 配置依赖不同环境下不稳定的数据库主键。
     */
    public SandboxExecutionResponse executeByProfileCode(String profileCode,
                                                         List<String> command,
                                                         List<com.zc.agent.core.modules.sandbox.dto.SandboxInputFileRequest> inputFiles,
                                                         List<String> artifactPaths,
                                                         SecurityUser user) {
        SandboxProfileRecord profile = getProfileByCode(profileCode);
        return execute(new ExecuteSandboxRequest(profile.profileId(), command, inputFiles, artifactPaths), user);
    }

    public SandboxExecutionRecord cancel(Long executionId) {
        SandboxExecutionRecord execution = getExecution(executionId);
        if (!List.of("pending", "running").contains(execution.status())) {
            throw new BusinessException("当前 Sandbox 执行状态不允许取消");
        }
        repository.markCancelled(executionId);
        executor.cancel(executionId);
        log.info("Sandbox 执行已取消 executionCode={}", execution.executionCode());
        return getExecution(executionId);
    }

    public PageResponse<SandboxExecutionRecord> pageExecutions(SandboxExecutionQueryRequest request) {
        int pageNum = request.pageNum() == null ? 1 : request.pageNum();
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        long total = repository.countExecutions(request.profileId(), request.status(), request.executionCode(),
                request.startedAfter(), request.startedBefore());
        List<SandboxExecutionRecord> records = repository.pageExecutions(request.profileId(), request.status(),
                request.executionCode(), request.startedAfter(), request.startedBefore(), (pageNum - 1) * pageSize, pageSize);
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public SandboxExecutionRecord getExecution(Long executionId) {
        return repository.findExecution(executionId).orElseThrow(() -> new BusinessException("Sandbox 执行记录不存在"));
    }

    private SandboxProfileRecord toProfile(Long id, SandboxProfileRequest request) {
        return new SandboxProfileRecord(id, request.profileCode().trim(), request.profileName().trim(), request.image().trim(),
                request.allowedCommands().stream().map(String::trim).toList(), request.networkMode(),
                request.memoryBytes(), request.nanoCpus(), request.pidsLimit(), request.timeoutSeconds(),
                request.maxOutputBytes(), request.maxWorkspaceBytes(), request.status(), LocalDateTime.now(), LocalDateTime.now());
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
