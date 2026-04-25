/*
 * Color-scheme toggle for the izumi microsite.
 *
 * The dark theme (assets/stylesheets/darkreader.css) loads unconditionally as
 * the default for all visitors. This script lets a user opt out — typically
 * for visual-accessibility reasons — and remembers the choice across page
 * loads via localStorage.
 *
 * The opt-out works by setting `link.disabled = true` on the darkreader
 * <link> element. When disabled, the browser ignores its rules and the
 * underlying white Material theme shows through.
 *
 * A returning visitor who chose "light" will see a brief flash of dark
 * before this script runs and disables the link, because paradox-material's
 * page template loads custom JS at end-of-body. Eliminating the flash
 * entirely would require a paradoxOverlayDirectories template override to
 * place an inline script at the top of <head>; not done here.
 */
(function () {
  var STORAGE_KEY = "izumi-color-scheme";
  var DARK_LINK_SELECTOR = 'link[href$="darkreader.css"]';

  function read() {
    try {
      return localStorage.getItem(STORAGE_KEY) || "dark";
    } catch (e) {
      return "dark";
    }
  }

  function write(scheme) {
    try {
      if (scheme === "dark") localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, scheme);
    } catch (e) {
      /* private mode / disabled storage — accept the loss */
    }
  }

  function applyScheme(scheme) {
    document.documentElement.dataset.scheme = scheme;
    var link = document.querySelector(DARK_LINK_SELECTOR);
    if (link) link.disabled = scheme === "light";
  }

  // Apply as early as possible. If the <link> isn't in the DOM yet (this
  // script may execute before the head finishes parsing), watch for it.
  var initial = read();
  applyScheme(initial);
  if (initial === "light" && !document.querySelector(DARK_LINK_SELECTOR)) {
    var observer = new MutationObserver(function () {
      var link = document.querySelector(DARK_LINK_SELECTOR);
      if (link) {
        link.disabled = true;
        observer.disconnect();
      }
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
    document.addEventListener("DOMContentLoaded", function () {
      observer.disconnect();
    });
  }

  // Inject the toggle button once the DOM is ready.
  function injectButton() {
    if (document.getElementById("scheme-switch")) return;

    var btn = document.createElement("button");
    btn.id = "scheme-switch";
    btn.type = "button";

    function refresh() {
      var dark = read() === "dark";
      btn.textContent = dark ? "☀" : "☾"; // ☀ : ☾
      btn.setAttribute(
        "aria-label",
        dark ? "Switch to light color scheme" : "Switch to dark color scheme"
      );
      btn.title = dark
        ? "Switch to light color scheme"
        : "Switch to dark color scheme";
    }

    btn.addEventListener("click", function () {
      var next = read() === "dark" ? "light" : "dark";
      write(next);
      applyScheme(next);
      refresh();
    });

    refresh();

    // Inline styles keep the button independent of either color scheme;
    // semi-transparent neutral background reads on both dark and white.
    var s = btn.style;
    s.position = "fixed";
    s.right = "20px";
    s.bottom = "20px";
    s.width = "44px";
    s.height = "44px";
    s.borderRadius = "50%";
    s.border = "1px solid rgba(255,255,255,0.2)";
    s.backgroundColor = "rgba(60,60,60,0.85)";
    s.color = "#ffffff";
    s.fontSize = "20px";
    s.lineHeight = "42px";
    s.padding = "0";
    s.cursor = "pointer";
    s.zIndex = "1000";
    s.boxShadow = "0 2px 8px rgba(0,0,0,0.35)";

    document.body.appendChild(btn);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", injectButton);
  } else {
    injectButton();
  }
})();
