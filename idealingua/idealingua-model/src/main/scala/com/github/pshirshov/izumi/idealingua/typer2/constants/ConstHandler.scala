package com.github.pshirshov.izumi.idealingua.typer2.constants

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawConstMeta, RawVal}
import com.github.pshirshov.izumi.idealingua.typer2._
import com.github.pshirshov.izumi.idealingua.typer2.indexing.{DomainIndex, InheritanceQueries}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.FName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgValue
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model._

class ConstHandler(source: ConstSource, index: DomainIndex, ts: Typespace2, scope: String, name: String, meta: RawConstMeta) {
  import ConstSupport._
  import results._

  private val iq = InheritanceQueries(ts)
  private val resolver = new ConstNameResolver(index)

  private def isParent(name: String, meta: RawConstMeta)(parent: IzTypeReference, child: IzTypeReference): Either[List[T2Fail], Boolean] = {
    iq.isParent(parent, child).left.map(_ => List(ConstMissingType(name, child, meta)))
  }

  def makeConst(expected: IzTypeReference, const: RawVal): Either[List[T2Fail], TypedVal] = {
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
        val id = resolver.toId(scope, r)
        val existing = source.get(id)
        Right(TypedVal.TCRef(id, existing.value.ref))

      case r: RawVal.CTypedRef =>
        val id = resolver.toId(scope, r)
        val existing = source.get(id)
        for {
          expected <- resolver.refToTopLevelRef(r.typeId)
          _ <- isParent(name, meta)(expected, existing.value.ref)
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
              tref <- resolver.refToTopLevelRef1(fdef.tpe)
              fieldv <- new ConstHandler(source, index, ts, scope, name, meta).makeConst(tref, v)
              parent <- isParent(name, meta)(fdef.tpe, fieldv.ref)
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
      elref <- resolver.listArgToTopLevelRef(expected)
      lst <- {
        value
          .map {
            v =>
              for {
                v <- new ConstHandler(source, index, ts, scope, name, meta).makeConst(elref, v)
                parent <- isParent(name, meta)(elref, v.ref)
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
      refarg <- resolver.listArgToTopLevelRef(lst.ref)
      ok <- isParent(name, meta)(elref, refarg)
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
          new ConstHandler(source, index, ts, scope, name, meta).makeConst(ANYTYPE, v).map {
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
            inferred <- infer(name, meta)(values.values.toSeq)
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
          new ConstHandler(source, index, ts, scope, name, meta).makeConst(ANYTYPE, v)
      }
      .biAggregate
      .flatMap {
        values =>
          for {
            inferred <- infer(name, meta)(values)
          } yield {
            val valRef = IzTypeArgValue(inferred)
            TypedVal.TCList(values, IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(valRef), None))
          }
      }
  }

  private def infer(name: String, meta: RawConstMeta)(consts: Seq[TypedVal]): Either[List[T2Fail], IzTypeReference] = {
    val allIds = consts.map(_.ref).toSet
    if (allIds.size == 1) {
      Right(allIds.head)
    } else {
      // O(N^2) and it's okay

      for {
        // trying to find common parent across existing members
        existingUpper <- find(allIds.toSeq) {
          parent =>
            allIds.map(child => isParent(name, meta)(parent, child)).toList.biAggregate.map(_.forall(identity))
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


}
