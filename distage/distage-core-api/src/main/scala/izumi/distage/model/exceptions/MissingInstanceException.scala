package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.strings.IzString._

class MissingInstanceException(message: String, val key: DIKey) extends DIException(message)

object MissingInstanceException {
  def format(target: DIKey, references: Set[DIKey]): String = {
    s"""Instance is not available in the object graph: $target.
       |Required by refs:${references.niceList(prefix = "* ")}""".stripMargin
  }
}


