#!/bin/bash
# 教育邮箱握手流程本地验证脚本
# 用法:
#   export AHU_TEST_USERNAME="your_student_id"
#   export AHU_TEST_PASSWORD="your_password"
#   bash test_mail_handshake.sh

set -e

if [ -z "$AHU_TEST_USERNAME" ] || [ -z "$AHU_TEST_PASSWORD" ]; then
    echo "错误: 需要设置环境变量 AHU_TEST_USERNAME 和 AHU_TEST_PASSWORD"
    exit 1
fi

COOKIE_JAR=$(mktemp)
trap "rm -f $COOKIE_JAR" EXIT

echo "=== 步骤 0: CAS 登录获取 TGT ==="
# 这里需要实现 CAS 登录获取 CASTGC cookie
# 简化起见，先假设已有 CASTGC

echo "=== 步骤 1: WebVPN 登录获取 wengine_vpn_ticket ==="
# GET /domain/oa/Entry 触发 WebVPN 登录链
TRIGGER_URL="https://wvpn.ahu.edu.cn/https/77726476706e69737468656265737421f5f9558e3e38721e6f0190a9d6047566363d1097/domain/oa/Entry"
echo "GET $TRIGGER_URL"
curl -v -L -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
  "$TRIGGER_URL" > /dev/null 2>&1 || true

echo ""
echo "=== 检查 wengine_vpn_ticket cookie ==="
grep "wengine_vpn_ticket" "$COOKIE_JAR" || echo "未找到 wengine_vpn_ticket"

echo ""
echo "=== 步骤 2: 调用 generateSsoUrl ==="
SSO_URL="https://wvpn.ahu.edu.cn/https/77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b/tp_up/up/subgroup/generateSsoUrl"
echo "POST $SSO_URL"
curl -v -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
  -H "Accept: application/json, text/plain, */*" \
  -H "Content-Type: application/json;charset=UTF-8" \
  -d '{}' \
  "$SSO_URL"

echo ""
echo "=== Cookie Jar 内容 ==="
cat "$COOKIE_JAR"
