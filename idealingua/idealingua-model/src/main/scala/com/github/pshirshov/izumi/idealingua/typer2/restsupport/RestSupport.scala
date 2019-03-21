package com.github.pshirshov.izumi.idealingua.typer2.restsupport

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.TargetInterface
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.{Ts2Builder, WarnLogger}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzMethod, IzType, T2Fail, T2Warn}
import com.github.pshirshov.izumi.functional.IzEither._

class RestSupport() extends WarnLogger.WarnLoggerImpl {
  import RestSupport._

  def translateRestAnnos(result: Ts2Builder.Output): Either[List[T2Fail], List[(ProcessedOp, List[T2Warn])]] = {
    result.ts.types.map {
      case p: ProcessedOp.Exported =>
        p.member match {
          case interface: TargetInterface =>
            for {
              r <- processRest(interface)
            } yield {
              (p.copy(member = r._1), r._2)
            }
          case o => Right((p, List.empty))
        }
      case p: ProcessedOp.Imported =>
        p.member match {
          case interface: TargetInterface =>
            for {
              r <- processRest(interface)
            } yield {
              (p.copy(member = r._1), r._2)
            }
          case o => Right((p, List.empty))
        }
    }.biAggregate
  }

  private def processRest(i: TargetInterface): Either[List[T2Fail], (TargetInterface, List[T2Warn])] = {
    i match {
      case b: IzType.Buzzer =>
        for {
          methods <- b.methods.map(process).biAggregate
        } yield {
          (b.copy(methods = methods.map(_.method)), methods.flatMap(_.warnings))
        }


      case b: IzType.Service =>
        for {
          methods <- b.methods.map(process).biAggregate
        } yield {
          (b.copy(methods = methods.map(_.method)), methods.flatMap(_.warnings))
        }
    }
  }

  private def process(method: IzMethod): Either[List[T2Fail], Output] = {
    Right(Output(method, allWarnings))
  }
}

object RestSupport {
  case class Output(method: IzMethod, warnings: Vector[T2Warn])
}
