import sbt._

object IzumiSite {
  val materialTheme: ParadoxMaterialTheme.Site = ParadoxMaterialTheme.Site(
    copyright = "7mind.io",
    repository = uri("https://github.com/7mind/izumi"),
    // Default dark theme: a static dump of Dark Reader (Dynamic mode) applied to the
    // white Material theme. Loaded after the Material stylesheets so its !important rules win.
    // Asset is staged via mdoc passthrough from src/main/tut/assets/stylesheets/darkreader.css.
    customStylesheet = Some("assets/stylesheets/darkreader.css"),
    // Visitor-facing toggle that disables the dark stylesheet at runtime via
    // link.disabled and persists the choice to localStorage. Provides a
    // fixed-position floating button; primarily intended as a visual-accessibility
    // override for users who need the lighter Material theme.
    customJavaScript = Some("assets/javascripts/scheme-switch.js"),
  )
}
