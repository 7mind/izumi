package com.github.pshirshov.izumi.idealingua.typer2.restsupport

import com.github.pshirshov.izumi.functional.IzEither._
import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.TargetInterface
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec.HttpMethod
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.{DuplicatedRestAnnos, MissingValue, UnexpectedAnnotationType, UnexpectedValueType}
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._

class RestSupport(ts2: Typespace2) extends WarnLogger.WarnLoggerImpl {

  import com.github.pshirshov.izumi.idealingua.typer2.indexing.TypespaceTools._

  def translateRestAnnos(): Either[List[T2Fail], (List[ProcessedOp], List[T2Warn])] = {
    val out = ts2.types.map {
      case p: ProcessedOp.Exported =>
        p.member match {
          case interface: TargetInterface =>
            for {
              r <- processRest(interface)
            } yield {
              p.copy(member = r)
            }
          case _ => Right(p)
        }
      case p: ProcessedOp.Imported =>
        p.member match {
          case interface: TargetInterface =>
            for {
              r <- processRest(interface)
            } yield {
              p.copy(member = r)
            }
          case _ => Right(p)
        }
    }.biAggregate

    for {
      o <- out
    } yield {
      (o, allWarnings.toList)
    }
  }

  private def processRest(i: TargetInterface): Either[List[T2Fail], TargetInterface] = {
    i match {
      case b: IzType.Buzzer =>
        for {
          methods <- b.methods.map(process(i.id)).biAggregate
        } yield {
          b.copy(methods = methods)
        }


      case b: IzType.Service =>
        for {
          methods <- b.methods.map(process(i.id)).biAggregate
        } yield {
          b.copy(methods = methods)
        }
    }
  }

  private def process(service: IzTypeId)(method: IzMethod): Either[List[T2Fail], IzMethod] = {
    val annos = method.meta.annos.filter(a => HttpMethod.all.contains(a.name.toLowerCase))
    if (annos.isEmpty) {
      Right(method)
    } else if (annos.size > 2) {
      Left(List(DuplicatedRestAnnos(service, method.name)))
    } else {
      val ann = annos.head
      val httpMethod = HttpMethod.all(ann.name.toLowerCase)
      for {
        annv <- ts2.resolveConst(ann.ref)
        anndef <- toDef(service, method, httpMethod, annv)
        spec <- toSpec(service, method, anndef)
      } yield {
        method.copy(restSpec = Some(spec))
      }
    }
  }

  private def toDef(service: IzTypeId, method: IzMethod, httpMethod: HttpMethod, v: TypedConst): Either[List[T2Fail], RestAnnotation] = {
    v.value match {
      case TypedVal.TCObject(value, _) =>
        value.get("path") match {
          case Some(p: TypedVal.TCString) =>
            Right(RestAnnotation(p.value, httpMethod))
          case Some(p) =>
            Left(List(UnexpectedValueType(service, method.name, p, "path")))
          case None =>
            Left(List(MissingValue(service, method.name, "path")))
        }

      case _ =>
        Left(List(UnexpectedAnnotationType(service, method.name, v)))
    }
  }

  private def toSpec(service: IzTypeId, method: IzMethod, v: RestAnnotation): Either[List[T2Fail], RestSpec] = {
    ???
  }
}
