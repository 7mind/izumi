/*
 * Color-scheme toggle for the izumi microsite.
 *
 * The dark theme (assets/stylesheets/darkreader.css) is the default for all
 * visitors. The toggle button itself is rendered by the page template
 * (paradox-overlay/partials/header.st) inside the header, so this script
 * only needs to wire its click handler and refresh aria-label/title for
 * screen readers when the scheme changes.
 *
 * The actual gating is performed by an inline script in <head> (overlaid via
 * paradox-overlay/page.st) that flips the dark <link>'s `media` attribute
 * synchronously, before the browser paints, based on
 * localStorage["izumi-color-scheme"]. This script only persists user intent
 * via the same key and updates `link.media` live on click.
 *
 * Storage:
 *   localStorage["izumi-color-scheme"] = "light"  -> opted out of dark
 *   localStorage["izumi-color-scheme"] absent     -> default (dark)
 * The "dark" value is never written; absence means "use the site default,"
 * which is dark. Clearing the entry is how we return to default state.
 */
(function () {
  var STORAGE_KEY = "izumi-color-scheme";
  var DARK_LINK_ID = "darkreader-css";
  var BUTTON_ID = "scheme-switch";

  function readScheme() {
    try {
      return localStorage.getItem(STORAGE_KEY) === "light" ? "light" : "dark";
    } catch (e) {
      return "dark";
    }
  }

  function writeScheme(scheme) {
    try {
      if (scheme === "dark") localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, scheme);
    } catch (e) {
      /* private mode / disabled storage — accept the loss silently */
    }
  }

  function applyScheme(scheme) {
    var link = document.getElementById(DARK_LINK_ID);
    if (link) link.media = scheme === "light" ? "not all" : "all";
  }

  function attachToggle() {
    var btn = document.getElementById(BUTTON_ID);
    if (!btn) return;

    function refresh() {
      // Icon stays as ☀ in both states. The aria-label and title describe
      // the action, so screen readers and tooltip-readers know what
      // clicking will do regardless of which scheme is active.
      var label =
        readScheme() === "dark"
          ? "Switch to light color scheme"
          : "Switch to dark color scheme";
      btn.setAttribute("aria-label", label);
      btn.title = label;
    }

    btn.addEventListener("click", function () {
      var next = readScheme() === "dark" ? "light" : "dark";
      writeScheme(next);
      applyScheme(next);
      refresh();
    });

    refresh();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", attachToggle);
  } else {
    attachToggle();
  }
})();
