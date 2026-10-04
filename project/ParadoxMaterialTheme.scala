import java.net.{URI, URLEncoder}

import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport.paradoxMarkdownToHtml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import sbt.Keys._
import sbt._

import scala.jdk.CollectionConverters.*

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

  final case class Site(
    copyright: String,
    repository: URI,
    customStylesheet: Option[String],
    customJavaScript: Option[String],
  )

  private val textFont: String = "Roboto"
  private val codeFont: String = "Roboto Mono"

  def properties(site: Site): Map[String, String] = Map(
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
    "material.copyright" -> site.copyright,
    "material.repo" -> site.repository.toString,
    "material.repo.type" -> "github",
    "material.repo.name" -> site.repository.getPath.dropWhile(_ == '/'),
  ) ++ site.customStylesheet.map("material.custom.stylesheet" -> _) ++ site.customJavaScript.map("material.custom.javascript" -> _)

  /** Lunr index consumed by the theme's search box, served at `search/search_index.json`. */
  def searchIndexMapping: Def.Initialize[Task[(File, String)]] = Def.task {
    val index = (Compile / target).value / "paradox-material-theme" / "search_index.json"
    IO.write(index, indexJson((Compile / paradoxMarkdownToHtml).value))
    index -> "search/search_index.json"
  }

  private final case class Section(location: String, title: String, text: String)

  private val headerTags: Set[String] = Set("h1", "h2", "h3", "h4", "h5", "h6")

  private def indexJson(mappings: Seq[(File, String)]): String = {
    val pages = mappings.filter { case (_, path) => path.endsWith(".html") }.map(mapping => mapping._2 -> readSections(mapping))
    val unsearchable = pages.collect { case (path, None) => path }
    if (unsearchable.nonEmpty) {
      sys.error(unsearchable.sorted.mkString(s"Pages without the theme's `$searchableSelector` element, the search index would miss them:\n  ", "\n  ", ""))
    }
    pages
      .flatMap(_._2.toList.flatten)
      .map {
        section =>
          s"{${jsonString("location")}:${jsonString(section.location)}," +
          s"${jsonString("text")}:${jsonString(section.text)}," +
          s"${jsonString("title")}:${jsonString(section.title)}}"
      }
      .mkString(s"{${jsonString("docs")}:[", ",", "]}")
  }

  private val searchableSelector: String = "body .md-content__searchable"

  private def readSections(mapping: (File, String)): Option[Seq[Section]] = {
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

    val roots = doc.select(searchableSelector).asScala.toList
    if (roots.isEmpty) {
      None
    } else {
      val (sections, last) = roots.flatMap(_.children().asScala.toList).foldLeft((Vector.empty[Section], Section(location, docTitle, ""))) {
        case ((done, current), header) if headerTags(header.tagName) =>
          (done :+ current, Section(headerLocation(header), header.text, ""))
        case ((done, current), element) =>
          val text = if (current.text.isEmpty) element.text else current.text + "\n" + element.text
          (done, current.copy(text = text.trim))
      }
      Some(sections :+ last)
    }
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
