package com.github.pshirshov.izumi.idealingua.typer2.restsupport

import com.github.pshirshov.izumi.functional.IzEither._
import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{BasicField, FName}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{IzStructure, TargetInterface}
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec.PathSegment.Parameter
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec._
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
          case Some(p: TypedVal.TCString) if p.value.nonEmpty =>
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
    val parts = v.path.split('?')
    val path = parts.head

    for {
      pathSegments <- translatePath(service, method)(path)
      qstr = parts.tail.mkString("?")
      query <- if (qstr.nonEmpty) {
        translateQuery(service, method)(qstr)
      } else {
        Right(Map.empty[QueryParameterName, QueryParameterSpec])
      }
      struct <- toStruct(service, method)
      existing = pathSegments.collect({ case p: Parameter => p.parameter }) ++ query.values.map(_.parameter).toSet
      missing = struct.fields.filterNot(f => existing.contains(f.basic))
      // TODO: validate
    } yield {
      val extractorSpec = ExtractorSpec(query, pathSegments)
      val bodySpec = BodySpec(missing.map(_.basic))
      RestSpec(v.method, extractorSpec, bodySpec)
    }
  }

  import com.github.pshirshov.izumi.functional.IzEither._

  import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

  private def translateQuery(service: IzTypeId, method: IzMethod)(query: String): Either[List[T2Fail], Map[QueryParameterName, QueryParameterSpec]] = {
    for {
      parts <- query.split('&').toList.map {
        p =>
          val pparts = p.split('=')
          val name = pparts.head
          val value = pparts.tail.mkString("=")
          if (value.startsWith("{") && value.endsWith("}") && value.length > 2) {
            Right(QueryParameterName(name) -> value)
          } else {
            Left(List.empty[T2Fail])
          }
      }.biAggregate
      grouped = parts.toMultimap
      bad = grouped.filter(_._2.size > 1)
      _ <- if (bad.nonEmpty) {
        Left(List())
      } else {
        Right(())
      }
      justVals = grouped.mapValues(_.head)
      mapped <- justVals.toList
        .map {
          case (k, v) =>
            for {
              param <- translateQueryParam(service, method)(v)
            } yield {
              k -> param
            }
        }
        .biAggregate
    } yield {
      mapped.toMap
    }
  }

  private def translateQueryParam(service: IzTypeId, method: IzMethod)(paramspec: String): Either[List[T2Fail], QueryParameterSpec] = {
    val spec = paramspec.substring(1, paramspec.length - 1)
    val (s, multi) = if (spec.startsWith("*")) {
      (spec.substring(1), true)
    } else {
      (spec, false)
    }

    for {
      p <- doTranslate(service, method)(s.split('.').toList)
      out <- if (multi) {
        if (p.path.size > 1) {
          Left(List())
        } else {
          Right(QueryParameterSpec.List(p.parameter, p.path.head, p.onWire))
        }
      } else {
        val isOpt = p.path.last.ref.id match {
          case b: IzTypeId.BuiltinTypeId if b == IzType.BuiltinGeneric.TOption.id =>
            true
          case _ =>
            false
        }
        Right(QueryParameterSpec.Scalar(p.parameter, p.path, p.onWire, isOpt))
      }

    } yield {
      out
    }
  }

  private def translatePath(service: IzTypeId, method: IzMethod)(path: String): Either[List[T2Fail], List[PathSegment]] = {
    val pathParts = path.split('/').toList
    pathParts.map {
      p =>
        if (p.startsWith("{") && p.endsWith("}") && p.length > 2) {
          val ps = p.substring(1, p.length - 1).split('.').toList
          doTranslate(service, method)(ps)
        } else {
          Right(PathSegment.Word(p))
        }
    }.biAggregate
  }

  private def toStruct(service: IzTypeId, method: IzMethod): Either[List[T2Fail], IzStructure] = {
    for {
      out <- method.input match {
        case IzInput.Singular(typeref: IzTypeReference.Scalar) =>
          for {
            struct <- ts2.asStructure(typeref.id)
          } yield {
            struct
          }
        case _ =>
          Left(List())
      }

    } yield {
      out
    }
  }

  private def doTranslate(service: IzTypeId, method: IzMethod)(ppath: List[String]): Either[List[T2Fail], Parameter] = {
    for {
      struct <- toStruct(service, method)
      s <- translate(struct, List.empty, ppath)
    } yield {
      s
    }
  }

  def translate(struct: IzStructure, cp: List[BasicField], ppath: List[String]): Either[List[T2Fail], Parameter] = {
    val fieldName = FName(ppath.head)
    val toProcess = ppath.tail

    for {
      f <- struct.fields.find(_.name == fieldName).toRight(List())
      nextcp = cp :+ f.basic
      next <- if (toProcess.isEmpty) {
        nextcp.last.ref match {
          case ref@IzTypeReference.Scalar(id: IzTypeId.BuiltinTypeId) if Builtins.mappingScalars.contains(id) =>
            Right(Parameter(cp.head, cp, OnWireScalar(ref)))

          case ref@IzTypeReference.Generic(id, args, _) if id == IzType.BuiltinGeneric.TOption.id && args.size == 1 =>
            args.head.ref match {
              case IzTypeReference.Scalar(aid: IzTypeId.BuiltinTypeId) if Builtins.mappingScalars.contains(aid) =>
                Right(Parameter(cp.head, cp, OnWireOption(ref)))

              case _ =>
                Left(List())
            }

          case _ =>
            Left(List())
        }
      } else {
        f.tpe match {
          case IzTypeReference.Scalar(id) =>
            for {
              nextStruct <- ts2.asStructure(id)
              next <- translate(nextStruct, nextcp, toProcess)
            } yield {
              next
            }
          case _ =>
            Left(List())
        }
      }
    } yield {
      next
    }
  }
}
