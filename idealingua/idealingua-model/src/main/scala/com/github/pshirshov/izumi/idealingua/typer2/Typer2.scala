package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.graphs
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.{NamedDefn, TypeDefn}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.{T2Fail, Typespace2}
import com.github.pshirshov.izumi.idealingua.typer2.results._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class Typer2(defn: DomainMeshResolved) {

  import Typer2._

  def run(): Unit = {
    println(s">>> ${defn.id}")
    val r = run1()
    r match {
      case Left(value) =>

        println(s"  ... failed: ${value}")

      case Right(value) =>
        val types = value.types.size
        println(s"  ... $types members, ${value.warnings.size} warnings")
    }

  }

  def run1(): Either[List[T2Fail], Typespace2] = {
    for {
      index <- DomainIndex.build(defn)
      _ <- preverify(index)
      importedIndexes <- defn.referenced.toSeq
        .map {
          case (k, v) => DomainIndex.build(v).map(r => k -> r)
        }
        .biAggregate
        .map(_.toMap)
      allOperations <- combineOperations(index, importedIndexes)
      groupedByType <- groupOps(allOperations)
      deps = groupedByType.mapValues(_.depends)
      ordered <- orderOps(deps)
      typespace <- fill(index, importedIndexes, groupedByType, ordered)
    } yield {
      typespace
    }
  }

  private def preverify(index: DomainIndex): Either[List[T2Fail], Unit] = {
    val badTypes = index.types
      .groupBy {
        case RawTopLevelDefn.TLDBaseType(v) =>
          v.id
        case RawTopLevelDefn.TLDNewtype(v) =>
          v.id
        case RawTopLevelDefn.TLDForeignType(v) =>
          RawDeclaredTypeName(v.id.name)
        case RawTopLevelDefn.TLDTemplate(t) =>
          t.decl.id
        case RawTopLevelDefn.TLDInstance(i) =>
          i.id
      }
      .filter(_._2.size > 1)

    val badServices = index.services.groupBy(_.v.id).filter(_._2.size > 1)
    val badBuzzers = index.buzzers.groupBy(_.v.id).filter(_._2.size > 1)
    val badStreams = index.streams.groupBy(_.v.id).filter(_._2.size > 1)

    val everything = (badServices.toSeq ++ badBuzzers.toSeq ++ badStreams.toSeq ++ badTypes.toSeq).groupBy(_._1).mapValues(_.map(_._2)).mapValues(_.flatten)

    for {
      _ <- check("service", badServices)
      _ <- check("buzzer", badBuzzers)
      _ <- check("stream", badStreams)
      _ <- check("type", badTypes)
      _ <- check("name", everything)
    } yield {

    }
  }

  private def check(kind: String, c: Map[RawDeclaredTypeName, Seq[NamedDefn]]): Either[List[TopLevelNameConflict], Unit] = {
    if (c.nonEmpty) {
      val info = c.map {
        case (k, tdef) =>
          k -> tdef.map {
            case defn: TypeDefn =>
              defn match {
                case RawTopLevelDefn.TLDBaseType(v) =>
                  v.meta
                case RawTopLevelDefn.TLDNewtype(v) =>
                  v.meta
                case RawTopLevelDefn.TLDForeignType(v) =>
                  v.meta
                case RawTopLevelDefn.TLDTemplate(t) =>
                  t.meta
                case RawTopLevelDefn.TLDInstance(i) =>
                  i.meta
              }
            case RawTopLevelDefn.TLDService(v) =>
              v.meta
            case RawTopLevelDefn.TLDBuzzer(v) =>
              v.meta
            case RawTopLevelDefn.TLDStreams(v) =>
              v.meta
          }
            .map(_.position)
      }
      Left(List(TopLevelNameConflict(kind, info)))
    } else {
      Right(())
    }
  }

  private def combineOperations(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]): Either[Nothing, Seq[UniqueOperation]] = {
    val domainOps = index.dependencies.groupByType()
    val importedOps = importedIndexes.values.flatMap(idx => idx.dependencies.groupByType())
    val builtinOps = Builtins.all.map(b => Builtin(index.makeAbstract(b.id)))
    val allOperations = domainOps ++ builtinOps ++ importedOps.toSeq
    Right(allOperations)
  }

  private def fill(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex], groupedByType: Map[UnresolvedName, UniqueOperation], ordered: Seq[UnresolvedName]): Either[List[T2Fail], Typespace2] = {
    val processor = new Ts2Builder(index, importedIndexes)
    val declIndex = index.declaredTypes.groupBy {
      case RawTopLevelDefn.TLDBaseType(v) =>
        index.resolveTopLeveleName(v.id)
      case RawTopLevelDefn.TLDNewtype(v) =>
        index.resolveTopLeveleName(v.id)
      case RawTopLevelDefn.TLDForeignType(v) =>
        index.resolveTopLeveleName(RawDeclaredTypeName(v.id.name))
      case RawTopLevelDefn.TLDTemplate(v) =>
        index.resolveTopLeveleName(v.decl.id)
      case RawTopLevelDefn.TLDInstance(v) =>
        index.resolveTopLeveleName(v.id)
    }

    Try {
      ordered.foreach {
        name =>
          val justOp = groupedByType(name)
          val withDecls = justOp match {
            case b: Builtin =>
              b
            case d: DefineType =>
              DefineWithDecls(d.id, d.depends, d.main, declIndex.getOrElse(d.id, Seq.empty).map(OriginatedDefn(defn.id, _)))
          }

          val missingDeps = withDecls.depends.diff(processor.defined)
          if (missingDeps.isEmpty) {
            processor.add(withDecls)
          } else {
            processor.fail(withDecls, List(DependencyMissing(withDecls, missingDeps, withDecls.id)))
          }
      }
      processor.finish()
    } match {
      case Failure(exception) =>
        import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
        println(exception.stackTrace)
        Left(List(UnexpectedException(exception)))
      case Success(value) =>
        value
    }
  }

  private def groupOps(allOperations: Seq[UniqueOperation]): Either[List[NameConflict], Map[UnresolvedName, UniqueOperation]] = {
    allOperations
      .groupBy(_.id)
      .map {
        case (key, ops) =>
          if (ops.tail.isEmpty) {
            Right(key -> ops.head)
          } else {
            Left(List(NameConflict(key)))
          }
      }
      .toSeq
      .biAggregate.map(_.toMap)
  }

  private def orderOps(deps: Map[UnresolvedName, Set[UnresolvedName]]): Either[List[CircularDependenciesDetected], Seq[UnresolvedName]] = {
    val circulars = new mutable.ArrayBuffer[Set[UnresolvedName]]()
    val ordered = graphs.toposort.cycleBreaking(deps, Seq.empty, (circular: Set[UnresolvedName]) => {
      circulars.append(circular)
      circular.head
    })

    if (circulars.nonEmpty) {
      Left(List(CircularDependenciesDetected(circulars.toList)))
    } else {
      Right(ordered)
    }
  }
}


object Typer2 {


  case class UnresolvedName(pkg: Seq[String], name: String) {
    override def toString: String = s"<${pkg.mkString(".")}>.$name"
  }

  case class OriginatedDefn(source: DomainId, defn: TypeDefn)

  sealed trait Operation {
    def id: UnresolvedName

    def depends: Set[UnresolvedName]
  }

  sealed trait UniqueOperation extends Operation

  case class Builtin(id: UnresolvedName) extends UniqueOperation {
    override def depends: Set[UnresolvedName] = Set.empty
  }

  case class DefineType(id: UnresolvedName, depends: Set[UnresolvedName], main: OriginatedDefn) extends UniqueOperation
  case class DefineWithDecls(id: UnresolvedName, depends: Set[UnresolvedName], main: OriginatedDefn, decls: Seq[OriginatedDefn]) extends Operation

}
