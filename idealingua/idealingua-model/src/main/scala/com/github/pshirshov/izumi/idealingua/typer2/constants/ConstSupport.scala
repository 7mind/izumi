package com.github.pshirshov.izumi.idealingua.typer2.constants

import com.github.pshirshov.izumi.fundamentals.graphs.Toposort
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TLDConsts
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawConst, RawVal}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.TyperFailure
import com.github.pshirshov.izumi.idealingua.typer2._
import com.github.pshirshov.izumi.idealingua.typer2.indexing.DomainIndex
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgValue
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedConst
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

import scala.collection.mutable

object ConstSupport {

  sealed trait WIPConst {
    def const: RawConst

    def index: DomainIndex

    def id: TypedConstId
  }

  object WIPConst {

    case class Imported(index: DomainIndex, id: TypedConstId, const: RawConst) extends WIPConst

    case class Exported(index: DomainIndex, id: TypedConstId, const: RawConst) extends WIPConst

  }

  val ANYTYPE = IzTypeReference.Scalar(IzType.BuiltinScalar.TAny.id)
  val ANYLIST = IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(IzTypeArgValue(ANYTYPE)), None)
  val ANYMAP = IzTypeReference.Generic(IzType.BuiltinGeneric.TMap.id, Seq(IzTypeArgValue(IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id)), IzTypeArgValue(ANYTYPE)), None)
}

class ConstSupport() extends ConstSource {

  import ConstSupport._
  import results._

  override def get(id: TypedConstId): TypedConst = processed(id)

  private val processed = new mutable.HashMap[TypedConstId, TypedConst]()

  def makeConsts(ts: Typespace2, index: DomainIndex, moreConsts: Seq[TLDConsts], importedIndexes: Map[DomainId, DomainIndex]): Either[TyperFailure, List[ProcessedConst]] = {
    val thisConsts = for {
      block <- index.consts ++ moreConsts
      const <- block.v.consts
    } yield {
      WIPConst.Exported(index, TypedConstId(index.defn.id, block.v.name, const.id.name), const)
    }

    val importedConsts = for {
      idx <- importedIndexes.values
      block <- idx.consts
      const <- block.v.consts
    } yield {
      WIPConst.Imported(idx, TypedConstId(idx.defn.id, block.v.name, const.id.name), const)
    }


    val circulars = new mutable.ArrayBuffer[Set[TypedConstId]]()

    def resolver(circular: Set[TypedConstId]): TypedConstId = {
      circulars.append(circular)
      circular.head
    }

    (for {
      allConsts <- group((importedConsts ++ thisConsts).toSeq)
      deps = allConsts.mapValues(extractDeps)
      sorted <- new Toposort().cycleBreaking(deps, Seq.empty, resolver)
        .left.map {
        f =>
          val existing = deps.keySet
          val problematic = f.issues.values.flatten.toSet
          val problems = problematic.diff(existing)
          problems
            .map {
              p =>
                val locations = deps.toSeq
                  .filter(_._2.contains(p))
                  .map(d => d._1 -> allConsts(d._1).const.meta.position)
                  .toMultimap
                  .mapValues(_.toSeq)

                MissingConst(p, locations)
            }
            .toList
      }
      _ <- if (circulars.nonEmpty) {
        Left(List(ConstCircularDependenciesDetected(circulars.toList)))
      } else {
        Right(())
      }
      ordered = sorted.map(v => allConsts(v))
      translated <- translateConsts(ts, ordered)
    } yield {
      translated
    }).left.map(TyperFailure.apply)
  }

  private def group(allConsts: Seq[WIPConst]): Either[List[T2Fail], Map[TypedConstId, WIPConst]] = {
    val deps = allConsts.map {
      c =>
        c.id -> c
    }
      .toMultimap
      .mapValues(_.toSeq)

    val bad = deps.filter(_._2.size > 1)

    if (bad.nonEmpty) {
      Left(List(DuplicatedConstants(bad)))
    } else {
      Right(deps.mapValues(_.head))
    }
  }

  private def translateConsts(ts: Typespace2, consts: Seq[WIPConst]): Either[List[T2Fail], List[ProcessedConst]] = {

    val result = for {
      const <- consts
    } yield {
      val resolver = new ConstNameResolver(const.index)
      val expectedType = toExpectedType(resolver, const.const)

      for {
        tref <- expectedType
        v <- new ConstHandler(this, const.index, ts, const.id.scope, const.id.name, const.const.meta).makeConst(tref, const.const.const)
      } yield {
        val c = TypedConst(const.id, v, const.const.meta)

        assert(!processed.contains(const.id))
        processed.put(const.id, c)

        const match {
          case _: WIPConst.Imported =>
            ProcessedConst.Imported(c)
          case _: WIPConst.Exported =>
            ProcessedConst.Exported(c)
        }

      }
    }

    //println(result.biAggregate)

    result
      .biAggregate
  }


  private def extractDeps(w: WIPConst): Set[TypedConstId] = {
    extractDeps(new ConstNameResolver(w.index), w.id.scope)(w.const.const)
  }

  private def extractDeps(resolver: ConstNameResolver, defScope: String)(w: RawVal): Set[TypedConstId] = {
    w match {
      case r: RawVal.CRef =>
        Set(resolver.toId(defScope, r))
      case r: RawVal.CTypedRef =>
        Set(resolver.toId(defScope, r))
      case _: RawVal.RawValScalar =>
        Set.empty
      case RawVal.CMap(value) =>
        value.values.flatMap(extractDeps(resolver, defScope)).toSet
      case RawVal.CList(value) =>
        value.flatMap(extractDeps(resolver, defScope)).toSet
      case RawVal.CTyped(_, _) =>
        Set.empty
      case RawVal.CTypedList(_, value) =>
        value.flatMap(extractDeps(resolver, defScope)).toSet
      case RawVal.CTypedObject(_, value) =>
        value.values.flatMap(extractDeps(resolver, defScope)).toSet
    }
  }


  private def toExpectedType(resolver: ConstNameResolver, c: RawConst): Either[List[T2Fail], IzTypeReference] = {
    c.const match {
      case scalar: RawVal.RawValScalar =>
        val t = scalar match {
          case _: RawVal.CInt =>
            IzTypeReference.Scalar(IzType.BuiltinScalar.TInt32.id)
          case _: RawVal.CLong =>
            IzTypeReference.Scalar(IzType.BuiltinScalar.TInt64.id)
          case _: RawVal.CFloat =>
            IzTypeReference.Scalar(IzType.BuiltinScalar.TFloat.id)
          case _: RawVal.CString =>
            IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id)
          case _: RawVal.CBool =>
            IzTypeReference.Scalar(IzType.BuiltinScalar.TBool.id)
        }
        Right(t)
      case RawVal.CTyped(typeId, _) =>
        resolver.refToTopLevelRef(typeId)
      case RawVal.CTypedList(typeId, _) =>
        resolver.refToTopLevelRef(typeId)
      case RawVal.CTypedObject(typeId, _) =>
        resolver.refToTopLevelRef(typeId)
      case _: RawVal.CRef =>
        Right(ANYTYPE)
      case r: RawVal.CTypedRef =>
        resolver.refToTopLevelRef(r.typeId)
      case RawVal.CMap(_) =>
        Right(ANYMAP)
      case RawVal.CList(_) =>
        Right(ANYLIST)
    }
  }


}






