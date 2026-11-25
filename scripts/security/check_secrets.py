#!/usr/bin/env python3
"""检查预计发布文件或 Git 暂存区中的凭据，只输出路径、行号和规则名。"""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = {".git", "node_modules", "target", "dist", ".venv", "venv", "__pycache__", ".runtime", "output", "backups", "test-results", "playwright-report", ".idea", ".vscode"}
PRIVATE_EXTENSIONS = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore"}
BINARY_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".docx", ".xlsx", ".pptx", ".jar", ".class", ".zip", ".ico", ".woff", ".woff2"}
PROVIDER_TOKEN = re.compile(r"\b(?:sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|xox[baprs]-[A-Za-z0-9-]{15,})\b")
JWT_TOKEN = re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b")
PRIVATE_KEY = re.compile(r"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----")
SENSITIVE_ENV = re.compile(r"\$\{([A-Z][A-Z0-9_]*(?:SECRET|PASSWORD|TOKEN|API_KEY|ENCRYPTION_KEY)):(-?)([^}\r\n]*)\}")
LEGACY_DEFAULT = re.compile(r"agent-platform-(?:local-jwt-secret|runtime-service|tool-secret)-change-this-in-prod|agent-wait-webhook-(?:change-this-in-prod)")
LITERAL = re.compile(r"(?:api[-_]?key|jwt[-_]?secret|service[-_]?token|encryption[-_]?key|password)\s*[\"']?\s*[:=]\s*[\"']([^\"'\r\n]+)[\"']", re.I)


def private_file(path):
    name = Path(path).name
    return (name.startswith(".env") and not name.endswith(".example")) or Path(path).suffix.lower() in PRIVATE_EXTENSIONS


def scan_text(path, content):
    """返回脱敏发现；示例和测试允许明确标注的假数据，真实凭据模式仍参与扫描。"""
    findings = []
    test_file = "/src/test/" in "/" + path or "/tests/" in "/" + path or path.startswith("scripts/test/")
    for number, line in enumerate(content.splitlines(), 1):
        labels = set()
        if PRIVATE_KEY.search(line):
            labels.add("private-key")
        if JWT_TOKEN.search(line):
            labels.add("jwt-literal")
        for match in PROVIDER_TOKEN.finditer(line):
            if not match.group().startswith(("sk-test-", "sk-example-", "sk-dummy-", "sk-fake-", "sk-mock-")):
                labels.add("provider-token")
        if not test_file:
            if LEGACY_DEFAULT.search(line):
                labels.add("shared-default-secret")
            for match in SENSITIVE_ENV.finditer(line):
                if match.group(3).strip() and not match.group(3).lstrip().startswith("?"):
                    labels.add("nonempty-secret-fallback")
            for match in LITERAL.finditer(line):
                value = match.group(1).strip()
                if value and not value.startswith(("$", "<", "Bearer ", "***")):
                    labels.add("hardcoded-sensitive-value")
        for label in sorted(labels):
            findings.append((number, label))
    return findings


def candidate_files(staged):
    """暂存模式读取索引内容，避免工作区已修改却漏掉待提交版本。"""
    if staged:
        result = subprocess.run(["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"], cwd=str(ROOT), env=os.environ.copy(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise ValueError("当前目录未初始化 Git，暂存区检查不可用；可先运行普通文件检查")
        for raw in result.stdout.split(b"\0"):
            if not raw:
                continue
            name = raw.decode("utf-8")
            blob = subprocess.run(["git", "show", ":" + name], cwd=str(ROOT), env=os.environ.copy(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            yield name, blob.stdout
        return
    for current, directories, names in os.walk(str(ROOT), followlinks=False):
        directories[:] = [name for name in directories if name not in SKIP_DIRS and not os.path.islink(os.path.join(current, name))]
        for name in names:
            path = Path(current) / name
            if path.is_symlink() or private_file(name) or path.suffix.lower() in BINARY_EXTENSIONS or name in {"package-lock.json", ".DS_Store", "Thumbs.db", "application-local.yml", "application-local.yaml", "application-local.properties"} or name.endswith((".log", ".dump", ".tsbuildinfo", ".pyc", ".pyo")):
                continue
            yield path.relative_to(ROOT).as_posix(), path.read_bytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="检查 Git 暂存版本并阻止私密文件进入提交")
    args = parser.parse_args()
    failures = []
    scanned = 0
    try:
        for path, raw in candidate_files(args.staged):
            if private_file(path):
                failures.append((path, 1, "private-file-staged"))
                continue
            if b"\0" in raw:
                continue
            try:
                content = raw.decode("utf-8-sig")
            except UnicodeDecodeError:
                failures.append((path, 1, "non-utf8-text"))
                continue
            scanned += 1
            failures.extend((path, line, label) for line, label in scan_text(path, content))
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print("检查失败：" + type(error).__name__, file=sys.stderr)
        return 2
    for path, line, label in failures:
        print("%s:%d [%s]" % (path, line, label))
    print("凭据检查完成：%d 个文本文件，%d 项发现。未输出凭据内容。" % (scanned, len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
