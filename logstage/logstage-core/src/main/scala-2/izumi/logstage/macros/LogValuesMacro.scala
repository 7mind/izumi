package izumi.logstage.macros

import logstage.Log

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

class LogValuesMacro[C <: blackbox.Context](val c: C) {
  import c.universe.*
  def createMessage(
    values: Seq[c.Expr[Any]]
  ): c.Expr[Log.Message] = {
    val messageString = createMessageString(values)
    new LogMessageMacro0[c.type](c, false).logMessageMacro(c.Expr[String](messageString))
  }

  private def createMessageString(values: Seq[c.Expr[Any]]): Tree = {
    @tailrec
    def loop(args: List[Tree], acc: Tree): Tree = {
      args match {
        case Nil => acc
        case head :: Nil => q""" $acc + $head """
        case head :: tail => loop(tail, q""" $acc + $head + ", "  """)
      }
    }

    loop(values.map(_.tree).toList, q""" "" """)
  }
}
