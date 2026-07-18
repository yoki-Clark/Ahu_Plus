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
$ProxyPort   = 8080
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
        $deadline = (Get-Date).AddSeconds(30)
        while((Get-Date) -lt $deadline -and
              -not (Test-Path $CertFile) -and
              -not $script:certProcess.HasExited){
            Start-Sleep -Milliseconds 250
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

function Wait-ProxyReady($process, $seconds = 20){
    $deadline = (Get-Date).AddSeconds($seconds)
    while((Get-Date) -lt $deadline){
        if($process.HasExited){ return $false }
        $client = New-Object Net.Sockets.TcpClient
        try{
            $task = $client.ConnectAsync($ProxyHost, $ProxyPort)
            if($task.Wait(300) -and $client.Connected){ return $true }
        }catch{}finally{
            $client.Dispose()
        }
        Start-Sleep -Milliseconds 250
    }
    return $false
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

try{
    Ensure-Runtime

    # 删旧结果
    @($DoneFlag,$TargetFlag,$AuthFlag,$InvalidAuthFlag,$TlsFailedFlag) | ForEach-Object {
        if(Test-Path $_){ Remove-Item -LiteralPath $_ -Force }
    }
    if(Test-Path $TokenFile){ Remove-Item $TokenFile -Force }
    if(Test-Path $QrFile){ Remove-Item $QrFile -Force }

    $runningWeChat = Get-WeChatProcesses
    if($runningWeChat.Count -gt 0){
        Write-Host ""
        Ok "检测到已运行的电脑版微信，将直接读取当前小程序登录态。"
        Write-Host "    不需要退出微信，不修改系统代理，也不会转储微信内存。" -ForegroundColor White
        Write-Host "    请打开【校园集市 / 赞噢校园集市】并下拉刷新帖子列表。" -ForegroundColor White
        Write-Host ""
        Info "正在等待本地身份（最多 5 分钟）…"

        $memoryProcess = Start-Process -FilePath $PyExe `
            -ArgumentList @("`"$MemoryScanner`"","--watch-seconds","300") `
            -PassThru -NoNewWindow
        $deadline = (Get-Date).AddMinutes(5)
        while((Get-Date) -lt $deadline){
            if(Test-Path $DoneFlag){ Start-Sleep -Milliseconds 800; break }
            if($memoryProcess.HasExited){ break }
            Start-Sleep -Milliseconds 600
        }
        if($memoryProcess -and -not $memoryProcess.HasExited){ $memoryProcess.Kill() }
    }else{
        # 微信尚未运行时保留原代理路径：监听就绪后再让用户打开微信。
        Ensure-Cert

        $existingListener = Get-NetTCPConnection -LocalPort $ProxyPort -State Listen -ErrorAction SilentlyContinue
        if($existingListener){
            throw "端口 $ProxyPort 已被其他程序占用，请关闭占用程序后重试。"
        }

        # 先启动并验证监听器，再修改系统代理，避免启动失败时造成短暂断网。
        $mitmArgs = @("$MitmRun","-s","$Script","-p","$ProxyPort",
                      "--set","confdir=$CaDir","--allow-hosts","^api\.zxs-bbs\.cn$",
                      "--set","termlog_verbosity=warn","--set","flow_detail=0")
        $mitmProcess = Start-Process -FilePath $PyExe -ArgumentList $mitmArgs -PassThru -NoNewWindow
        if(-not (Wait-ProxyReady $mitmProcess)){
            $exitDetail = if($mitmProcess.HasExited){ "退出码 $($mitmProcess.ExitCode)" } else { "等待监听超时" }
            throw "抓包代理启动失败（$exitDetail），请查看窗口中的错误信息。"
        }

        # 设代理（先存原状态）
        $savedProxy = Get-ProxyState
        Info "开启抓包代理（127.0.0.1:$ProxyPort）…"
        Set-Proxy "$ProxyHost`:$ProxyPort"
        $proxyChanged = $true

        Write-Host ""
        Ok   "准备就绪！现在请按下面做："
        Write-Host "    1) 打开【电脑版微信】" -ForegroundColor White
        Write-Host "    2) 搜索并打开【校园集市 / 赞噢校园集市】小程序" -ForegroundColor White
        Write-Host "    3) 在小程序里随便点一下，比如下拉刷新帖子列表" -ForegroundColor White
        Write-Host ""
        Info "正在等待抓取（最多 5 分钟）… 抓到后会自动提示，无需手动操作。"
        Write-Host ""

        # 轮询 .captured；最多 5 分钟
        $deadline = (Get-Date).AddMinutes(5)
        while((Get-Date) -lt $deadline){
            if(Test-Path $DoneFlag){ Start-Sleep -Milliseconds 800; break }
            if($mitmProcess.HasExited){ break }
            Start-Sleep -Milliseconds 600
        }

        if($mitmProcess -and -not $mitmProcess.HasExited){ $mitmProcess.Kill() }
    }

    Write-Host ""
    if(Test-Path $TokenFile){
        Ok "已成功获取！Token 已复制到剪贴板，并保存在："
        Write-Host "    $TokenFile" -ForegroundColor White
        Write-Host ""
        if(Test-Path $QrFile){
            Ok "导入二维码已在本机生成并打开，请用 App 扫描："
            Write-Host "    $QrFile" -ForegroundColor White
            Start-Process -FilePath $QrFile | Out-Null
        }
        Ok "也可以回到 App 的【集市设置】，从剪贴板粘贴。"
    }else{
        if($runningWeChat.Count -gt 0){
            Warn "已检测到微信，但没有在进程内存中识别到集市身份。"
            Write-Host "    请确认小程序已登录，并在帖子列表下拉刷新后保持页面打开。" -ForegroundColor White
            Write-Host "    若安全软件拦截了读取微信进程，请允许本工具后重试。" -ForegroundColor White
        }elseif(Test-Path $InvalidAuthFlag){
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
        }else{
            Warn "没有任何集市流量进入抓包代理。"
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
