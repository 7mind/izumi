import sbt._

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.matching.Regex

/**
  * Resolves `#member` fragments of the documentation's links into the generated API pages.
  *
  * Scala 3 scaladoc gives every member page element the id `name-<hash>`, where the hash is
  * `((paramSigs.mkString + resultSig).hashCode % 4096).toHexString` of the member's erased signature
  * (`dotty.tools.scaladoc.tasty.SymOps.anchor`); it emits no name-only alias and has no option to change
  * the format. Such an id is stable for as long as the signature is, but opaque to write by hand and
  * silently stale once the signature changes, while mdoc runs with `--no-link-hygiene`, so nothing else
  * would notice.
  *
  * The documentation therefore links members by name (`@scaladoc[...](izumi.pkg.Type#member)`), and this
  * rewrites every `href` into the API subdirectory of the site mappings as follows:
  *   - a fragment equal to an id on the target page is kept as it is (that includes an explicit `name-hash`);
  *   - a fragment matching exactly one `name-<hex>` id is replaced by that id;
  *   - a missing page, an unknown member, or a name shared by several overloads fails the build with a
  *     message naming the link; overloads must be pinned with the explicit `name-hash` form.
  *
  * Rewritten pages are written next to the originals under `output`; untouched mappings pass through.
  * Case-class constructor fields get no element at all from scaladoc and so cannot be linked.
  */
object ScaladocAnchors {
  final case class Resolved(mappings: Seq[(File, String)])

  private val idPattern: Regex = """\bid="([^"]+)"""".r

  def resolve(mappings: Seq[(File, String)], apiSubdir: String, output: File, log: Logger): Resolved = {
    val apiPrefix = s"$apiSubdir/"
    val apiPages: Map[String, File] = mappings.collect { case (file, path) if path.startsWith(apiPrefix) => path.stripPrefix(apiPrefix) -> file }.toMap
    val ids = collection.mutable.Map.empty[String, Set[String]]
    def idsOf(page: String): Set[String] = ids.getOrElseUpdate(page, idPattern.findAllMatchIn(read(apiPages(page))).map(_.group(1)).toSet)

    val linkPattern: Regex = ("""href="([^"]*?)""" + Regex.quote(s"/$apiSubdir/") + """([^"#]+)#([^"]+)"""").r
    val problems = collection.mutable.ListBuffer.empty[String]
    var rewritten = 0

    val resolved = mappings.map {
      case (file, path) if path.endsWith(".html") && !path.startsWith(apiPrefix) =>
        val source = read(file)
        val target = linkPattern.replaceAllIn(
          source,
          m => {
            val (base, page, fragment) = (m.group(1), m.group(2), m.group(3))
            val replacement = resolveFragment(page, fragment, apiPages.contains(page), idsOf) match {
              case Right(id) => id
              case Left(problem) =>
                problems += s"$path -> $apiSubdir/$page#$fragment: $problem"
                fragment
            }
            if (replacement != fragment) rewritten += 1
            Regex.quoteReplacement(s"""href="$base/$apiSubdir/$page#$replacement"""")
          },
        )
        if (target == source) {
          (file, path)
        } else {
          val out = output / path
          IO.write(out, target, StandardCharsets.UTF_8)
          (out, path)
        }
      case mapping => mapping
    }

    if (problems.nonEmpty) {
      sys.error(problems.sorted.mkString("Unresolvable scaladoc links in the site:\n  ", "\n  ", ""))
    }
    log.info(s"Resolved $rewritten scaladoc member anchors")
    Resolved(resolved)
  }

  private def resolveFragment(page: String, fragment: String, pageExists: Boolean, idsOf: String => Set[String]): Either[String, String] = {
    if (!pageExists) {
      Left("no such API page")
    } else {
      val ids = idsOf(page)
      if (ids.contains(fragment)) {
        Right(fragment)
      } else {
        val candidates = ids.filter(id => id.startsWith(s"$fragment-") && id.drop(fragment.length + 1).forall(c => c.isDigit || ('a' to 'f').contains(c)))
        candidates.toList match {
          case Nil => Left("no such member")
          case single :: Nil => Right(single)
          case several => Left(s"ambiguous, ${several.size} overloads: use one of ${several.sorted.mkString(", ")}")
        }
      }
    }
  }

  private def read(file: File): String = new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
}
