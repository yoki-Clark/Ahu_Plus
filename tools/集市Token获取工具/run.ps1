# =====================================================================
#  校园集市 Token 一键获取工具 —— 主逻辑
#  由「获取集市Token.cmd」调起，普通用户无需直接运行本文件。
#
#  做的事：
#   1) 准备便携版 Python（工具目录内，绝不污染系统）
#   2) 给它装 mitmproxy + 本地二维码组件（国内镜像加速）
#   3) 生成临时 CA，仅安装到当前用户受信任根
#   4) 设置系统代理 -> 127.0.0.1:8080
#   5) 启动抓包，等用户在电脑微信打开集市小程序点一下
#   6) 抓到 token 后自动收尾；无论如何都会还原代理并删除证书/私钥
# =====================================================================

param(
    [switch]$RuntimeSelfTest,
    [string]$RuntimeSelfTestPython,
    [switch]$CertificateSelfTest
)

$ErrorActionPreference = "Stop"

# 中文 Windows 用户名下，Python 默认按 GBK 输出 sys.executable，PowerShell 按
# UTF-8 解码会乱码导致路径存在性检查失败（误判无系统 Python）。强制两端统一
# UTF-8；ASCII 用户名不受影响。只改 Python 端在 GBK 控制台上反而会制造乱码。
$env:PYTHONIOENCODING = "utf-8"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# 代理端口不再写死 8080：主流程会调 Resolve-FreePort 扫到一个本机空闲端口，
# 避免 8080 被 Clash/开发服务占用导致整个工具硬失败。
$ProxyPort   = 0
$ProxyHost   = "127.0.0.1"
$Root        = Split-Path -Parent $MyInvocation.MyCommand.Path
$PyDir       = Join-Path $Root "python"
$PortablePy  = Join-Path $PyDir "python.exe"   # 便携版路径（兜底用）
$PyExe       = $PortablePy                      # 实际使用的 python；Ensure-Runtime 可能改指系统 Python
$Script      = Join-Path $Root "catch_token.py"
$MemoryScanner = Join-Path $Root "wechat_memory_token.py"
$DoneFlag    = Join-Path $Root ".captured"
$TokenFile   = Join-Path $Root "我的集市Token.txt"
$QrFile      = Join-Path $Root "集市身份导入二维码.png"
$TargetFlag  = Join-Path $Root ".target_seen"
$AuthFlag    = Join-Path $Root ".auth_seen"
$InvalidAuthFlag = Join-Path $Root ".invalid_auth_seen"
$TlsFailedFlag = Join-Path $Root ".tls_failed"
$CaDir       = Join-Path ([IO.Path]::GetTempPath()) "AhuPlus-Market-CA-$PID"
$CertFile    = Join-Path $CaDir "mitmproxy-ca-cert.cer"
$CertMarker  = Join-Path $Root ".market-ca-thumbprint"

# mitmproxy 没有 `python -m mitmproxy.tools.main` 入口（该模块无 __main__ 守卫，
# -m 会静默什么都不做）。用同目录的启动垫片 _mitmrun.py 显式调用 mitmdump()，
# 这样后续参数也能稳妥地经 sys.argv 传入（Start-Process 对带空格的 -c 串会拆词）。
$MitmRun     = Join-Path $Root "_mitmrun.py"

# Python embeddable 下载源（国内镜像优先，失败回退官方）
$PyVer = "3.12.7"
$PyUrls = @(
    "https://mirrors.huaweicloud.com/python/$PyVer/python-$PyVer-embed-amd64.zip",
    "https://www.python.org/ftp/python/$PyVer/python-$PyVer-embed-amd64.zip"
)
$GetPipUrls = @(
    "https://mirrors.aliyun.com/pypi/get-pip.py",
    "https://bootstrap.pypa.io/get-pip.py"
)
$PipIndex = "https://mirrors.aliyun.com/pypi/simple/"

function Info($m){ Write-Host "  $m" -ForegroundColor Cyan }
function Ok($m){   Write-Host "  $m" -ForegroundColor Green }
function Warn($m){ Write-Host "  $m" -ForegroundColor Yellow }
function Err($m){  Write-Host "  $m" -ForegroundColor Red }

