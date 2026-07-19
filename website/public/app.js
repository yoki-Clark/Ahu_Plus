const fallbackRelease = {
  version: "2.2.2",
  fileSize: 8158167,
  sha256: "601A157A0A4F9608899B09D12E54C40D3941E6423273961BA5FA303A3242B409",
  downloadUrl: "https://gitee.com/yao-enqi/Ahu_Plus/releases/download/v2.2.2/app-arm64-v8a-release.apk",
  publishedAt: "2026-07-07T20:00:00+08:00"
};

const formatBytes = (bytes) => `${(bytes / 1024 / 1024).toFixed(1)} MB`;

function renderRelease(release) {
  const version = document.querySelector("[data-version]");
  const size = document.querySelector("[data-size]");
  const hash = document.querySelector("[data-hash]");
  const links = document.querySelectorAll("[data-download]");
  const status = document.querySelector("[data-release-status]");
  const date = new Intl.DateTimeFormat("zh-CN", { dateStyle: "long" }).format(new Date(release.publishedAt));

  version.textContent = `v${release.version}`;
  size.textContent = formatBytes(release.fileSize);
  hash.textContent = `SHA-256 ${release.sha256.slice(0, 8)}…${release.sha256.slice(-4)}`;
  links.forEach((link) => {
    link.href = release.downloadUrl;
    link.setAttribute("download", release.fileName || `ahu-plus-${release.version}.apk`);
  });
  status.textContent = `${date} 发布。下载后可使用上方 SHA-256 校验文件完整性。`;
  document.querySelector("[data-copy-hash]").dataset.fullHash = release.sha256;
}

async function loadRelease() {
  try {
    const response = await fetch("/release.json", { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const release = await response.json();
    renderRelease(release);
  } catch {
    renderRelease(fallbackRelease);
  }
}

function setupHashCopy() {
  const button = document.querySelector("[data-copy-hash]");
  const toast = document.querySelector("[data-toast]");
  let timer;

  button.addEventListener("click", async () => {
    const hash = button.dataset.fullHash || fallbackRelease.sha256;
    try {
      await navigator.clipboard.writeText(hash);
      toast.textContent = "已复制 SHA-256";
    } catch {
      toast.textContent = hash;
    }
    toast.classList.add("show");
    clearTimeout(timer);
    timer = setTimeout(() => toast.classList.remove("show"), 1800);
  });
}

function setupHeader() {
  const header = document.querySelector("[data-header]");
  const update = () => header.classList.toggle("scrolled", window.scrollY > 24);
  update();
  window.addEventListener("scroll", update, { passive: true });
}

loadRelease();
setupHashCopy();
setupHeader();
