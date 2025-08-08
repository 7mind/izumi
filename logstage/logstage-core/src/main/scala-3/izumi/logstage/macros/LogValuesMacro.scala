package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.{AbstractLogIO, AbstractLogger}

import scala.annotation.tailrec
import scala.quoted.*

object LogValuesMacro {

  def logValuesIO[F[_]: Type, Enc: Type](
    logger: Expr[AbstractLogIO[F]],
    level: Expr[Level],
    values: Expr[Seq[Any]],
  )(using Quotes): Expr[F[Unit]] = {
    val mode = EncodingModeExtractors.getModeFromType[Enc]
    val messageString = createMessageString(values)
    val message =  createMessageWithMode(mode, messageString)
    '{
      ${ logger }.log(${ level })(${ message })(using ${ CodePositionMaterializerMacro.getCodePositionMaterializer() })
    }
  }

  def logValues[Enc: Type](
    logger: Expr[AbstractLogger { type EncMode = Enc }],
    level: Expr[Level],
    values: Expr[Seq[Any]],
  )(using Quotes): Expr[Unit] = {
    val mode = EncodingModeExtractors.getModeFromType[Enc]
    val messageString = createMessageString(values)
    val message =  createMessageWithMode(mode, messageString)
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
        case head :: Nil => '{ $acc + ${ head } }
        case head :: tail => loopOverArgs(tail, '{ $acc + ${ head } + ", " })
      }
    }

    values match {
      case Varargs(args) => loopOverArgs(args.toList, '{ "" })
      case tree => report.errorAndAbort(s"Expected varargs parameter, but got code `${tree.show}` (raw: $tree)")
    }
  }

  private def createMessageWithMode(mode: EncodingMode, messageString: Expr[String])(using Quotes): Expr[Message] = {
    mode.fold(
      onRaw = '{ Message.raw(${ messageString }) }
    )(onStrictness = LogMessageMacro.message(messageString, _))
  }

  inline private def constToMode(value: "NonStrict" | "Strict" | "Raw"): EncodingMode = value match {
    case "NonStrict" => EncodingMode.NonStrict
    case "Strict" => EncodingMode.Strict
    case "Raw" => EncodingMode.Raw
  }

}
