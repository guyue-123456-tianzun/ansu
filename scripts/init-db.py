#!/usr/bin/env python3
"""Create the local ansu database and app user.

Reads ansu/.env, uses MYSQL_ROOT_USER/MYSQL_ROOT_PASSWORD once,
then creates DB `ansu` and user `ansu`/`ansu` for the app.
"""
import os
import subprocess
import sys
from pathlib import Path


def load_env(path: Path) -> dict:
    env = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip()
    return env


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    env_path = root / ".env"
    if not env_path.exists():
        print("缺少 .env，请先复制 .env.example 为 .env")
        return 1

    env = load_env(env_path)
    root_host = env.get("MYSQL_HOST", "127.0.0.1")
    root_port = env.get("MYSQL_PORT", "3306")
    root_user = env.get("MYSQL_ROOT_USER", "root")
    root_password = env.get("MYSQL_ROOT_PASSWORD", "")
    app_user = env.get("DB_USER", "ansu")
    app_password = env.get("DB_PASSWORD", "ansu")

    if not root_password or root_password == "CHANGE_ME":
        print("请先在 ansu/.env 里填写 MYSQL_ROOT_PASSWORD")
        return 1

    sql = (
        "CREATE DATABASE IF NOT EXISTS ansu "
        "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
        f"CREATE USER IF NOT EXISTS '{app_user}'@'localhost' IDENTIFIED BY '{app_password}';"
        f"CREATE USER IF NOT EXISTS '{app_user}'@'127.0.0.1' IDENTIFIED BY '{app_password}';"
        f"GRANT ALL PRIVILEGES ON ansu.* TO '{app_user}'@'localhost';"
        f"GRANT ALL PRIVILEGES ON ansu.* TO '{app_user}'@'127.0.0.1';"
        "FLUSH PRIVILEGES;"
    )

    cmd = ["mysql", "-h", root_host, "-P", root_port, "-u", root_user, "-e", sql]
    child_env = {**os.environ, "MYSQL_PWD": root_password}
    try:
        subprocess.run(cmd, check=True, env=child_env)
    except FileNotFoundError:
        print("找不到 mysql 命令，请把 MySQL bin 目录加入 PATH")
        return 1
    except subprocess.CalledProcessError:
        print("MySQL root 登录失败：请检查 MYSQL_ROOT_PASSWORD")
        return 1

    print(f"OK: database ansu ready; app user = {app_user}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
