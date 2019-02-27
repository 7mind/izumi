package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.graphs
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TypeDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.DomainMeshResolved
import com.github.pshirshov.izumi.idealingua.typer2.T2Fail._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class Typer2(defn: DomainMeshResolved) {

  import Typer2._

  def run(): Unit = {
    println(s">>> ${defn.id}")
    val r = run1()
    r match {
      case Left(value) =>
        import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
        println(s"  ... failed: ${value}")

      case Right(value) =>
        val types = value.types.collect({case t: TsMember.UserType => t}).size
        val nss = value.types.collect({case n: TsMember.Namespace => n}).size
        println(s"  ... $types members, $nss namespaces")
    }

  }

  private lazy val index = new DomainIndex(defn)
  private lazy val importedIndexes: Map[DomainId, DomainIndex] = defn.referenced.mapValues(v => new DomainIndex(v))


  def run1(): Either[List[T2Fail], Typespace2] = {
    for {
      allOperations <- combineOperations()
      groupedByType <- groupOps(allOperations)
      deps = groupedByType.mapValues(_.depends)
      ordered <- orderOps(deps)
      typespace <- fill(groupedByType, ordered)
    } yield {
      typespace
    }
  }

  private def fill(groupedByType: Map[UnresolvedName, Identified], ordered: Seq[UnresolvedName]): Either[List[T2Fail], Typespace2] = {
    val processor = new Ts2Builder(index, importedIndexes)

    Try {
      ordered.foreach {
        name =>
          val ops = groupedByType(name)

          val missingDeps = ops.depends.diff(processor.defined)
          if (missingDeps.isEmpty) {
            processor.add(ops)
          } else {
            processor.fail(ops, List(DependencyMissing(missingDeps, ops.id)))
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

  private def combineOperations(): Either[Nothing, Seq[Identified]] = {
    val domainOps = index.dependencies.groupByType()
    val importedOps = importedIndexes.values.flatMap(idx => idx.dependencies.groupByType())
    val builtinOps = Builtins.all.map(b => Identified(index.makeAbstract(b.id), Set.empty, Seq.empty))
    val allOperations: Seq[Identified] = domainOps ++ builtinOps ++ importedOps.toSeq
    Right(allOperations)
  }

  private def groupOps(allOperations: Seq[Identified]): Either[List[ConflictingNames], Map[UnresolvedName, Identified]] = {
    val groupedByType = allOperations.groupBy(_.id).mapValues {
      ops =>
        ops.tail.foldLeft(ops.head) {
          case (acc, op) =>
            assert(acc.id == op.id)
            acc.copy(depends = acc.depends ++ op.depends, defns = acc.defns ++ op.defns)
        }
    }

    // TODO: check conflicts in each group

    Right(groupedByType)
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

  case class Identified(id: UnresolvedName, depends: Set[UnresolvedName], defns: Seq[OriginatedDefn])

}
