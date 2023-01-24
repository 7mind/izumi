package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.strings.IzString.*

class MissingInstanceException(message: String, val key: DIKey) extends DIException(message)

object MissingInstanceException {
  def format(target: DIKey, references: Set[DIKey]): String = {
    // TODO: this representation is imperfect, we will have to change this once we have https://github.com/zio/izumi-reflect/issues/367 implemented
    import izumi.reflect.macrortti.LTTRenderables.Short._

    val refRepr = target.tpe.tag.ref.render()

    s"""Instance is not available in the object graph: $target.
       |Required by refs: ${references.niceList(prefix = "* ")}
       |
       |You may add missing binding with code alike to
       |
       |  make[$refRepr]
       |""".stripMargin
  }
}