function Invoke-PythonCommand{
    param(
        [Parameter(Mandatory=$true)][string]$Python,
        [Parameter(Mandatory=$true)][string[]]$Arguments,
        [switch]$Quiet
    )
    $previousErrorAction = $ErrorActionPreference
    $exitCode = 1
    $output = ""
    try{
        # Windows PowerShell 5.1 会在全局 Stop 模式下把原生程序的 stderr
        # 提升成终止异常。Python 缺模块属于可预期探测结果，必须按退出码处理。
        $ErrorActionPreference = "Continue"
        if($Quiet){
            $output = (& $Python @Arguments 2>$null | Out-String)
        }else{
            $lines = & $Python @Arguments 2>&1
            if($lines){
                $lines | ForEach-Object { Write-Host "  $($_.ToString())" }
            }
        }
        $exitCode = $LASTEXITCODE
    }catch{
        $output = $_.Exception.Message
        $exitCode = 1
    }finally{
        $ErrorActionPreference = $previousErrorAction
    }
    return [PSCustomObject]@{ ExitCode = $exitCode; Output = $output }
}

function Download-File($urls, $dest){
    foreach($u in $urls){
        try{
            Info "下载：$u"
            Invoke-WebRequest -Uri $u -OutFile $dest -UseBasicParsing -TimeoutSec 120
            if(Test-Path $dest){ return $true }
        }catch{ Warn "该源失败，换下一个…" }
    }
    return $false
}

# ---- 0. 优先复用系统现成的 Python / mitmproxy ----
function Resolve-SystemPython{
    # 返回可用的系统 python.exe 绝对路径；找不到/版本太低返回 $null。
    # 优先 py launcher（最可靠），其次 python/python3。
    # 排除 Microsoft Store 的 stub（WindowsApps 路径）——它没真装时会弹商店，
    # 且要求 >=3.10（mitmproxy 12 的下限），不够则视为不可用。
    $probe = "import sys;sys.stdout.write(sys.executable if sys.version_info>=(3,10) else '')"
    $cands = @(
        @{ exe="py";      pre=@("-3") },
        @{ exe="python";  pre=@() },
        @{ exe="python3"; pre=@() }
    )
    foreach($c in $cands){
        $callArgs = $c.pre + @("-c", $probe)
        $result = Invoke-PythonCommand -Python $c.exe -Arguments $callArgs -Quiet
        $out = $result.Output.Trim()
        if($result.ExitCode -eq 0 -and $out -and
           [IO.File]::Exists($out) -and $out -notmatch 'WindowsApps'){
            return $out
        }
    }
    return $null
}

function Test-HasRuntime($py){
    if(-not $py){ return $false }
    $result = Invoke-PythonCommand -Python $py `
        -Arguments @("-c", "import mitmproxy,qrcode,sys;sys.stdout.write('ok')") -Quiet
    return $result.ExitCode -eq 0 -and $result.Output.Trim() -eq "ok"
}

# 决定用哪个 Python：能用系统现成的就用，最后才下载便携版。
function Ensure-Runtime{
    $sys = Resolve-SystemPython
    if($sys){
        if(Test-HasRuntime $sys){
            $script:PyExe = $sys
            Ok "检测到系统已装 Python + 抓包组件，直接使用（免下载）。"
            return
        }
        # 有 Python 没组件：装到当前用户目录（--user，不动全局 site-packages）
        Info "检测到系统 Python，正在安装抓包和本地二维码组件（仅当前用户）…"
        $install = Invoke-PythonCommand -Python $sys -Arguments @(
            "-m", "pip", "install", "--user", "mitmproxy", "qrcode[pil]",
            "-i", $PipIndex, "--no-warn-script-location"
        )
        if($install.ExitCode -eq 0 -and (Test-HasRuntime $sys)){
            $script:PyExe = $sys
            Ok "已用系统 Python 准备好抓包组件。"
            return
        }
        Warn "用系统 Python 安装失败，改用便携版（不动你的系统）…"
    }
    # 兜底：完全没有可用系统 Python，或上面装失败 → 便携版
    Ensure-PortablePython
    Ensure-PortableMitmproxy
}

# ---- 1. 准备便携 Python（兜底，仅在没有系统 Python 时） ----
function Ensure-PortablePython{
    if(Test-Path $PortablePy){ $script:PyExe = $PortablePy; return }
    Info "未发现可用的系统 Python，正在准备便携版（只放在本文件夹，不动你的系统）…"
    $zip = Join-Path $Root "py-embed.zip"
    if(-not (Download-File $PyUrls $zip)){
        throw "Python 下载失败。请检查网络后重试，或手动把 python-$PyVer-embed-amd64.zip 解压到 $PyDir"
    }
    Info "解压中…"
    if(Test-Path $PyDir){ Remove-Item $PyDir -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $PyDir -Force
    Remove-Item $zip -Force

    # embeddable 版默认禁用 site-packages，需打开 ._pth 里的 import site
    $pth = Get-ChildItem $PyDir -Filter "python*._pth" | Select-Object -First 1
    if($pth){
        # 注意：Get-Content 返回字符串数组，-join 成单串再判断/写回，
        # 否则 -notmatch 作用在数组上会过滤元素而非给布尔值，导致重复追加。
        $c = (Get-Content $pth.FullName) -join "`n"
        $c = $c -replace '(?m)^#\s*import site','import site'
        if($c -notmatch '(?m)^\s*import site'){ $c += "`nimport site" }
        Set-Content $pth.FullName $c -Encoding ASCII
    }

    # 装 pip
    Info "安装 pip…"
    $getpip = Join-Path $Root "get-pip.py"
    if(-not (Download-File $GetPipUrls $getpip)){ throw "get-pip.py 下载失败" }
    $getPipResult = Invoke-PythonCommand -Python $PortablePy -Arguments @(
        $getpip, "--no-warn-script-location", "-i", $PipIndex
    )
    if($getPipResult.ExitCode -ne 0){ throw "pip 安装失败" }
    Remove-Item $getpip -Force
    $script:PyExe = $PortablePy
    Ok "Python 环境就绪。"
}

