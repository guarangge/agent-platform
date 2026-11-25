#!/usr/bin/env python3
"""生成本地配置，并将 dotenv 作为进程环境传递给独立的 Spring Boot 服务。"""

import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
DB_PASSWORD_KEYS = ("POSTGRES_PASSWORD", "POSTGRES_ADMIN_PASSWORD", "REGISTRY_DB_PASSWORD", "AGENT_CORE_DB_PASSWORD")
REQUIRED = {
    "registry-center": ("REGISTRY_DB_PASSWORD", "REGISTRY_JWT_SECRET", "REGISTRY_RUNTIME_SERVICE_TOKEN", "REGISTRY_TOOL_ENCRYPTION_KEY"),
    "agent-core": ("AGENT_CORE_DB_PASSWORD", "CORE_JWT_SECRET", "REGISTRY_RUNTIME_SERVICE_TOKEN", "CORE_WAIT_WEBHOOK_SECRET"),
}


def parse_env(path):
    """读取简单 KEY=value 格式，不执行文件内容、不展开变量、不输出配置值。"""
    values = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        key = key.strip()
        if not separator or not key or not (key[0].isalpha() or key[0] == "_") or not all(c.isalnum() or c == "_" for c in key):
            raise ValueError("环境文件第 %d 行格式错误" % number)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in (chr(34), chr(39)):
            value = value[1:-1]
        values[key] = value
    return values


def initialize(root=ROOT):
    """仅为全新本地环境生成随机配置，使用排他写入避免覆盖现有密钥。"""
    template = (root / ".env.example").read_text(encoding="utf-8-sig")
    database_password = secrets.token_hex(32)
    jwt_secret = secrets.token_hex(32)
    generated = {key: database_password for key in DB_PASSWORD_KEYS}
    generated.update({
        "REGISTRY_JWT_SECRET": jwt_secret,
        "CORE_JWT_SECRET": jwt_secret,
        "REGISTRY_RUNTIME_SERVICE_TOKEN": secrets.token_hex(32),
        "REGISTRY_TOOL_ENCRYPTION_KEY": secrets.token_hex(32),
        "CORE_WAIT_WEBHOOK_SECRET": secrets.token_hex(32),
        "REGISTRY_BOOTSTRAP_ADMIN_PASSWORD": secrets.token_hex(24),
        "MCP_POSTGRES_PASSWORD": secrets.token_hex(32),
    })
    lines = []
    for line in template.splitlines():
        key, separator, _ = line.partition("=")
        lines.append(key + "=" + generated[key] if separator and key in generated else line)
    destination = root / ".env"
    descriptor = os.open(str(destination), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join(lines) + "\n")
    print("已生成本地 .env，未输出密钥。已有数据库请先按 README 校准密码和原加密密钥。")
    return destination


def service_environment(service, root=ROOT):
    """显式进程环境优先于本地文件，缺少必要配置时在启动前失败。"""
    env_file = root / ".env"
    environment = parse_env(env_file) if env_file.is_file() else {}
    environment.update(os.environ)
    missing = [key for key in REQUIRED[service] if not environment.get(key, "").strip()]
    if missing:
        raise ValueError("缺少环境变量：" + ", ".join(missing))
    registry_secret = environment.get("REGISTRY_JWT_SECRET")
    core_secret = environment.get("CORE_JWT_SECRET")
    if registry_secret and core_secret and registry_secret != core_secret:
        raise ValueError("REGISTRY_JWT_SECRET 与 CORE_JWT_SECRET 必须一致")
    return environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    subcommands.add_parser("init", help="生成随机 .env，拒绝覆盖已有文件；已有数据库须保留原加密密钥")
    run_parser = subcommands.add_parser("run", help="加载本地配置并启动一个服务")
    run_parser.add_argument("service", choices=sorted(REQUIRED))
    args = parser.parse_args()
    try:
        if args.command == "init":
            initialize()
            return 0
        environment = service_environment(args.service)
        executable = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
        if not executable:
            raise ValueError("未找到 Maven，请安装并加入 PATH")
        return subprocess.call([executable, "-pl", args.service, "spring-boot:run"], cwd=str(ROOT), env=environment)
    except FileExistsError:
        print(".env 已存在，拒绝覆盖。请编辑原文件；不要重新生成已有数据库的加密密钥。", file=sys.stderr)
        return 1
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
