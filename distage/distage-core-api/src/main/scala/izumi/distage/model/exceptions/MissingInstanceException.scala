package izumi.distage.model.exceptions

import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.strings.IzString.*

class MissingInstanceException(message: String, val key: DIKey) extends DIException(message)

object MissingInstanceException {
  def format(target: DIKey, references: Set[(DIKey, EqualizedOperationOrigin)]): String = {
    s"""Instance is not available in the object graph: $target.
       |Required by refs: ${references.map { case (k, origin) => s"$k ${origin.value.render()}" }.niceList(prefix = "* ")}""".stripMargin
  }
}