# ---- 2. 便携 Python 装 mitmproxy ----
function Ensure-PortableMitmproxy{
    if(Test-HasRuntime $PortablePy){ return }
    Info "安装抓包和本地二维码组件（首次较慢，请耐心等待）…"
    $install = Invoke-PythonCommand -Python $PortablePy -Arguments @(
        "-m", "pip", "install", "mitmproxy", "qrcode[pil]",
        "-i", $PipIndex, "--no-warn-script-location"
    )
    if($install.ExitCode -ne 0){ throw "抓包组件安装失败，请检查网络后重试。" }
    Ok "抓包组件就绪。"
}

# ---- 3. 生成并安装临时 CA 证书（仅当前用户） ----
function Remove-InstalledCert{
    $thumbprint = $script:installedCertThumbprint
    if(-not $thumbprint -and (Test-Path $CertMarker)){
        $thumbprint = (Get-Content $CertMarker -Raw -ErrorAction SilentlyContinue).Trim()
    }
    if($thumbprint -match '^[0-9A-Fa-f]{40}$'){
        $certPath = "Cert:\CurrentUser\Root\$thumbprint"
        if(Test-Path $certPath){
            Remove-Item -LiteralPath $certPath -Force -ErrorAction SilentlyContinue
        }
    }
    $script:installedCertThumbprint = $null
    Remove-Item -LiteralPath $CertMarker -Force -ErrorAction SilentlyContinue
}

