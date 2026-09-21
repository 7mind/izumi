import java.net.{URI, URLEncoder}

import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport.paradoxMarkdownToHtml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import sbt.Keys._
import sbt._

/**
  * Inlined replacement for the `sbt-paradox-material-theme` sbt plugin.
  *
  * The theme itself is an ordinary artifact consumed through `paradoxTheme`; the plugin only
  * translated a config object into `material.*` paradox properties and generated the search
  * index. Both are reproduced here so that the microsite does not depend on an sbt plugin.
  *
  * @see https://github.com/sbt/sbt-paradox-material-theme
  */
object ParadoxMaterialTheme {

  val artifact: ModuleID = "com.github.sbt" % "paradox-material-theme" % V.paradox_material_theme

  private val repository: URI = uri("https://github.com/7mind/izumi")
  private val textFont: String = "Roboto"
  private val codeFont: String = "Roboto Mono"

  lazy val properties: Map[String, String] = Map(
    "material.theme.version" -> V.paradox_material_theme,
    // defaults of the plugin's `ParadoxMaterialTheme()`
    "material.font.text" -> textFont,
    "material.font.text.url" -> URLEncoder.encode(textFont, "UTF-8"),
    "material.font.code" -> codeFont,
    "material.font.code.url" -> URLEncoder.encode(codeFont, "UTF-8"),
    "material.logo.icon" -> "local_library",
    "material.favicon" -> "assets/images/favicon.png",
    "material.search" -> "true",
    "material.search.tokenizer" -> "[\\s\\-]+",
    "material.copyright" -> "7mind.io",
    "material.repo" -> repository.toString,
    "material.repo.type" -> "github",
    "material.repo.name" -> repository.getPath.dropWhile(_ == '/'),
    // Default dark theme: a static dump of Dark Reader (Dynamic mode) applied to the
    // white Material theme. Loaded after the Material stylesheets so its !important rules win.
    // Asset is staged via mdoc passthrough from src/main/tut/assets/stylesheets/darkreader.css.
    "material.custom.stylesheet" -> "assets/stylesheets/darkreader.css",
    // Visitor-facing toggle that disables the dark stylesheet at runtime via
    // link.disabled and persists the choice to localStorage. Provides a
    // fixed-position floating button; primarily intended as a visual-accessibility
    // override for users who need the lighter Material theme.
    "material.custom.javascript" -> "assets/javascripts/scheme-switch.js",
  )

  /** Lunr index consumed by the theme's search box, served at `search/search_index.json`. */
  def searchIndexMapping: Def.Initialize[Task[(File, String)]] = Def.task {
    val index = (Compile / target).value / "paradox-material-theme" / "search_index.json"
    IO.write(index, indexJson((Compile / paradoxMarkdownToHtml).value))
    index -> "search/search_index.json"
  }

  private final case class Section(location: String, title: String, text: String)

  private val headerTags: Set[String] = Set("h1", "h2", "h3", "h4", "h5", "h6")

  private def indexJson(mappings: Seq[(File, String)]): String = {
    mappings
      .flatMap(readSections)
      .map {
        section =>
          s"{${jsonString("location")}:${jsonString(section.location)}," +
          s"${jsonString("text")}:${jsonString(section.text)}," +
          s"${jsonString("title")}:${jsonString(section.title)}}"
      }
      .mkString(s"{${jsonString("docs")}:[", ",", "]}")
  }

  private def readSections(mapping: (File, String)): Seq[Section] = {
    val (file, location) = mapping
    val doc = Jsoup.parse(file, "UTF-8")

    val docTitle = {
      val title = doc.select("head title").text()
      val separator = title.lastIndexOf(" · ")
      if (separator > 0) title.substring(0, separator) else title
    }

    def headerLocation(header: Element): String = {
      val anchor = header.select("a[name]").first()
      if (anchor == null) location else location + "#" + anchor.attr("name")
    }

    def processElements(section: Section, elements: List[Element]): Seq[Section] = {
      elements match {
        case header :: tail if headerTags(header.tagName) =>
          Vector(section) ++ processElements(Section(headerLocation(header), header.text, ""), tail)
        case element :: tail =>
          val text = if (section.text.isEmpty) element.text else section.text + "\n" + element.text
          processElements(section.copy(text = text.trim), tail)
        case Nil =>
          Vector(section)
      }
    }

    val searchable = elements(doc.select("body .md-content__searchable")).flatMap(e => elements(e.children()))
    processElements(Section(location, docTitle, ""), searchable)
  }

  // avoids `JavaConverters`/`jdk.CollectionConverters`, which are spelled differently across the
  // Scala versions the metabuild may be compiled with
  private def elements(elements: Elements): List[Element] = {
    val builder = List.newBuilder[Element]
    val iterator = elements.iterator()
    while (iterator.hasNext) {
      builder += iterator.next()
    }
    builder.result()
  }

  private def jsonString(value: String): String = {
    val sb = new StringBuilder(value.length + 2)
    sb.append('"')
    value.foreach {
      case '"' => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\b' => sb.append("\\b")
      case '\f' => sb.append("\\f")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case c if c < 0x20 => sb.append("\\u%04x".format(c.toInt))
      case c => sb.append(c)
    }
    sb.append('"')
    sb.result()
  }
}
