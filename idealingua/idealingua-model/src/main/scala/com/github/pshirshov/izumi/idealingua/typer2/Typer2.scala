package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.graphs.Toposort
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.{NamedDefn, TypeDefn}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawNongenericRef}
import com.github.pshirshov.izumi.idealingua.typer2.constants.ConstSupport
import com.github.pshirshov.izumi.idealingua.typer2.indexing.DomainIndex
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.results._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class Typer2(options: TyperOptions, defn: DomainMeshResolved) {
  Quirks.discard(options)

  import Typer2._

  def run(): Either[TyperFailure, Typespace2] = {
    val r = doRun()

    println(s">>> ${defn.id}")
    r match {
      case Left(value) =>

        println(s"  ... failed: ${value}")

      case Right(value) =>
        val types = value.types.size
        println(s"  ... $types members, ${value.warnings.size} warnings")
    }

    r
  }


  private def doRun(): Either[TyperFailure, Typespace2] = {
    for {
      index <- DomainIndex.build(defn).left.map(TyperFailure.apply)
      _ <- preverify(index)
      importedIndexes <- defn.referenced.toSeq
        .map {
          case (k, v) => DomainIndex.build(v).map(r => k -> r)
        }
        .biAggregate
        .map(_.toMap)
        .left.map(TyperFailure.apply)
      allOperations <- combineOperations(index, importedIndexes)
      groupedByType <- groupOps(allOperations)
      ordered <- orderOps(groupedByType)
      result <- fill(index, importedIndexes, groupedByType, ordered)
      consts <- new ConstSupport().makeConsts(result.ts, index, result.consts, importedIndexes)
    } yield {
      result.ts.copy(consts = consts)
    }
  }


  private def preverify(index: DomainIndex): Either[TyperFailure, Unit] = {
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

    val everything = badTypes.toSeq.groupBy(_._1).mapValues(_.map(_._2)).mapValues(_.flatten)

    (for {
      _ <- check("type", badTypes)
      _ <- check("name", everything)
    } yield {

    }).left.map(TyperFailure.apply)
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
    val aliases = index.importIndex.filter(v => v._2.id.importedAs != v._2.id.name).mapValues {
      v =>
        val rname = RawDeclaredTypeName(v.id.importedAs)
        val name = index.resolveTopLeveleName(rname)
        val source = importedIndexes(v.domain).resolveTopLeveleName(RawDeclaredTypeName(v.id.name))
        val sourceRef = RawNongenericRef(v.domain.toPackage, v.id.name)
        DefineType(name, Set(source), OriginatedDefn(defn.id, RawTopLevelDefn.TLDBaseType(RawTypeDef.Alias(rname, sourceRef, v.id.meta))))
    }

    val builtinOps = Builtins.mappingAll.map(b => DefineBuiltin(index.makeAbstract(b._1)))
    val allOperations = domainOps ++ builtinOps ++ aliases.values ++ importedOps.toSeq
    Right(allOperations)
  }

  private def fill(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex], groupedByType: Map[TypenameRef, UniqueOperation], ordered: Seq[TypenameRef]): Either[TyperFailure, Ts2Builder.Output] = {
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
            case b: DefineBuiltin =>
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

        // TODO: remove
        exception.printStackTrace()

        Left(TyperFailure(List(UnexpectedException(exception))))
      case Success(value) =>
        value
    }
  }

  private def groupOps(allOperations: Seq[UniqueOperation]): Either[TyperFailure, Map[TypenameRef, UniqueOperation]] = {
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
      .left.map(TyperFailure.apply)
  }

  private def orderOps(groupedByType: Map[TypenameRef, UniqueOperation]): Either[TyperFailure, Seq[TypenameRef]] = {

    val circulars = new mutable.ArrayBuffer[Set[TypenameRef]]()

    def resolver(circular: Set[TypenameRef]): TypenameRef = {
      circulars.append(circular)
      circular.head
    }


    val deps = groupedByType.mapValues(_.depends)

    (for {
      ordered <- new Toposort().cycleBreaking(deps, Seq.empty, resolver)
        .left.map {
        f =>
          val existing = deps.keySet
          val problematic = f.issues.values.flatten.toSet
          val problems = problematic.diff(existing)
          problems
            .map {

              p =>
                val locations = groupedByType.values.filter(_.depends.contains(p)).collect({
                  case DefineType(id, _, main) =>
                    id -> main.defn.defn.meta.position
                }).groupBy(_._1).mapValues(_.map(_._2).toSeq)

                MissingType(p, locations)

            }
            .toList
      }

      verified <- if (circulars.nonEmpty) {
        Left(List(CircularDependenciesDetected(circulars.toList)))
      } else {
        Right(ordered)
      }
    } yield {
      verified
    }).left.map(TyperFailure.apply)

  }
}


object Typer2 {

  case class TyperFailure(errors: List[T2Fail], warnings: List[T2Warn])

  object TyperFailure {
    def apply(errors: List[T2Fail]): TyperFailure = new TyperFailure(errors, List.empty)
  }

  case class TypenameRef(pkg: Seq[String], name: String) {
    override def toString: String = s"<${pkg.mkString(".")}>.$name"
  }

  case class OriginatedDefn(source: DomainId, defn: TypeDefn)

  sealed trait Operation {
    def id: TypenameRef

    def depends: Set[TypenameRef]
  }

  sealed trait UniqueOperation extends Operation

  case class DefineBuiltin(id: TypenameRef) extends UniqueOperation {
    override def depends: Set[TypenameRef] = Set.empty
  }

  case class DefineType(id: TypenameRef, depends: Set[TypenameRef], main: OriginatedDefn) extends UniqueOperation

  case class DefineWithDecls(id: TypenameRef, depends: Set[TypenameRef], main: OriginatedDefn, decls: Seq[OriginatedDefn]) extends Operation

}