function Ensure-Cert{
    # 内存扫描已经抓到 → 不再花时间生成证书，直接跳过代理路。
    if(Test-Path $DoneFlag){ return }

    # 上次若被强制结束，按精确指纹清掉遗留证书。
    Remove-InstalledCert
    if(Test-Path $CaDir){ Remove-Item -LiteralPath $CaDir -Recurse -Force }
    New-Item -ItemType Directory -Path $CaDir -Force | Out-Null

    if(-not (Test-Path $CertFile)){
        Info "生成本次运行专用的临时抓包证书…"
        $script:certProcess = Start-Process -FilePath $PyExe `
            -ArgumentList "$MitmRun","-p","0","--set","confdir=$CaDir","--set","termlog_verbosity=error" `
            -PassThru -NoNewWindow

        # 首次安装后 Windows Defender/磁盘扫描可能让 Python 冷启动超过 4 秒。
        # 等待实际产物，而不是固定睡眠；进程提前退出时保留 stderr 作为诊断信息。
        # 同时轮询 DoneFlag：内存扫描赢了就立刻杀掉证书子进程、不再等待。
        $deadline = (Get-Date).AddSeconds(30)
        $heartbeat = (Get-Date)
        while((Get-Date) -lt $deadline -and
              -not (Test-Path $CertFile) -and
              -not $script:certProcess.HasExited){
            if(Test-Path $DoneFlag){
                if($script:certProcess -and -not $script:certProcess.HasExited){ $script:certProcess.Kill() }
                return
            }
            Start-Sleep -Milliseconds 250
            # 每 5 秒打一个心跳，让用户知道在等证书而不是卡死。
            if((Get-Date).Subtract($heartbeat).TotalSeconds -ge 5){
                $elapsed = [int](Get-Date).Subtract($deadline.AddSeconds(-30)).TotalSeconds
                Write-Host "    仍在生成临时证书…（已等 ${elapsed}s）" -ForegroundColor DarkGray
                $heartbeat = (Get-Date)
            }
        }
        if($script:certProcess -and -not $script:certProcess.HasExited){ $script:certProcess.Kill() }
        Start-Sleep -Milliseconds 250
    }
    if(-not (Test-Path $CertFile)){
        $exitDetail = if($script:certProcess -and $script:certProcess.HasExited){
            "子进程退出码 $($script:certProcess.ExitCode)"
        }else{ "等待 30 秒仍无产物" }
        throw "临时抓包证书生成失败：$exitDetail。详细错误已显示在当前窗口。"
    }
    # 装证书前再查一次：内存扫描可能在证书生成完成的瞬间获胜。
    if(Test-Path $DoneFlag){ return }
    Info "临时信任抓包证书（仅当前 Windows 用户）…"
    $installed = Import-Certificate -FilePath $CertFile -CertStoreLocation "Cert:\CurrentUser\Root"
    if(-not $installed){ throw "临时证书安装失败。" }
    $script:installedCertThumbprint = $installed.Thumbprint
    Set-Content -LiteralPath $CertMarker -Value $installed.Thumbprint -Encoding ASCII
    Ok "临时证书已安装，工具结束时会自动删除。"
}

# ---- 4/6. 系统代理读写 ----
$RegPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
function Get-ProxyState{
    $k = Get-ItemProperty -Path $RegPath
    return @{ Enable = $k.ProxyEnable; Server = $k.ProxyServer }
}
function Set-Proxy($server){
    Set-ItemProperty -Path $RegPath -Name ProxyServer -Value $server
    Set-ItemProperty -Path $RegPath -Name ProxyEnable -Value 1
    Refresh-WinINet
}
function Restore-Proxy($state){
    if($null -eq $state){ return }
    if($state.Enable){
        Set-ItemProperty -Path $RegPath -Name ProxyEnable -Value $state.Enable
        if($state.Server){ Set-ItemProperty -Path $RegPath -Name ProxyServer -Value $state.Server }
    }else{
        Set-ItemProperty -Path $RegPath -Name ProxyEnable -Value 0
    }
    Refresh-WinINet
}

# 上一次运行如果被 X 掉窗口或被强杀，PowerShell 的 finally 不会执行，
# 系统代理会残留指向 127.0.0.1:<上次端口> 但此刻无人监听——表现为微信
# 小程序"网络错误"。启动时检测这种孤儿状态并自动清掉。
function Clear-LeftoverProxy{
    $cur = Get-ProxyState
    if(-not $cur -or -not $cur.Enable -or -not $cur.Server){ return $false }
    $server = "$($cur.Server)".Trim()
    # 只处理指向本机回环的代理；用户自己的外部代理（如公司代理）不动。
    if($server -notmatch '^127\.0\.0\.1:(\d+)$' -and $server -notmatch '^localhost:(\d+)$'){
        return $false
    }
    $port = [int]$matches[1]
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if($listener){ return $false }   # 有人正在听，可能是用户其它工具，不碰

    Warn "检测到上一次运行遗留的孤儿系统代理：$server（当前无人监听）。"
    Write-Host "    这会让微信小程序显示网络错误。正在清理…" -ForegroundColor White
    Set-ItemProperty -Path $RegPath -Name ProxyEnable -Value 0 -ErrorAction SilentlyContinue
    Remove-ItemProperty -Path $RegPath -Name ProxyServer -ErrorAction SilentlyContinue
    Refresh-WinINet
    Ok "孤儿代理已清除。"
    return $true
}
function Refresh-WinINet{
    # 通知系统代理设置已变（不重启浏览器/微信也能生效）
    $sig = @'
[DllImport("wininet.dll", SetLastError=true)]
public static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);
'@
    try{
        $t = Add-Type -MemberDefinition $sig -Name WinINet -Namespace Net -PassThru -ErrorAction SilentlyContinue
        $INTERNET_OPTION_SETTINGS_CHANGED = 39
        $INTERNET_OPTION_REFRESH = 37
        $t::InternetSetOption([IntPtr]::Zero,$INTERNET_OPTION_SETTINGS_CHANGED,[IntPtr]::Zero,0) | Out-Null
        $t::InternetSetOption([IntPtr]::Zero,$INTERNET_OPTION_REFRESH,[IntPtr]::Zero,0) | Out-Null
    }catch{}
}

