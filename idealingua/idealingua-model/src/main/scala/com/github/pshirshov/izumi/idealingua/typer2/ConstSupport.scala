package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawConst, RawConstMeta, RawVal}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.TyperFailure
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{Interpreter, ResolversImpl}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.FName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.BuiltinType
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgValue
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.TopLevelScalarOrBuiltinGenericExpected
import com.github.pshirshov.izumi.idealingua.typer2.model._

class ConstSupport() {

  import results._

  private val ANYTYPE = IzTypeReference.Scalar(IzType.BuiltinScalar.TAny.id)
  private val ANYLIST = IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(IzTypeArgValue(ANYTYPE)), None)
  private val ANYMAP = IzTypeReference.Generic(IzType.BuiltinGeneric.TMap.id, Seq(IzTypeArgValue(IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id)), IzTypeArgValue(ANYTYPE)), None)

  def makeConsts(ts: Typespace2, index: DomainIndex): Either[TyperFailure, List[TypedConst]] = {
    val result = index.consts.flatMap(_.v.consts).map {
      c =>
        val expectedType = toExpectedType(index, c)

        for {
          tref <- expectedType
          v <- makeConst(index, ts, c.id.name, c.meta)(tref, c.const)
        } yield {
          TypedConst(c.id.name, v, c.meta)
        }
    }
      .biAggregate
    println(result)
    result.left.map(TyperFailure.apply)
  }

  private def toExpectedType(index: DomainIndex, c: RawConst) = {
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
      case RawVal.CMap(value) =>
        Right(ANYMAP)
      case RawVal.CList(value) =>
        Right(ANYLIST)
    }
  }

  private def makeConst(index: DomainIndex, ts: Typespace2, name: String, meta: RawConstMeta)(expected: IzTypeReference, const: RawVal): Either[List[T2Fail], TypedVal] = {
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
      case RawVal.CMap(value) =>
        if (expected == ANYMAP || expected == ANYTYPE) {
          makeObject(index, ts, name, meta)(value)
        } else {
          for {
            //ref <- refToTopLevelRef(index)(typeId)
            obj <- makeTypedObject(index, ts, name, meta)(expected, value)
          } yield {
            obj
          }
        }

      case RawVal.CList(value) =>
        if (expected == ANYLIST || expected == ANYTYPE) {
          makeList(index, ts, name, meta)(value)
        } else {
          makeTypedListConst(index, ts, name, meta, expected, value)
        }

      case RawVal.CTypedList(_, value) =>
        makeTypedListConst(index, ts, name, meta, expected, value)

      case RawVal.CTypedObject(_, value) =>
        for {
          //ref <- refToTopLevelRef(index)(typeId)
          obj <- makeTypedObject(index, ts, name, meta)(expected, value)
        } yield {
          obj
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
                Left(List(???))
              }
            case RawVal.CLong(value) =>
              if (
                expected.id == IzType.BuiltinScalar.TInt64.id ||
                  (expected.id == IzType.BuiltinScalar.TUInt64.id && value > 0)
              ) {
                Right(TypedVal.TCLong(value))
              } else {
                Left(List(???))
              }
            case RawVal.CFloat(value) =>
              if (expected.id == IzType.BuiltinScalar.TFloat.id) {
                Right(TypedVal.TCFloat(value))
              } else {
                Left(List(???))
              }
            case RawVal.CString(value) =>
              if (expected.id == IzType.BuiltinScalar.TString.id) {
                Right(TypedVal.TCString(value))
              } else {
                println(expected.id)
                Left(List(???))
              }
            case RawVal.CBool(value) =>
              if (expected.id == IzType.BuiltinScalar.TBool.id) {
                Right(TypedVal.TCBool(value))
              } else {
                Left(List(???))
              }
          }
        } yield {
          v
        }
    }
  }

  private def refToTopLevelRef1(index: DomainIndex)(ref: IzTypeReference): Either[List[T2Fail], IzTypeReference] = {
    ref match {
      case s: IzTypeReference.Scalar =>
        Right(s)

      case g@IzTypeReference.Generic(_: BuiltinType, args, _) =>
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
      case IzTypeReference.Generic(id, arg :: Nil, adhocName) if id == IzType.BuiltinGeneric.TList.id =>
        refToTopLevelRef1(index)(arg.ref)
      case o =>
        Left(List(???))
    }

  }


  private def makeTypedObject(index: DomainIndex, ts: Typespace2, name: String, meta: RawConstMeta)(ref: IzTypeReference, value: Map[String, RawVal]): Either[List[T2Fail], TypedVal.TCObject] = {
    val defn = ref match {
      case IzTypeReference.Scalar(id) =>
        ts.index.get(ref.id).map(_.member) match {
          case Some(v: IzStructure) =>
            v
          case None =>
            ???
        }

      case IzTypeReference.Generic(id, args, adhocName) =>
        ???
    }


    val fields = defn.fields.map(f => f.name -> f).toMap

    assert(fields.keySet == value.keySet.map(FName))

    value
      .map {
        case (k, v) =>
          for {
            fdef <- fields.get(FName(k)).toRight(List(???))
            tref <- refToTopLevelRef1(index)(fdef.tpe)
            fieldv <- makeConst(index, ts, name, meta)(tref, v)
            parent <- isParent(ts)(fdef.tpe, fieldv.ref)
            _ <- if (parent) {
              Right(())
            } else {
              println((fdef.tpe, fieldv.ref))
              Left(List(???))
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
  }


  private def makeTypedListConst(index: DomainIndex, ts: Typespace2, name: String, meta: RawConstMeta, expected: IzTypeReference, value: List[RawVal]) = {
    for {
      elref <- listArgToTopLevelRef(index)(expected)
      lst <- {
        value
          .map {
            v =>
              for {
                v <- makeConst(index, ts, name, meta)(elref, v)
                parent <- isParent(ts)(elref, v.ref)
                _ <- if (parent) {
                  Right(())
                } else {
                  println((elref, v.ref))
                  Left(List(???))
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
      ok <- isParent(ts)(elref, refarg)
      _ <- if (ok) {
        Right(())
      } else {
        println((elref, lst.ref))
        Left(List(???))
      }
    } yield {
      lst
    }
  }


  private def makeObject(index: DomainIndex, ts: Typespace2, name: String, meta: RawConstMeta)(value: Map[String, RawVal]): Either[List[T2Fail], TypedVal.TCObject] = {
    value
      .map {
        case (k, v) =>
          makeConst(index, ts, name, meta)(ANYTYPE, v).map {
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

  private def makeList(index: DomainIndex, ts: Typespace2, name: String, meta: RawConstMeta)(value: List[RawVal]): Either[List[T2Fail], TypedVal.TCList] = {
    value
      .map {
        v =>
          makeConst(index, ts, name, meta)(ANYTYPE, v)
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
      val existingUpper = allIds.find {
        parent =>
          allIds.map(child => isParent(ts)(parent, child)).toList.biAggregate match {
            case Left(value) =>
              ???
            case Right(value) =>
              value.forall(identity)
          }
      }
      existingUpper match {
        case Some(value) =>
          Right(value)
        case None =>
          // TODO: here we should try to find "common parent"
          Right(ANYTYPE)
      }
    }
  }

  def isParent(ts: Typespace2)(parent: IzTypeReference, child: IzTypeReference): Either[List[T2Fail], Boolean] = {
    (parent, child) match {
      case (p: IzTypeReference.Scalar, c: IzTypeReference.Scalar) =>
        isParentX(ts)(p, c)
      case (
          IzTypeReference.Generic(idp, IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _),
          IzTypeReference.Generic(idc, IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)
          ) if idc == idp && idc == IzType.BuiltinGeneric.TList.id =>
        isParentX(ts)(p, c)
      case (IzTypeReference.Generic(idp, IzTypeArgValue(kp: IzTypeReference.Scalar) :: IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _), IzTypeReference.Generic(idc, IzTypeArgValue(kc: IzTypeReference.Scalar) :: IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)) if idc == idp && idc == IzType.BuiltinGeneric.TMap.id  && kp == kc && kp.id == IzType.BuiltinScalar.TString.id =>
        isParentX(ts)(p, c)
      case (o1, o2) =>
        Right(o1 == o2)
    }
  }

  def isParentX(ts: Typespace2)(parent: IzTypeReference.Scalar, child: IzTypeReference.Scalar): Either[List[T2Fail], Boolean] = {
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
            Left(List(???))
        }
    }
  }


}
