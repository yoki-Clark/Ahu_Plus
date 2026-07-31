#!/usr/bin/env python3
"""
教育邮箱握手测试脚本 - 验证完整 SSO 流程

测试目标:
1. 复用现有 CAS TGT (从 SessionManager 读取)
2. 获取 WebVPN ticket
3. 完成 Sirius SSO 握手
4. 拿到 Coremail/mCoremail/QIYE_SESS cookie 和 sid
5. 调用业务 API 验证 session 有效

环境变量:
- AHU_TEST_USERNAME: 测试学号
- AHU_TEST_PASSWORD: 测试密码
"""
import os
import re
import sys
import json
import requests
from urllib.parse import urlparse, parse_qs, unquote

# 禁用 SSL 警告(WebVPN 可能有证书问题)
requests.packages.urllib3.disable_warnings()

class MailHandshakeTester:
    def __init__(self):
        self.session = requests.Session()
        self.session.verify = False  # 测试环境可能需要
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36'
        })

        # WebVPN 常量
        self.WVPN_HOST = "wvpn.ahu.edu.cn"
        self.HEX_MAIL = "77726476706e69737468656265737421fdf6489069237c45300981b9d6502720b63704"
        self.HEX_ONE = "77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b"
        self.HEX_ONE_ENTRY = "77726476706e69737468656265737421f5f9558e3e38721e6f0190a9d6047566363d1097"
        self.MAIL_HOST = "mail.stu.ahu.edu.cn"
        self.DEVICE_ID = "174796e776c5b2859c980f4f907e3ca0"

    def cas_3des_encrypt(self, data):
        """调 node 跑 CAS 官网原版 des.js 加密(与 Kotlin DES.strEnc 一致)。"""
        import subprocess
        node_script = (
            "const fs=require('fs');"
            "eval(fs.readFileSync(process.argv[1],'utf8'));"
            "console.log(strEnc(process.argv[2],'1','2','3'));"
        )
        out = subprocess.run(
            ["node", "-e", node_script, "E:/Ahu_Plus/des_orig.js", data],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        return out

    def step1_cas_login(self, username, password):
        """Step 1: CAS 统一认证登录(与 CasAuthRepository 一致: device 预验证 + rsa 表单)"""
        print("\n=== Step 1: CAS Login ===")

        service = "https://one.ahu.edu.cn/tp_up/view?m=up"
        cas_url = "https://one.ahu.edu.cn/cas/login?service=" + service
        resp = self.session.get(cas_url, allow_redirects=True)
        print(f"GET {cas_url} -> {resp.status_code}")

        lt_match = re.search(r'name="lt"\s+value="([^"]+)"', resp.text)
        exec_match = re.search(r'name="execution"\s+value="([^"]+)"', resp.text)
        if not lt_match or not exec_match:
            print("[X] 未找到 lt/execution")
            return False
        lt = lt_match.group(1)
        execution = exec_match.group(1)
        print(f"lt={lt[:20]}... execution={execution}")

        encrypted = self.cas_3des_encrypt(username + password + lt)
        ul, pl = len(username), len(password)

        # /cas/device 预验证
        device_data = {
            'ul': str(ul), 'pl': str(pl), 'rsa': encrypted, 'method': 'login'
        }
        resp = self.session.post(
            "https://one.ahu.edu.cn/cas/device",
            data=device_data,
            headers={
                'X-Requested-With': 'XMLHttpRequest',
                'Accept': 'application/json, text/javascript, */*; q=0.01',
                'Referer': cas_url,
            },
            allow_redirects=True
        )
        print(f"POST /cas/device -> {resp.status_code}: {resp.text[:100]}")
        if '"ok"' not in resp.text:
            print("[X] device 预验证失败")
            return False

        # POST /cas/login 提交表单
        login_data = {
            'rsa': encrypted, 'ul': str(ul), 'pl': str(pl),
            'lt': lt, 'execution': execution, '_eventId': 'submit'
        }
        resp = self.session.post(
            "https://one.ahu.edu.cn/cas/login?service=" + service,
            data=login_data,
            headers={'Referer': cas_url, 'Content-Type': 'application/x-www-form-urlencoded'},
            allow_redirects=False
        )
        print(f"POST /cas/login -> {resp.status_code}")

        castgc = self.session.cookies.get('CASTGC', domain='one.ahu.edu.cn')
        if castgc:
            print(f"[OK] 获得 CASTGC: {castgc[:30]}...")
            return True
        print("[X] CAS 登录失败, cookies=" + str(list(self.session.cookies.keys())))
        return False

    def step2_webvpn_login(self):
        """Step 2: 获取 WebVPN ticket"""
        print("\n=== Step 2: WebVPN Login ===")

        # 触发 WebVPN 登录流程
        trigger_url = f"https://{self.WVPN_HOST}/https/{self.HEX_ONE_ENTRY}/domain/oa/Entry"
        print(f"GET {trigger_url}")

        # 手动跟随重定向以观察每一步
        resp = self.session.get(trigger_url, allow_redirects=False)

        redirect_count = 0
        while resp.status_code in (302, 301) and redirect_count < 10:
            location = resp.headers.get('Location')
            print(f"  [{redirect_count}] {resp.status_code} -> {location[:80] if location else 'None'}...")

            if not location:
                break

            # 解析绝对 URL
            if location.startswith('http'):
                next_url = location
            else:
                next_url = f"https://{self.WVPN_HOST}{location}"

            resp = self.session.get(next_url, allow_redirects=False)
            redirect_count += 1

        # 检查 wengine_vpn_ticket cookie
        wvpn_ticket = self.session.cookies.get('wengine_vpn_ticketwvpn_ahu_edu_cn', domain=self.WVPN_HOST)
        if wvpn_ticket:
            print(f"[OK] 获得 WebVPN ticket: {wvpn_ticket}")
            return True
        else:
            print("[X] 未获得 WebVPN ticket")
            print(f"当前 cookies: {dict(self.session.cookies)}")
            return False

    def step3_generate_sso_url(self):
        """Step 3: 调用 generateSsoUrl 获取网易 SSO URL"""
        print("\n=== Step 3: Generate SSO URL ===")

        url = f"https://{self.WVPN_HOST}/https/{self.HEX_ONE}/tp_up/up/subgroup/generateSsoUrl?vpn-12-o2-one.ahu.edu.cn"
        resp = self.session.post(url, json={})
        print(f"POST {url} -> {resp.status_code}")

        if resp.status_code != 200:
            print(f"[X] HTTP {resp.status_code}")
            return None

        data = resp.json()
        ssourl = data.get('ssourl')
        if ssourl:
            print(f"[OK] ssourl: {ssourl[:80]}...")
            return ssourl
        else:
            print(f"[X] 响应无 ssourl: {data}")
            return None

    def step4_sso_handshake(self, ssourl):
        """Step 4-10: Sirius SSO 7步跳转"""
        print("\n=== Step 4-10: Sirius SSO Handshake ===")

        # 跟随 ssourl 的完整重定向链
        resp = self.session.get(ssourl, allow_redirects=False)

        redirect_count = 0
        while resp.status_code in (302, 301) and redirect_count < 15:
            location = resp.headers.get('Location')
            print(f"  [{redirect_count}] {resp.status_code} -> {location[:80] if location else 'None'}...")

            # 检查 Set-Cookie
            set_cookies = resp.headers.get_list('Set-Cookie') if hasattr(resp.headers, 'get_list') else []
            for sc in set_cookies:
                cookie_name = sc.split('=')[0]
                if cookie_name in ['Coremail', 'mCoremail', 'QIYE_SESS', 'sid']:
                    print(f"    Set-Cookie: {cookie_name}={sc.split(';')[0].split('=')[1][:30]}...")

            if not location:
                break

            # 构造下一个 URL
            if location.startswith('http'):
                next_url = location
            elif location.startswith('/'):
                # 判断当前域
                current_host = urlparse(resp.url).netloc
                next_url = f"https://{current_host}{location}"
            else:
                current_host = urlparse(resp.url).netloc
                next_url = f"https://{current_host}/{location}"

            resp = self.session.get(next_url, allow_redirects=False)
            redirect_count += 1

        # 最后一步应该到达 exmail.qq.com 或回到 Entry
        print(f"最终 URL: {resp.url}")
        print(f"最终状态码: {resp.status_code}")

        return True

    def step11_cookie_bridge(self):
        """Step 11: 通过 cookie 中转桥拉取 mail.stu.ahu.edu.cn 的 cookie"""
        print("\n=== Step 11: Cookie Bridge ===")

        import time
        timestamp = int(time.time() * 1000)

        url = (f"https://{self.WVPN_HOST}/wengine-vpn/cookie"
               f"?method=get&host={self.MAIL_HOST}&scheme=http"
               f"&path=/&vpn_timestamp={timestamp}")

        resp = self.session.get(url)
        print(f"GET cookie bridge -> {resp.status_code}")

        if resp.status_code == 200:
            cookies_str = resp.text
            print(f"获得 cookies: {cookies_str[:200]}...")

            # 解析并注入到 session
            for cookie in cookies_str.split(';'):
                cookie = cookie.strip()
                if '=' in cookie:
                    name, value = cookie.split('=', 1)
                    self.session.cookies.set(name, value, domain=self.MAIL_HOST)

            return True
        else:
            print(f"[X] Cookie bridge 失败")
            return False

    def step12_validate_session(self):
        """Step 12: 验证 session - 调用 accountInfo API"""
        print("\n=== Step 12: Validate Session ===")

        url = (f"https://{self.WVPN_HOST}/http/{self.HEX_MAIL}"
               f"/cowork/api/biz/enter/accountInfo"
               f"?vpn-12-o1-{self.MAIL_HOST}&_host={self.MAIL_HOST}"
               f"&sid=&needUnitNamePath=false&_version=1.65.2"
               f"&_appName=sirius-web&_deviceId={self.DEVICE_ID}")

        resp = self.session.get(url)
        print(f"GET accountInfo -> {resp.status_code}")

        if resp.status_code != 200:
            print(f"[X] HTTP {resp.status_code}")
            return False

        data = resp.json()
        if data.get('success'):
            account_data = data.get('data', {})
            print(f"[OK] 登录成功!")
            print(f"  邮箱: {account_data.get('email')}")
            print(f"  昵称: {account_data.get('nickName')}")
            print(f"  组织: {account_data.get('orgName')}")
            return True
        else:
            print(f"[X] API 返回失败: {data}")
            return False

    def run_full_test(self):
        """执行完整测试流程"""
        username = os.getenv('AHU_TEST_USERNAME')
        password = os.getenv('AHU_TEST_PASSWORD')

        if not username or not password:
            print("[ERROR] 请设置环境变量: AHU_TEST_USERNAME, AHU_TEST_PASSWORD")
            return False

        print(f"测试账号: {username}")

        # Step 1: CAS Login
        if not self.step1_cas_login(username, password):
            return False

        # Step 2: WebVPN Login
        if not self.step2_webvpn_login():
            return False

        # Step 3: Generate SSO URL
        ssourl = self.step3_generate_sso_url()
        if not ssourl:
            return False

        # Step 4-10: SSO Handshake
        if not self.step4_sso_handshake(ssourl):
            return False

        # Step 11: Cookie Bridge
        if not self.step11_cookie_bridge():
            return False

        # Step 12: Validate Session
        if not self.step12_validate_session():
            return False

        print("\n" + "="*50)
        print("[OK] 完整握手流程测试通过!")
        print("="*50)
        return True

if __name__ == '__main__':
    tester = MailHandshakeTester()
    success = tester.run_full_test()
    sys.exit(0 if success else 1)
