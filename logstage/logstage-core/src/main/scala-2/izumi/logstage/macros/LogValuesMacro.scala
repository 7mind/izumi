package izumi.logstage.macros

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object LogValuesMacro {
  def createMessageString(c: blackbox.Context)(values: Seq[c.Expr[Any]]): c.Expr[String] = {
    import c.universe.*

    @tailrec
    def loop(args: List[c.Tree], acc: c.Tree): c.Tree = {
      args match {
        case Nil => acc
        case head :: Nil => q""" $acc + $head """
        case head :: tail => loop(tail, q""" $acc + $head + ", "  """)
      }
    }

    c.Expr[String](loop(values.map(_.tree).toList, q""" "" """))
  }
}
