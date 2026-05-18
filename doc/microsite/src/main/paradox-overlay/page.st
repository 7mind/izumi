$!
  Copyright (c) 2016-2018 Martin Donath <martin.donath@squidfunk.com>
  Copyright (c) 2017-2018 Jonas Fonseca <jonas.fonseca@gmail.com>

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to
  deal in the Software without restriction, including without limitation the
  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
  sell copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
  IN THE SOFTWARE.
!$
<!DOCTYPE html>
<html lang="$page.properties.("material.language"); null="en"$" class="no-js">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta http-equiv="x-ua-compatible" content="ie=edge">
    <meta name="description" content="$page.properties.("project.description")$">
    <meta name="generator" content="Paradox, paradox-material-theme=$page.properties.("material.theme.version")$, mkdocs-material=3.0.3">

    $partials/language()$

    $ if (page.properties.("description")) $
      <meta name="description" content="$page.properties.("description")$">
    $ elseif (page.properties.("project.description")) $
      <meta name="description" content="$page.properties.("project.description")$">
    $ endif $
    $ if (page.properties.("material.canonical.url")) $
      <link rel="canonical" href="$page.properties.("material.canonical.url")$">
    $ endif $
    $ if (page.properties.("author")) $
      <meta name="author" content="$page.properties.("author")$">
    $ elseif (page.properties.("material.author")) $
      <meta name="author" content="$page.properties.("site.author")$">
    $ endif $
    <link rel="shortcut icon" href="$page.base$$page.properties.("material.favicon")$">
    $ if (page.properties.("title")) $
      <title>$page.properties.("title")$</title>
    $ elseif (page.title) $
      <title>$page.title$$ if (!page.home.active) $ · $page.home.title$$ endif $</title>
    $ else $
      <title>$page.home.title$</title>
    $ endif $
    <link rel="stylesheet" href="$page.base$assets/stylesheets/application.451f80e5.css">
    $ if (page.properties.("material.color.primary")) $
      <link rel="stylesheet" href="$page.base$assets/stylesheets/application-palette.22915126.css">
      $ if (page.properties.("material.color.primary.theme")) $
        <meta name="theme-color" content="$page.properties.("material.color.primary.theme")$" />
      $ endif $
    $ endif $
    <link rel="stylesheet" href="$page.base$lib/material__tabs/dist/mdc.tabs.min.css">
    <link rel="stylesheet" href="$page.base$lib/prettify/prettify.css">
    <script src="$page.base$assets/javascripts/modernizr.1aa3b519.js"></script>
    $ if (page.properties.("material.font.text")) $
      <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=$page.properties.("material.font.text.url")$:300,400,400i,700|$page.properties.("material.font.code.url")$">
      <style>
        body,input{font-family:"$page.properties.("material.font.text")$","Helvetica Neue",Helvetica,Arial,sans-serif}
        code,kbd,pre{font-family:"$page.properties.("material.font.code")$","Courier New",Courier,monospace}
      </style>
    $ endif $
    <link rel="stylesheet" href="$page.base$assets/fonts/font-awesome.css">
    <link rel="stylesheet" href="$page.base$assets/fonts/material-icons.css">
    <link rel="stylesheet" href="$page.base$assets/stylesheets/paradox-material-theme.css">
    $ if (page.properties.("material.custom.stylesheet")) $
      $!
        Custom (dark) stylesheet emitted with id="darkreader-css" so the
        runtime toggle can find it, and a default media= attribute that
        gates it on color-scheme preference for visitors WITHOUT JavaScript.
        Default state: dark applies to dark/no-pref OS, white shows through on
        explicit-light OS. JS-enabled visitors get the inline script below,
        which overrides this default with their stored preference (or the
        site default of "always dark").
      !$
      <link rel="stylesheet"
            id="darkreader-css"
            href="$page.base$$page.properties.("material.custom.stylesheet")$"
            media="not (prefers-color-scheme: light)">
      <script>
        /* Synchronous: runs after the <link> is parsed but before the browser
         * applies its rules to the painted DOM, so adjusting media here
         * eliminates any flash-of-wrong-theme on repeat visits. */
        (function () {
          var link = document.getElementById("darkreader-css");
          if (!link) return;
          var stored = null;
          try { stored = localStorage.getItem("izumi-color-scheme"); } catch (e) {}
          /* "light" => disable (force original white theme).
           * Anything else (including null) => force dark, overriding the OS
           * preference. This makes dark the JS-on default for everyone. */
          link.media = (stored === "light") ? "not all" : "all";
        })();
      </script>
    $ endif $
    <style>
      /* Theme-toggle layout (see partials/header.st).
       *
       * .md-flex is `display: table` upstream — its children are table-cells
       * whose heights contribute to the row's height. The title cell on
       * sub-pages uses absolute-positioned .md-header-nav__topic spans (each
       * line-height: 4.8rem), which need the row to be at least 4.8rem tall
       * to render fully. So at wide widths we KEEP the table layout intact:
       * the toggle goes in its own .md-flex__cell sibling, no flex on cells.
       *
       * Wide (>=60em): source-cell sits to the right of search (DOM order),
       * with the toggle-cell after it. The default Material rule gives
       * .md-header-nav__source `width: 23rem` and the github link doesn't
       * fill that — the toggle's own cell appears just after, in line.
       *
       * Narrow (<60em): we switch the row to flex (with overrides for the
       * cell display modes) so we can use `order: 1` on the search cell to
       * push it past source-cell + toggle-cell. The source segment, which
       * is normally display:none here, is reshown as icon-only by hiding
       * .md-source__repository.
       */
      .md-scheme-switch {
        /* The button reuses .md-header-nav__button for padding/cursor.
         * Override font-family — without this, .md-icon (which our button
         * also has) forces "Material Icons" font, which has no ☀ glyph at
         * U+2600 and renders an empty/tofu placeholder. */
        background: transparent;
        border: 0;
        font-family: inherit;
        /* Smaller than the 2.4rem pictographic Material Icons baseline because
         * Roboto's ☀ glyph fills its em-box more densely than icon fonts;
         * 1.8rem visually matches the search/github icons next to it. */
        font-size: 1.8rem;
        line-height: 1;
        color: inherit;
      }
      @media only screen and (max-width: 59.9375em) {
        /* Switch the row to flex so we can reorder via `order`. Force the
         * row's height to match .md-header (4.8rem upstream) so the title
         * cell — whose .md-header-nav__topic children are position:absolute
         * with line-height 4.8rem — has room. Without min-height the row
         * collapses to the icon buttons' ~4rem and topics overflow downward
         * on sub-pages. */
        .md-flex {
          display: flex;
          align-items: stretch;
          min-height: 4.8rem;
        }
        .md-flex__cell {
          display: flex;
          align-items: center;
          width: auto;
          /* Belt-and-suspenders: also pin min-height on each cell so they
           * are guaranteed to provide the 4.8rem the absolute-positioned
           * .md-header-nav__topic spans need on sub-pages, regardless of
           * how align-items: stretch propagates from the container. */
          min-height: 4.8rem;
        }
        .md-flex__cell--stretch {
          flex: 1 1 0;
          min-width: 0;
        }
        .md-flex__ellipsis {
          display: block;
          flex: 1 1 0;
          min-width: 0;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          /* The title element has no in-flow content (topics are absolute),
           * so it would collapse to height 0 without an explicit size. The
           * absolute topics position relative to the cell, but giving the
           * title 100% of cell height keeps any cascade/inheritance that
           * depends on title's own box happy. */
          height: 4.8rem;
        }
        .md-header-nav__search-cell {
          order: 1;
        }
        /* Show github source as icon-only. */
        .md-header-nav__source-cell .md-header-nav__source {
          display: flex;
          width: auto;
          max-width: none;
          margin: 0;
          padding: 0;
        }
        .md-header-nav__source-cell .md-source {
          padding: 0;
          height: auto;
          line-height: 1;
        }
        .md-header-nav__source-cell .md-source__repository,
        .md-header-nav__source-cell .md-source__facts {
          display: none;
        }
      }
    </style>
  </head>
  <body
    $ if (page.properties.("material.color.primary")) $
      data-md-color-primary="$page.properties.("material.color.primary")$"
    $ endif $
    $ if (page.properties.("material.color.accent")) $
      data-md-color-accent="$page.properties.("material.color.accent")$"
    $ endif $
  >
    <input class="md-toggle" data-md-toggle="drawer" type="checkbox" id="__drawer" autocomplete="off">
    <input class="md-toggle" data-md-toggle="search" type="checkbox" id="__search" autocomplete="off">
    <label class="md-overlay" data-md-component="overlay" for="__drawer"></label>
    $! FIXME: Skip to content (0e1850280a1c25d3bc697419288d97582dacbbc3) !$
    $partials/header()$
    <div class="md-container">
      $ if (page.properties.("material.hero")) $
        $partials/hero()$
      $ endif $
      <main class="md-main">
        <div class="md-main__inner md-grid" data-md-component="container">
          <div class="md-sidebar md-sidebar--primary" data-md-component="navigation">
            <div class="md-sidebar__scrollwrap">
              <div class="md-sidebar__inner">
                $partials/nav()$
              </div>
            </div>
          </div>
          $ if (page.subheaders) $
            <div class="md-sidebar md-sidebar--secondary" data-md-component="toc">
              <div class="md-sidebar__scrollwrap">
                <div class="md-sidebar__inner">
                  $partials/toc()$
                </div>
              </div>
            </div>
          $ endif $
          <div class="md-content">
            <article class="md-content__inner md-typeset">
              <div class="md-content__searchable">
                $page.content$
              </div>
              $ if (page.source_url) $
                <div>
                  <a href="$page.source_url$" title="Edit this page" class="md-source-file md-edit">
                    Edit this page
                  </a>
                </div>
              $ endif $
              <div class="print-only">
                <span class="md-source-file md-version">
                  $page.properties.("project.version.short")$
                </span>
              </div>
            </article>
          </div>
        </div>
      </main>
      $partials/footer()$
    </div>
    <script src="$page.base$assets/javascripts/application.583bbe55.js"></script>
    <script src="$page.base$assets/javascripts/paradox-material-theme.js"></script>
    $!

      The ending "." enables search to work without providing the site URL, since the
      theme JavaScript fetches the index by concatenating the base URL to an absolute
      path: "/search/search_index.json".

      It originally was "./" but this breaks previewSite because it leads to a double
      slash when fetching the index, i.e. ".//search/...". To complete the workaround
      all locations in the search index now start with a "/" so the JavaScript can
      safely do: `url.base + doc.location`.

    !$
    <script>app.initialize({version:"0.17",url:{base:"$page.base$."}})</script>
    <script type="text/javascript" src="$page.base$lib/prettify/prettify.js"></script>
    <script type="text/javascript" src="$page.base$lib/prettify/lang-scala.js"></script>
    <script type="text/javascript">
      document.addEventListener("DOMContentLoaded", function(event) {
        window.prettyPrint && prettyPrint();
      });
    </script>
    $ if (page.properties.("material.custom.javascript")) $
      <script src="$page.base$$page.properties.("material.custom.javascript")$"></script>
    $ endif $
    $ if (page.properties.("material.google.analytics")) $
      $partials/integrations/analytics()$
    $ endif $
  </body>
</html>
