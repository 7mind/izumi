package izumi.distage.model.exceptions.runtime

import izumi.distage.model.definition.Binding
import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.strings.IzString.*

class MissingInstanceException(message: String, val key: DIKey) extends DIException(message)

object MissingInstanceException {
  def format(target: DIKey, references: Set[DIKey], similarSame: Set[Binding], similarSub: Set[Binding]): String = {
    // TODO: this representation is imperfect, we will have to change this once we have https://github.com/zio/izumi-reflect/issues/367 implemented
    import izumi.reflect.macrortti.LTTRenderables.Short._

    val refRepr = target.tpe.tag.ref.render()

    def formatRelevant(similar: Iterable[Binding], header: String): Seq[String] = {
      Option(similar)
        .filterNot(_.isEmpty)
        .map {
          bb =>
            s"$header: ${bb.map(b => s"${b.key} defined at ${b.origin}").niceList()}".stripMargin
        }
        .toSeq
    }
    val similarSameHints = formatRelevant(similarSame, "Found other bindings for the same type (did you forget to add or remove `@Id` annotation?)")
    val similarSubHints = formatRelevant(similarSub, "Found other bindings for related subtypes (did you mean to summon one of them?)")

    (Seq(
      s"""Instance is not available in the object graph: $target.
         |Required by refs: ${references.niceList(prefix = "* ")}""".stripMargin,
      s"""You may add missing binding with code alike to
         |
         |  make[$refRepr]""".stripMargin,
    ) ++ similarSameHints ++ similarSubHints).mkString("\n\n")
  }
}
