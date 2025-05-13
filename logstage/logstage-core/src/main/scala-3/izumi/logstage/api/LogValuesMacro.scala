package izumi.logstage.api

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.{AbstractLogIO, AbstractLogger}

import scala.annotation.tailrec
import scala.quoted.*

object LogValuesMacro {
  def logValuesIO[F[_]: Type](
    using Quotes
  )(logger: Expr[AbstractLogIO[F]],
    level: Expr[Level],
    values: Expr[Seq[Any]],
  ): Expr[F[Unit]] = {
    val messageString = createMessageString(values)
    '{ $logger.log($level)(Message($messageString))(CodePositionMaterializer.materialize) }
  }

  def logValues(
    using Quotes
  )(logger: Expr[AbstractLogger],
    level: Expr[Level],
    values: Expr[Seq[Any]],
  ): Expr[Unit] = {
    val messageString = createMessageString(values)
    '{
      val pos = CodePositionMaterializer.materialize
      if ($logger.acceptable(pos.get, $level)) {
        $logger.unsafeLog(Log.Entry.create($level, Message($messageString))(pos))
      }
    }
  }

  private def createMessageString(
    using qctx: Quotes
  )(values: Expr[Seq[Any]]
  ): Expr[String] = {
    import qctx.reflect.*
    @tailrec
    def loopOverArgs(args: List[Expr[Any]], acc: Expr[String]): Expr[String] = {
      args match {
        case Nil => acc
        case head :: Nil => '{ $acc + $head }
        case head :: tail => loopOverArgs(tail, '{ $acc + $head + ", " })
      }
    }

    values match {
      case Varargs(args) => loopOverArgs(args.toList, '{ "" })
      case _ => report.errorAndAbort("Expected varargs parameter")
    }
  }
}
