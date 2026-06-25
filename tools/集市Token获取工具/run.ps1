# =====================================================================
#  校园集市 Token 一键获取工具 —— 主逻辑
#  由「获取集市Token.cmd」调起，普通用户无需直接运行本文件。
#
#  做的事：
#   1) 准备便携版 Python（工具目录内，绝不污染系统）
#   2) 给它装 mitmproxy（国内镜像加速）
#   3) 首次运行装 mitmproxy CA 证书到系统受信任根（需管理员）
#   4) 设置系统代理 -> 127.0.0.1:8080
#   5) 启动抓包，等用户在电脑微信打开集市小程序点一下
#   6) 抓到 token 后自动收尾；无论如何都会还原系统代理（finally 保证）
# =====================================================================

$ErrorActionPreference = "Stop"
$ProxyPort   = 8080
$ProxyHost   = "127.0.0.1"
$Root        = Split-Path -Parent $MyInvocation.MyCommand.Path
$PyDir       = Join-Path $Root "python"
$PortablePy  = Join-Path $PyDir "python.exe"   # 便携版路径（兜底用）
$PyExe       = $PortablePy                      # 实际使用的 python；Ensure-Runtime 可能改指系统 Python
$Script      = Join-Path $Root "catch_token.py"
$DoneFlag    = Join-Path $Root ".captured"
$TokenFile   = Join-Path $Root "我的集市Token.txt"
$CertFile    = Join-Path $env:USERPROFILE ".mitmproxy\mitmproxy-ca-cert.cer"

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
        try{
            $callArgs = $c.pre + @("-c", $probe)
            $out = (& $c.exe @callArgs 2>$null | Out-String).Trim()
            if($LASTEXITCODE -eq 0 -and $out -and (Test-Path $out) -and $out -notmatch 'WindowsApps'){
                return $out
            }
        }catch{}
    }
    return $null
}

function Test-HasMitm($py){
    return (& $py -c "import mitmproxy,sys;sys.stdout.write('ok')" 2>$null) -eq "ok"
}

# 决定用哪个 Python：能用系统现成的就用，最后才下载便携版。
function Ensure-Runtime{
    $sys = Resolve-SystemPython
    if($sys){
        if(Test-HasMitm $sys){
            $script:PyExe = $sys
            Ok "检测到系统已装 Python + mitmproxy，直接使用（免下载）。"
            return
        }
        # 有 Python 没组件：装到当前用户目录（--user，不动全局 site-packages）
        Info "检测到系统 Python，正在为其安装抓包组件 mitmproxy（装到当前用户，不影响全局）…"
        & $sys -m pip install --user mitmproxy -i $PipIndex --no-warn-script-location
        if($LASTEXITCODE -eq 0 -and (Test-HasMitm $sys)){
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
    & $PortablePy $getpip --no-warn-script-location -i $PipIndex | Out-Null
    Remove-Item $getpip -Force
    $script:PyExe = $PortablePy
    Ok "Python 环境就绪。"
}

# ---- 2. 便携 Python 装 mitmproxy ----
function Ensure-PortableMitmproxy{
    if(Test-HasMitm $PortablePy){ return }
    Info "安装抓包组件 mitmproxy（首次较慢，请耐心等待）…"
    & $PortablePy -m pip install mitmproxy -i $PipIndex --no-warn-script-location
    if($LASTEXITCODE -ne 0){ throw "mitmproxy 安装失败，请检查网络后重试。" }
    Ok "抓包组件就绪。"
}

# ---- 3. 生成并安装 CA 证书 ----
function Ensure-Cert{
    if(-not (Test-Path $CertFile)){
        Info "生成抓包证书…"
        # 真正起一次 mitmdump 让它在 ~/.mitmproxy 生成 CA 证书，4 秒后杀掉
        $p2 = Start-Process -FilePath $PyExe `
            -ArgumentList "$MitmRun","-p","$ProxyPort","--set","termlog_verbosity=error" `
            -PassThru -WindowStyle Hidden
        Start-Sleep -Seconds 4
        if($p2 -and -not $p2.HasExited){ $p2.Kill() }
        Start-Sleep -Seconds 1
    }
    if(-not (Test-Path $CertFile)){
        Warn "证书尚未生成，稍后抓包启动时会自动生成并安装。"
        return
    }
    # 是否已在受信任根
    $installed = certutil -store Root 2>$null | Select-String "mitmproxy"
    if($installed){ Ok "抓包证书已信任。"; return }
    Info "安装抓包证书到系统受信任根（会弹一次确认）…"
    certutil -addstore -f Root "$CertFile" | Out-Null
    if($LASTEXITCODE -eq 0){ Ok "证书安装成功。" }
    else{ Warn "证书安装未成功，可能影响抓包；可继续尝试。" }
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
    Ensure-Cert

    # 删旧结果
    if(Test-Path $DoneFlag){ Remove-Item $DoneFlag -Force }
    if(Test-Path $TokenFile){ Remove-Item $TokenFile -Force }

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

    # 启动抓包（前台子进程，输出直达本窗口）
    $mitmArgs = @("$MitmRun","-s","$Script","-p","$ProxyPort",
                  "--set","termlog_verbosity=error","--set","flow_detail=0")
    $proc = Start-Process -FilePath $PyExe -ArgumentList $mitmArgs -PassThru -NoNewWindow

    # 轮询 .captured；最多 5 分钟
    $deadline = (Get-Date).AddMinutes(5)
    while((Get-Date) -lt $deadline){
        if(Test-Path $DoneFlag){ Start-Sleep -Milliseconds 800; break }
        if($proc.HasExited){ break }
        Start-Sleep -Milliseconds 600
    }

    if($proc -and -not $proc.HasExited){ $proc.Kill() }

    Write-Host ""
    if(Test-Path $TokenFile){
        Ok "已成功获取！Token 已复制到剪贴板，并保存在："
        Write-Host "    $TokenFile" -ForegroundColor White
        Write-Host ""
        Ok "回到 App 的【集市设置】，粘贴即可。"
    }else{
        Warn "这次没抓到。常见原因："
        Write-Host "    - 还没在电脑微信里打开集市小程序，或没点任何页面" -ForegroundColor White
        Write-Host "    - 杀毒软件拦截了代理/证书（请临时放行后重试）" -ForegroundColor White
        Write-Host "    - 还没登录集市（请先在小程序里完成登录）" -ForegroundColor White
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
    # 关键：无论成功失败，都还原系统代理，绝不让用户断网
    if($proxyChanged){
        Info "正在还原系统网络设置…"
        Restore-Proxy $savedProxy
        Ok "网络设置已还原。"
    }
    Write-Host ""
    Write-Host "  按任意键关闭窗口…" -ForegroundColor DarkGray
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}
