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

function renderReleaseUnavailable() {
  document.querySelector("[data-version]").textContent = "暂不可用";
  document.querySelector("[data-size]").textContent = "--";
  document.querySelector("[data-hash]").textContent = "SHA-256 暂不可用";
  document.querySelector("[data-release-status]").textContent = "发布信息暂时不可用，请稍后重试。";
  document.querySelector("[data-copy-hash]").dataset.fullHash = "";
  document.querySelectorAll("[data-download]").forEach((link) => {
    link.removeAttribute("href");
    link.setAttribute("aria-disabled", "true");
  });
}

async function loadRelease() {
  try {
    const response = await fetch("/release.json", { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const release = await response.json();
    renderRelease(release);
  } catch {
    renderReleaseUnavailable();
  }
}

function setupHashCopy() {
  const button = document.querySelector("[data-copy-hash]");
  const toast = document.querySelector("[data-toast]");
  let timer;

  button.addEventListener("click", async () => {
    const hash = button.dataset.fullHash;
    if (!hash) {
      toast.textContent = "校验值暂不可用";
      toast.classList.add("show");
      clearTimeout(timer);
      timer = setTimeout(() => toast.classList.remove("show"), 1800);
      return;
    }
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

function setupScrollUi() {
  const header = document.querySelector("[data-header]");
  const progress = document.querySelector("[data-scroll-progress]");
  let scheduled = false;

  const update = () => {
    const scrollRange = document.documentElement.scrollHeight - window.innerHeight;
    const ratio = scrollRange > 0 ? Math.min(window.scrollY / scrollRange, 1) : 0;
    header.classList.toggle("scrolled", window.scrollY > 24);
    progress.style.transform = `scaleX(${ratio})`;
    scheduled = false;
  };

  const scheduleUpdate = () => {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(update);
  };

  update();
  window.addEventListener("scroll", scheduleUpdate, { passive: true });
  window.addEventListener("resize", scheduleUpdate, { passive: true });
}

function setupSectionNavigation() {
  const links = new Map(
    [...document.querySelectorAll("[data-nav-link]")].map((link) => [link.dataset.navLink, link])
  );
  const sections = [...links.keys()]
    .map((id) => document.getElementById(id))
    .filter(Boolean);

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      links.forEach((link, id) => {
        if (id === entry.target.id) link.setAttribute("aria-current", "true");
        else link.removeAttribute("aria-current");
      });
    });
  }, { rootMargin: "-30% 0px -60%", threshold: 0 });

  sections.forEach((section) => observer.observe(section));
}

function setupMotion() {
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  if (typeof Element.prototype.animate !== "function") return;

  const heroItems = document.querySelectorAll("[data-hero-item]");
  heroItems.forEach((item, index) => {
    item.animate(
      [
        { opacity: 0, transform: "translateY(16px)" },
        { opacity: 1, transform: "translateY(0)" }
      ],
      {
        duration: 560,
        delay: 90 + index * 85,
        easing: "cubic-bezier(0.22, 1, 0.36, 1)",
        fill: "backwards"
      }
    );
  });

  const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.animate(
        [
          { opacity: 0, transform: "translateY(22px)" },
          { opacity: 1, transform: "translateY(0)" }
        ],
        {
          duration: 620,
          easing: "cubic-bezier(0.22, 1, 0.36, 1)"
        }
      );
      revealObserver.unobserve(entry.target);
    });
  }, { threshold: 0.12 });

  document.querySelectorAll(".reveal").forEach((item) => revealObserver.observe(item));
}

loadRelease();
setupHashCopy();
setupScrollUi();
setupSectionNavigation();
setupMotion();
