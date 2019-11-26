package izumi.fundamentals.reflection

import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object TreeUtil {
  def stringLiteral(c: blackbox.Context)(u: Universe)(tree: u.Tree): String = {
    tree.collect {
      case l: u.Literal @unchecked if l.value.value.isInstanceOf[String] => l.value.value.asInstanceOf[String] // avoid unchecked warning
    }.headOption.getOrElse(c.abort(c.enclosingPosition, "must use string literal"))
  }
}
