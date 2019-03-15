package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.graphs.Toposort
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TLDConsts
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawConst, RawConstMeta, RawVal}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef
import com.github.pshirshov.izumi.idealingua.typer2.ConstSupport.WIPConst
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.TyperFailure
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{Interpreter, ResolversImpl}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.FName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.BuiltinTypeId
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgValue
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedConst
import com.github.pshirshov.izumi.idealingua.typer2.model._

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

}

class ConstSupport() {

  import results._

  private val ANYTYPE = IzTypeReference.Scalar(IzType.BuiltinScalar.TAny.id)
  private val ANYLIST = IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(IzTypeArgValue(ANYTYPE)), None)
  private val ANYMAP = IzTypeReference.Generic(IzType.BuiltinGeneric.TMap.id, Seq(IzTypeArgValue(IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id)), IzTypeArgValue(ANYTYPE)), None)

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
                  .groupBy(_._1)
                  .mapValues(_.map(_._2))

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
    }.groupBy(_._1).mapValues(_.map(_._2))
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
      val expectedType = toExpectedType(const.index, const.const)

      for {
        tref <- expectedType
        v <- makeConst(const.index, ts, const.id.scope, const.id.name, const.const.meta)(tref, const.const.const)
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
    extractDeps(w.index, w.id.scope)(w.const.const)
  }

  private def extractDeps(index: DomainIndex, defScope: String)(w: RawVal): Set[TypedConstId] = {
    w match {
      case r: RawVal.CRef =>
        Set(toId(index, defScope, r))
      case r: RawVal.CTypedRef =>
        Set(toId(index, defScope, r))
      case _: RawVal.RawValScalar =>
        Set.empty
      case RawVal.CMap(value) =>
        value.values.flatMap(extractDeps(index, defScope)).toSet
      case RawVal.CList(value) =>
        value.flatMap(extractDeps(index, defScope)).toSet
      case RawVal.CTyped(_, _) =>
        Set.empty
      case RawVal.CTypedList(_, value) =>
        value.flatMap(extractDeps(index, defScope)).toSet
      case RawVal.CTypedObject(_, value) =>
        value.values.flatMap(extractDeps(index, defScope)).toSet
    }
  }

  private def toId(index: DomainIndex, defScope: String, r: RawVal.CTypedRef): TypedConstId = {
    val did = r.domain.getOrElse(index.defn.id)
    val sid = r.scope.getOrElse(defScope)
    TypedConstId(did, sid, r.name)
  }

  private def toId(index: DomainIndex, defScope: String, r: RawVal.CRef): TypedConstId = {
    val did = r.domain.getOrElse(index.defn.id)
    val sid = r.scope.getOrElse(defScope)
    TypedConstId(did, sid, r.name)
  }

  private def makeConst(index: DomainIndex, ts: Typespace2, scope: String, name: String, meta: RawConstMeta)(expected: IzTypeReference, const: RawVal): Either[List[T2Fail], TypedVal] = {
    const match {
      case scalar: RawVal.RawValScalar =>
        scalar match {
          case RawVal.CInt(value) =>
            Right(TypedVal.TCInt(value))
          case RawVal.CLong(value) =>
            Right(TypedVal.TCLong(value))
          case RawVal.CFloat(value) =>
            Right(TypedVal.TCFloat(value))
          case RawVal.CString(value) =>
            Right(TypedVal.TCString(value))
          case RawVal.CBool(value) =>
            Right(TypedVal.TCBool(value))
        }

      case r: RawVal.CRef =>
        val id = toId(index, scope, r)
        val existing = processed(id)
        Right(TypedVal.TCRef(id, existing.value.ref))

      case r: RawVal.CTypedRef =>
        val id = toId(index, scope, r)
        val existing = processed(id)
        for {
          expected <- refToTopLevelRef(index)(r.typeId)
          _ <- isParent(ts, name, meta)(expected, existing.value.ref)
        } yield {
          TypedVal.TCRef(id, expected)
        }

      case RawVal.CMap(value) =>
        if (expected == ANYMAP || expected == ANYTYPE) {
          makeObject(index, ts, scope, name, meta)(value)
        } else {
          for {
            obj <- makeTypedObject(index, ts, scope, name, meta)(expected, value)
          } yield {
            obj
          }
        }

      case RawVal.CTypedObject(_, value) =>
        if (expected == ANYMAP || expected == ANYTYPE) {
          makeObject(index, ts, scope, name, meta)(value)
        } else {
          for {
            obj <- makeTypedObject(index, ts, scope, name, meta)(expected, value)
          } yield {
            obj
          }
        }

      case RawVal.CList(value) =>
        if (expected == ANYLIST || expected == ANYTYPE) {
          makeList(index, ts, scope, name, meta)(value)
        } else {
          makeTypedListConst(index, ts, scope, name, meta, expected, value)
        }

      case RawVal.CTypedList(_, value) =>
        if (expected == ANYLIST || expected == ANYTYPE) {
          makeList(index, ts, scope, name, meta)(value)
        } else {
          makeTypedListConst(index, ts, scope, name, meta, expected, value)
        }


      case RawVal.CTyped(_, raw) =>
        for {
          v <- raw match {
            case RawVal.CInt(value) =>
              if (
                (expected.id == IzType.BuiltinScalar.TInt32.id && value >= Int.MinValue && value <= Int.MaxValue) ||
                  (expected.id == IzType.BuiltinScalar.TInt16.id && value <= -32768 && value >= 32767) ||
                  (expected.id == IzType.BuiltinScalar.TInt8.id && value <= -128 && value >= 127) ||
                  (expected.id == IzType.BuiltinScalar.TUInt8.id && value >= 0 && value <= 255) ||
                  (expected.id == IzType.BuiltinScalar.TUInt16.id && value >= 0 && value <= 65535) ||
                  (expected.id == IzType.BuiltinScalar.TUInt32.id && value >= 0)
              ) {
                Right(TypedVal.TCInt(value))
              } else {
                Left(List(RawConstantViolatesType(name, expected, raw, meta)))
              }
            case RawVal.CLong(value) =>
              if (
                expected.id == IzType.BuiltinScalar.TInt64.id ||
                  (expected.id == IzType.BuiltinScalar.TUInt64.id && value > 0)
              ) {
                Right(TypedVal.TCLong(value))
              } else {
                Left(List(RawConstantViolatesType(name, expected, raw, meta)))
              }
            case RawVal.CFloat(value) =>
              if (expected.id == IzType.BuiltinScalar.TFloat.id) {
                Right(TypedVal.TCFloat(value))
              } else {
                Left(List(RawConstantViolatesType(name, expected, raw, meta)))
              }
            case RawVal.CString(value) =>
              if (expected.id == IzType.BuiltinScalar.TString.id) {
                Right(TypedVal.TCString(value))
              } else {
                Left(List(RawConstantViolatesType(name, expected, raw, meta)))
              }
            case RawVal.CBool(value) =>
              if (expected.id == IzType.BuiltinScalar.TBool.id) {
                Right(TypedVal.TCBool(value))
              } else {
                Left(List(RawConstantViolatesType(name, expected, raw, meta)))
              }
          }
        } yield {
          v
        }
    }
  }

  private def toExpectedType(index: DomainIndex, c: RawConst): Either[List[T2Fail], IzTypeReference] = {
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
        refToTopLevelRef(index)(typeId)
      case RawVal.CTypedList(typeId, _) =>
        refToTopLevelRef(index)(typeId)
      case RawVal.CTypedObject(typeId, _) =>
        refToTopLevelRef(index)(typeId)
      case _: RawVal.CRef =>
        Right(ANYTYPE)
      case r: RawVal.CTypedRef =>
        refToTopLevelRef(index)(r.typeId)
      case RawVal.CMap(_) =>
        Right(ANYMAP)
      case RawVal.CList(_) =>
        Right(ANYLIST)
    }
  }

  private def refToTopLevelRef1(index: DomainIndex)(ref: IzTypeReference): Either[List[T2Fail], IzTypeReference] = {
    ref match {
      case s: IzTypeReference.Scalar =>
        Right(s)

      case g@IzTypeReference.Generic(_: BuiltinTypeId, args, _) =>
        for {
          // to make sure all args are instantiated recursively
          _ <- args.map(a => refToTopLevelRef1(index)(a.ref)).biAggregate
        } yield {
          g
        }

      case g: IzTypeReference.Generic =>
        Left(List(TopLevelScalarOrBuiltinGenericExpected(ref, g)))
    }
  }

  private def refToTopLevelRef(index: DomainIndex)(ref: RawRef): Either[List[T2Fail], IzTypeReference] = {
    val r = new ResolversImpl(Interpreter.Args(Map.empty), index)
    val tref = r.refToTopId2(r.resolve(ref))
    refToTopLevelRef1(index)(tref)
  }

  private def listArgToTopLevelRef(index: DomainIndex)(ref: IzTypeReference): Either[List[T2Fail], IzTypeReference] = {
    ref match {
      case IzTypeReference.Generic(id, arg :: Nil, _) if id == IzType.BuiltinGeneric.TList.id =>
        refToTopLevelRef1(index)(arg.ref)
      case o =>
        Left(List(ListExpectedToHaveOneTopLevelArg(o)))
    }

  }


  private def makeTypedObject(index: DomainIndex, ts: Typespace2, scope: String, name: String, meta: RawConstMeta)(ref: IzTypeReference, value: Map[String, RawVal]): Either[List[T2Fail], TypedVal.TCObject] = {
    for {
      defn <- ref match {
        case IzTypeReference.Scalar(id) =>
          ts.index.get(id).map(_.member) match {
            case Some(v: IzStructure) =>
              Right(v)
            case Some(_) =>
              Left(List(TopLevelStructureExpected(name, ref, meta)))
            case None =>
              Left(List(ConstMissingType(name, ref, meta)))
          }

        case o =>
          Left(List(TopLevelStructureExpected(name, o, meta)))
      }
      fields = defn.fields.map(f => f.name -> f).toMap
      v <- value
        .map {
          case (k, v) =>
            for {
              fdef <- fields.get(FName(k)).toRight(List(UndefinedField(name, ref, FName(k), meta)))
              tref <- refToTopLevelRef1(index)(fdef.tpe)
              fieldv <- makeConst(index, ts, scope, name, meta)(tref, v)
              parent <- isParent(ts, name, meta)(fdef.tpe, fieldv.ref)
              _ <- if (parent) {
                Right(())
              } else {
                Left(List(ConstantViolatesType(name, ref, fieldv, meta)))
              }
            } yield {
              k -> fieldv
            }
        }
        .toSeq
        .biAggregate
        .map {
          pairs =>
            val values = pairs.toMap
            TypedVal.TCObject(values, ref)
        }
    } yield {
      assert(fields.keySet == value.keySet.map(FName))


      v
    }


  }


  private def makeTypedListConst(index: DomainIndex, ts: Typespace2, scope: String, name: String, meta: RawConstMeta, expected: IzTypeReference, value: List[RawVal]): Either[List[T2Fail], TypedVal.TCList] = {
    for {
      elref <- listArgToTopLevelRef(index)(expected)
      lst <- {
        value
          .map {
            v =>
              for {
                v <- makeConst(index, ts, scope, name, meta)(elref, v)
                parent <- isParent(ts, name, meta)(elref, v.ref)
                _ <- if (parent) {
                  Right(())
                } else {
                  Left(List(ConstantViolatesType(name, expected, v, meta)))
                }
              } yield {
                v
              }
          }
          .biAggregate
          .map {
            values =>
              val valRef = IzTypeArgValue(elref)
              TypedVal.TCList(values, IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(valRef), None))
          }
      }
      refarg <- listArgToTopLevelRef(index)(lst.ref)
      ok <- isParent(ts, name, meta)(elref, refarg)
      _ <- if (ok) {
        Right(())
      } else {
        Left(List(ConstantViolatesType(name, expected, lst, meta)))
      }
    } yield {
      lst
    }
  }


  private def makeObject(index: DomainIndex, ts: Typespace2, scope: String, name: String, meta: RawConstMeta)(value: Map[String, RawVal]): Either[List[T2Fail], TypedVal.TCObject] = {
    value
      .map {
        case (k, v) =>
          makeConst(index, ts, scope, name, meta)(ANYTYPE, v).map {
            c =>
              k -> c
          }
      }
      .toSeq
      .biAggregate
      .flatMap {
        pairs =>
          val values = pairs.toMap
          for {
            inferred <- infer(ts, name, meta)(values.values.toSeq)
          } yield {
            val keyRef = IzTypeArgValue(IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id))
            val valRef = IzTypeArgValue(inferred)
            TypedVal.TCObject(values, IzTypeReference.Generic(IzType.BuiltinGeneric.TMap.id, Seq(keyRef, valRef), None))
          }
      }
  }

  private def makeList(index: DomainIndex, ts: Typespace2, scope: String, name: String, meta: RawConstMeta)(value: List[RawVal]): Either[List[T2Fail], TypedVal.TCList] = {
    value
      .map {
        v =>
          makeConst(index, ts, scope, name, meta)(ANYTYPE, v)
      }
      .biAggregate
      .flatMap {
        values =>
          for {
            inferred <- infer(ts, name, meta)(values)
          } yield {
            val valRef = IzTypeArgValue(inferred)
            TypedVal.TCList(values, IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(valRef), None))
          }
      }
  }

  private def infer(ts: Typespace2, name: String, meta: RawConstMeta)(consts: Seq[TypedVal]): Either[List[T2Fail], IzTypeReference] = {
    val allIds = consts.map(_.ref).toSet
    if (allIds.size == 1) {
      Right(allIds.head)
    } else {
      // O(N^2) and it's okay

      for {
        // trying to find common parent across existing members
        existingUpper <- find(allIds.toSeq) {
          parent =>
            allIds.map(child => isParent(ts, name, meta)(parent, child)).toList.biAggregate.map(_.forall(identity))
        }
      } yield {
        existingUpper match {
          case Some(value) =>
            value
          case None =>
            // TODO: here we should try to find "common parent"
            ANYTYPE
        }
      }

    }
  }

  private def find[T, E](s: Seq[T])(predicate: T => Either[List[E], Boolean]): Either[List[E], Option[T]] = {
    val i = s.iterator
    while (i.hasNext) {
      val a = i.next()
      predicate(a) match {
        case Left(value) =>
          return Left(value)
        case Right(value) if value =>
          return Right(Some(a))

        case Right(_) =>
      }
    }
    Right(None)
  }

  def isParent(ts: Typespace2, name: String, meta: RawConstMeta)(parent: IzTypeReference, child: IzTypeReference): Either[List[T2Fail], Boolean] = {
    (parent, child) match {
      case (p: IzTypeReference.Scalar, c: IzTypeReference.Scalar) =>
        isParentScalar(ts, name, meta)(p, c)
      case (
        IzTypeReference.Generic(idp, IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _),
        IzTypeReference.Generic(idc, IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)
        ) if idc == idp && idc == IzType.BuiltinGeneric.TList.id =>
        isParentScalar(ts, name, meta)(p, c)
      case (IzTypeReference.Generic(idp, IzTypeArgValue(kp: IzTypeReference.Scalar) :: IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _), IzTypeReference.Generic(idc, IzTypeArgValue(kc: IzTypeReference.Scalar) :: IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)) if idc == idp && idc == IzType.BuiltinGeneric.TMap.id && kp == kc && kp.id == IzType.BuiltinScalar.TString.id =>
        isParentScalar(ts, name, meta)(p, c)
      case (o1, o2) =>
        Right(o1 == o2)
    }
  }

  def isParentScalar(ts: Typespace2, name: String, meta: RawConstMeta)(parent: IzTypeReference.Scalar, child: IzTypeReference.Scalar): Either[List[T2Fail], Boolean] = {
    child match {
      case IzTypeReference.Scalar(id) if id == IzType.BuiltinScalar.TAny.id =>
        Right(true)
      case p: IzTypeReference.Scalar if p == parent =>
        Right(true)
      case IzTypeReference.Scalar(id) =>
        ts.index.get(id) match {
          case Some(value) =>
            value.member match {
              case structure: IzType.IzStructure =>
                Right(structure.allParents.contains(parent.id))
              case o =>
                Right(o.id == parent.id)
            }
          case None =>
            Left(List(ConstMissingType(name, child, meta)))
        }
    }
  }


}
