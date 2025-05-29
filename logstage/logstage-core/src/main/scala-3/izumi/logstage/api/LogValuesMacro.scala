package izumi.logstage.api

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.{AbstractLogIO, AbstractLogger}
import izumi.logstage.macros.EncodingMode

import scala.annotation.tailrec
import scala.quoted.*

object LogValuesMacro {

  def logValuesIO[F[_]: Type](
    logger: Expr[AbstractLogIO[F]],
    level: Expr[Level],
    values: Expr[Seq[Any]],
    mode: 1 | 2 | 3
  )(using Quotes): Expr[F[Unit]] = {
    val messageString = createMessageString(values)
    val message =  createMessageWithMode(intToMode(mode), messageString)
    '{
      ${ logger }.log(${ level })(
        ${ message }
      )(using ${ CodePositionMaterializerMacro.getCodePositionMaterializer() })
    }
  }

  def logValues(
    logger: Expr[AbstractLogger],
    level: Expr[Level],
    values: Expr[Seq[Any]],
    mode: 1 | 2 | 3,
  )(using Quotes): Expr[Unit] = {
    val messageString = createMessageString(values)
    val message =  createMessageWithMode(intToMode(mode), messageString)
    '{
      val pos = ${ CodePositionMaterializerMacro.getCodePositionMaterializer() }
      if (${ logger }.acceptable(pos.get, ${ level })) {
        ${ logger }.unsafeLog(
          Log.Entry.create(
            ${ level },
            ${ message },
          )(using pos)
        )
      }
    }
  }

  private def createMessageString(
    values: Expr[Seq[Any]]
  )(using qctx: Quotes): Expr[String] = {
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

  private def createMessageWithMode(mode: EncodingMode, messageString: Expr[String])(using Quotes): Expr[Message] = {
    mode.fold(
      onRaw = '{ Message.raw(${ messageString }) }
    )(onStrictness = LogMessageMacro.message(messageString, _))
  }

  inline private def intToMode(value: 1 | 2 | 3): EncodingMode = value match {
    case 1 => EncodingMode.NonStrict
    case 2 => EncodingMode.Strict
    case 3 => EncodingMode.Raw
  }

}