function Get-WeChatProcesses{
    return @(Get-Process -Name "Weixin","WeChat","WeChatAppEx" -ErrorAction SilentlyContinue)
}

# 从 8080 起向上扫一个本机空闲端口。Clash/IDE/开发服务常驻 8080 是工具
# 最频发的硬失败点；参数化后 mitmdump 不再硬绑 8080。
function Resolve-FreePort{
    param([int]$Start = 8080, [int]$MaxAttempts = 2048)
    $p = $Start
    $tried = 0
    while($tried -lt $MaxAttempts){
        $tried++
        $busy = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
        if(-not $busy){ return $p }
        $p++
        if($p -gt 65535){ $p = 1024 }
    }
    throw "在端口 $Start 之后扫了 $MaxAttempts 个均有占用，未能找到空闲端口。"
}

# 检测可能以 TUN/虚拟网卡或全局系统代理方式接管流量的代理软件。
# 这类客户端即便不修改注册表代理，也会在协议栈更下层拦截，导致 mitmdump
# 看不到任何集市流量——是"时灵时不灵"最常见的不显式失败原因。
function Get-TunProxyProcesses{
    # 覆盖主流 Clash 系、V2Ray 系、sing-box 系、独狼 Trojan/SS 等。Get-Process
    # -Name 支持通配符，匹配主进程名；子进程（如 mihomo helper）也会被一并提示。
    $patterns = @(
        "clash*","verge*","mihomo*",
        "v2ray*","v2rayN*","xray*","sing-box*",
        "trojan*","shadow*","ssr*","netch*",
        "wireguard*","tun2socks*"
    )
    $found = @()
    foreach($pat in $patterns){
        try{
            $procs = Get-Process -Name $pat -ErrorAction SilentlyContinue
            if($procs){ $found += $procs }
        }catch{}
    }
    # 去重（同一 PID 可能命中多个通配）
    return $found | Sort-Object Id -Unique
}

# 检测出 TUN 类代理后阻塞并交互：R=重检 / S=跳过代理只走内存扫描 / C=强行继续 / Q=退出。
# 返回 "ok"（无 TUN）/ "skip-proxy"（用户选 S）/ "force"（用户选 C）。
# 非交互控制台（CI/出管道）下回退到 Read-Host。
function Wait-TunProxyResolved{
    while($true){
        $procs = Get-TunProxyProcesses
        if(-not $procs -or $procs.Count -eq 0){ return "ok" }
        Write-Host ""
        Warn "检测到以下可能接管系统流量的代理软件仍在运行："
        $procs | ForEach-Object {
            Write-Host ("    - {0} (PID {1})" -f $_.ProcessName, $_.Id) -ForegroundColor White
        }
        Write-Host ""
        Write-Host "  这些软件若以 TUN/虚拟网卡模式运行，会在协议栈更下层拦截流量，" -ForegroundColor Yellow
        Write-Host "  导致抓包代理看不到任何集市请求。" -ForegroundColor Yellow
        Write-Host "   R = 重新检测    S = 跳过代理，只走内存扫描    C = 强行继续    Q = 退出工具" -ForegroundColor Yellow
        $code = 0
        try{
            $key = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
            $code = [int]$key.VirtualKeyCode
        }catch{
            $answer = (Read-Host "请输入 R / S / C / Q").Trim().ToUpper()
            $code = switch($answer){
                "R" { 82 }
                "S" { 83 }
                "C" { 67 }
                "Q" { 81 }
                default { 82 }
            }
        }
        if($code -eq 81){ throw "用户取消：请先关闭代理软件后重新运行工具。" }
        if($code -eq 83){
            Warn "跳过代理捕获，仅尝试内存扫描（需要电脑版微信已运行）。"
            return "skip-proxy"
        }
        if($code -eq 67){
            Warn "继续运行；抓包若失败，请优先检查上述代理软件的 TUN 模式。"
            return "force"
        }
        # R 或其它键：循环重检
    }
}

function Wait-ProxyReady($process, $port, $seconds = 20){
    $deadline = (Get-Date).AddSeconds($seconds)
    while((Get-Date) -lt $deadline){
        if($process.HasExited){ return $false }
        $client = New-Object Net.Sockets.TcpClient
        try{
            $task = $client.ConnectAsync($ProxyHost, $port)
            if($task.Wait(300) -and $client.Connected){ return $true }
        }catch{}finally{
            $client.Dispose()
        }
        Start-Sleep -Milliseconds 250
    }
    return $false
}

# 非阻塞启动代理：调用方拿到 mitm 进程引用后自行轮询 DoneFlag。
# 失败抛异常供主流程降级为"只内存扫描"。
function Start-ProxyCapture{
    param([Parameter(Mandatory=$true)][int]$Port)
    $script:proxyAttempted = $true
    # 内存扫描可能在我们等证书的间隙赢了，入口处再查一次避免无谓工作。
    if(Test-Path $DoneFlag){ return $null }
    Info "正在生成本次运行专用的临时抓包证书（最多 30 秒）…"
    Ensure-Cert
    # Ensure-Cert 可能因 DoneFlag 早退，返回后复查一次。
    if(-not (Test-Path $CertFile)){ return $null }
    Info "正在启动抓包代理并等待端口 $Port 就绪…"
    $mitmArgs = @("$MitmRun","-s","$Script","-p","$Port",
                  "--set","confdir=$CaDir","--allow-hosts","zxs-bbs\.cn$",
                  "--set","termlog_verbosity=warn","--set","flow_detail=0")
    $proc = Start-Process -FilePath $PyExe -ArgumentList $mitmArgs -PassThru -NoNewWindow
    if(-not (Wait-ProxyReady $proc $Port)){
        if($proc -and -not $proc.HasExited){ try{ $proc.Kill() }catch{} }
        $exitDetail = if($proc.HasExited){ "退出码 $($proc.ExitCode)" } else { "等待监听超时（20s）" }
        throw "抓包代理启动失败（$exitDetail），请查看窗口中的错误信息。"
    }
    return $proc
}

if($RuntimeSelfTest){
    $candidate = if($RuntimeSelfTestPython){ $RuntimeSelfTestPython } else { Resolve-SystemPython }
    if(-not $candidate){ throw "自检失败：未找到可用的 Python" }
    $available = Test-HasRuntime $candidate
    Write-Output "Runtime probe completed: available=$available python=$candidate"
    exit 0
}

if($CertificateSelfTest){
    $candidate = Resolve-SystemPython
    if(-not $candidate -or -not (Test-HasRuntime $candidate)){
        throw "证书自检失败：没有已安装抓包组件的 Python"
    }
    $script:PyExe = $candidate
    try{
        Ensure-Cert
        Write-Output "Certificate lifecycle completed: generated, installed and ready for cleanup"
    }finally{
        if($script:certProcess -and -not $script:certProcess.HasExited){
            $script:certProcess.Kill()
        }
        Remove-InstalledCert
        if(Test-Path $CaDir){
            Remove-Item -LiteralPath $CaDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
    exit 0
}

# =====================================================================
#  主流程
# =====================================================================
Clear-Host
Write-Host ""
Write-Host "  ============================================" -ForegroundColor White
Write-Host "      校园集市 Token 一键获取工具" -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor White
Write-Host ""

$savedProxy = $null
$proxyChanged = $false
$proxyAttempted = $false
$mitmProcess = $null
$memoryProcess = $null
$certProcess = $null
$installedCertThumbprint = $null

# 关键：很多同学开着 clash/v2ray，系统代理是 socks=127.0.0.1:xxxx。
# 便携版 Python 没装 PySocks，pip 走系统 SOCKS 代理会报
# “Missing dependencies for SOCKS support” 直接装不上。
# 这里在进程内把代理环境变量清空并设 NO_PROXY=*，让 pip/python 全程直连
# （不影响 .NET 的 Invoke-WebRequest 下载，也不影响 mitmdump 抓包数据面）。
$env:HTTP_PROXY = ""; $env:HTTPS_PROXY = ""; $env:ALL_PROXY = ""
$env:http_proxy = ""; $env:https_proxy = ""; $env:all_proxy = ""
$env:NO_PROXY = "*"; $env:no_proxy = "*"

# 双路并行总超时。原来串行（内存 30s + 代理 300s）最坏 330s，并有 ~30s
# 空窗；改为并行后两条路径同时跑，180s 足够覆盖一次正常的"启动-刷新-抓到"。
$TotalTimeoutSeconds = 180

$savedProxy = $null
$proxyChanged = $false
$proxyAttempted = $false
$proxyStarted = $false
$mitmProcess = $null
$memoryProcess = $null
$memoryAttempted = $false
$certProcess = $null
$installedCertThumbprint = $null

try{
    Ensure-Runtime

    # TUN 类代理检测：返回 ok / skip-proxy / force。
    $tunStatus = Wait-TunProxyResolved
    $skipProxy = ($tunStatus -eq "skip-proxy")

    # 清理上一次崩溃可能遗留的孤儿系统代理（否则微信会显示网络错误）。
    Clear-LeftoverProxy | Out-Null

    # 删旧结果
    @($DoneFlag,$TargetFlag,$AuthFlag,$InvalidAuthFlag,$TlsFailedFlag) | ForEach-Object {
        if(Test-Path $_){ Remove-Item -LiteralPath $_ -Force }
    }
    if(Test-Path $TokenFile){ Remove-Item $TokenFile -Force }
    if(Test-Path $QrFile){ Remove-Item $QrFile -Force }

    $runningWeChat = Get-WeChatProcesses

    # ── 内存路优先启动：不依赖代理，不受 TUN / 端口 / 证书影响 ──
    if($runningWeChat.Count -gt 0){
        Write-Host ""
        Ok "检测到已运行的电脑版微信，立即启动内存扫描。"
        Write-Host "    请打开【校园集市 / 赞噢校园集市】并在帖子列表下拉刷新。" -ForegroundColor White
        $script:memoryProcess = Start-Process -FilePath $PyExe `
            -ArgumentList @("`"$MemoryScanner`"","--watch-seconds","$TotalTimeoutSeconds") `
            -PassThru -NoNewWindow
        $memoryAttempted = $true
    }

    # ── 代理路：用户选 S 或无微信时跳过；否则尽力启动，失败不阻塞内存扫描 ──
    if(-not $skipProxy){
        # 自动选空闲端口（取代写死的 8080）。
        $ProxyPort = Resolve-FreePort
        Info "本次抓包代理端口：$ProxyHost`:$ProxyPort"

        try{
            $script:mitmProcess = Start-ProxyCapture -Port $ProxyPort
            # 内存扫描在我们等证书的间隙赢了 → 跳过代理上线，直接进轮询。
            if($null -ne $script:mitmProcess){
                $script:savedProxy = Get-ProxyState
                Set-Proxy "$ProxyHost`:$ProxyPort"
                $script:proxyChanged = $true
                $script:proxyAttempted = $true
                $proxyStarted = $true
                Write-Host ""
                Ok "抓包代理已就绪（127.0.0.1:$ProxyPort）。"
                Write-Host "    仅观察 zxs-bbs.cn 任意子域，长期凭据只写入本机文件。" -ForegroundColor White
                if($memoryAttempted){
                    Write-Host "    内存扫描与代理捕获并行运行中，谁先识别到即结束。" -ForegroundColor White
                }
            }
        }catch{
            $proxyError = $_.Exception.Message
            $script:proxyAttempted = $true
            if($runningWeChat.Count -gt 0){
                Warn "代理模式不可用（$proxyError），仅靠内存扫描。"
                Write-Host "    如内存扫描也失败，请退出代理软件 / 关闭占用端口的程序后重试。" -ForegroundColor White
            }else{
                # 微信未运行且代理起不来：工具无路可走。
                throw "代理模式不可用且微信未运行，无法捕获：$proxyError"
            }
        }
    }elseif($runningWeChat.Count -eq 0){
        throw "用户选择跳过代理，但未检测到微信进程，无可用的捕获路径。请打开微信后重试。"
    }else{
        Write-Host ""
        Info "已跳过代理捕获，仅靠内存扫描（请在小程序中下拉刷新触发请求）。"
    }

    Write-Host ""
    Info "正在等待捕获（最多 $TotalTimeoutSeconds 秒）…"

    # ── 统一轮询：任一路命中 DoneFlag 即停，或两边都死则早停 ──
    $deadline = (Get-Date).AddSeconds($TotalTimeoutSeconds)
    while((Get-Date) -lt $deadline){
        if(Test-Path $DoneFlag){ Start-Sleep -Milliseconds 800; break }
        $mitmDead = $null -eq $script:mitmProcess -or $script:mitmProcess.HasExited
        $memDead = $null -eq $script:memoryProcess -or $script:memoryProcess.HasExited
        # 两路都退出且都没拿到结果 → 再等也没意义。
        if($mitmDead -and $memDead){ break }
        Start-Sleep -Milliseconds 500
    }

    # ── 结果展示 ──
    Write-Host ""
    if(Test-Path $TokenFile){
        Ok "已成功获取！Token 仅保存在本机文件："
        Write-Host "    $TokenFile" -ForegroundColor White
        Write-Host ""
        if(Test-Path $QrFile){
            Ok "导入二维码已在本机生成并打开，请用 App 扫描："
            Write-Host "    $QrFile" -ForegroundColor White
            Start-Process -FilePath $QrFile | Out-Null
        }
        Ok "工具不会自动复制 Token；需要时可手动打开本机 Token 文件。"
    }else{
        if(Test-Path $InvalidAuthFlag){
            Warn "已看到集市请求和鉴权头，但它不是可识别的 JWT。"
            Write-Host "    集市接口可能调整了身份格式，请保留窗口信息并反馈。" -ForegroundColor White
        }elseif(Test-Path $AuthFlag){
            Warn "已看到集市请求和身份字段，但解析没有完成。"
            Write-Host "    请确认小程序已登录，再重新进入帖子列表刷新。" -ForegroundColor White
        }elseif(Test-Path $TargetFlag){
            Warn "已看到集市请求，但请求中没有身份字段。"
            Write-Host "    请先在小程序完成登录，然后回帖子列表下拉刷新。" -ForegroundColor White
        }elseif(Test-Path $TlsFailedFlag){
            Warn "微信连接到了代理，但 HTTPS 证书校验失败。"
            Write-Host "    请允许安全软件信任临时证书后重试。" -ForegroundColor White
        }elseif($memoryAttempted -and -not $proxyStarted){
            if($skipProxy){
                Warn "内存扫描超时未识别到身份（已跳过代理捕获）。"
                Write-Host "    请确认小程序已登录，并在帖子列表下拉刷新后保持页面打开。" -ForegroundColor White
                Write-Host "    若仍失败，请退出 Clash/V2ray 等代理软件后选 C 或不运行 TUN 模式重试。" -ForegroundColor White
            }else{
                Warn "内存扫描超时未识别到身份（代理未启动）。"
                Write-Host "    请确认小程序已登录，并在帖子列表下拉刷新后保持页面打开。" -ForegroundColor White
                Write-Host "    若安全软件拦截了读取微信进程，请允许本工具后重试。" -ForegroundColor White
            }
        }elseif($memoryAttempted -and $proxyStarted){
            Warn "双路并行运行超时：既未在微信内存中识别到身份，也未捕获到集市请求。"
            Write-Host "    请确认小程序已登录，并在帖子列表下拉刷新一次。" -ForegroundColor White
            Write-Host "    若开了 Clash/V2ray 等代理软件，请彻底退出（含 TUN 模式）后重试。" -ForegroundColor White
        }else{
            Warn "没有集市流量进入抓包代理。"
            Write-Host "    请在已登录的集市帖子列表中下拉刷新一次。" -ForegroundColor White
        }
        Write-Host ""
        Warn "重新运行本工具再试一次即可。"
    }
}
catch{
    Write-Host ""
    Err "出错了：$($_.Exception.Message)"
    Write-Host ""
}
finally{
    if($mitmProcess -and -not $mitmProcess.HasExited){
        $mitmProcess.Kill()
    }
    if($memoryProcess -and -not $memoryProcess.HasExited){
        $memoryProcess.Kill()
    }
    if($certProcess -and -not $certProcess.HasExited){
        $certProcess.Kill()
    }
    # 无论成功失败，都还原代理并删除本次运行的证书和 CA 私钥。
    if($proxyChanged){
        Info "正在还原系统网络设置…"
        Restore-Proxy $savedProxy
        Ok "网络设置已还原。"
    }
    Remove-InstalledCert
    if(Test-Path $CaDir){
        Remove-Item -LiteralPath $CaDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    @($TargetFlag,$AuthFlag,$InvalidAuthFlag,$TlsFailedFlag) | ForEach-Object {
        Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue
    }
    Ok "临时证书和抓包私钥已清理。"
    Write-Host ""
    Write-Host "  按任意键关闭窗口…" -ForegroundColor DarkGray
    try{
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }finally{
        if(Test-Path $QrFile){
            Remove-Item -LiteralPath $QrFile -Force -ErrorAction SilentlyContinue
        }
    }
}
