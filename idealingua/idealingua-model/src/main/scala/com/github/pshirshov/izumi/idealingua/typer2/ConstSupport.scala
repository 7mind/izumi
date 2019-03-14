package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawConstMeta, RawVal}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawGenericRef, RawNongenericRef, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.TyperFailure
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{Interpreter, ResolversImpl}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.FName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.BuiltinType
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.TopLevelScalarOrBuiltinGenericExpected
import com.github.pshirshov.izumi.idealingua.typer2.model._

class ConstSupport() {

  import results._

  private val ANYTYPE = IzTypeReference.Scalar(IzType.BuiltinScalar.TAny.id)

  def makeConsts(ts: Typespace2, index: DomainIndex): Either[TyperFailure, List[TypedConst]] = {
    val result = index.consts.flatMap(_.v.consts).map {
      c =>
        val t = c.const match {
          case scalar: RawVal.RawValScalar =>
            Right(ANYTYPE)
          case RawVal.CMap(value) =>
            Right(ANYTYPE)
          case RawVal.CList(value) =>
            Right(ANYTYPE)
          case RawVal.CTyped(typeId, _) =>
            refToTopLevelRef(index)(typeId)
          case RawVal.CTypedList(typeId, _) =>
            refToTopLevelRef(index)(typeId)
          case RawVal.CTypedObject(typeId, _) =>
            refToTopLevelRef(index)(typeId)
        }

        for {
          tref <- t
          v <- makeConst(index, ts, c.id.name, c.meta)(tref, c.const)
        } yield {
          TypedConst(c.id.name, v, c.meta)
        }
    }
      .biAggregate
    println(result)
    result.left.map(TyperFailure.apply)
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
        if (expected == ANYTYPE) {
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
        if (expected == ANYTYPE) {
          makeList(index, ts, name, meta)(value)
        } else {
          for {
            listArg <- listArgToTopLevelRef(index)(expected)
            lst <- makeTypedList(index, ts, name, meta)(listArg, value)
            ok <- isParent(ts)(listArg, lst.ref)
            _ <- if (ok) {
              Right(())
            } else {
              println((listArg, lst.ref))
              Left(List(???))
            }
          } yield {
            lst
          }
        }

      case RawVal.CTypedList(_, value) =>
        for {
          listArg <- listArgToTopLevelRef(index)(expected)
          lst <- makeTypedList(index, ts, name, meta)(listArg, value)
          refarg <- listArgToTopLevelRef(index)(lst.ref)
          ok <- isParent(ts)(listArg, refarg)
          _ <- if (ok) {
            Right(())
          } else {
            println((listArg, lst.ref))
            Left(List(???))
          }
        } yield {
          lst
        }

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
          _ <- args.map(a => refToTopLevelRef1(index)(a.value.ref)).biAggregate
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
        refToTopLevelRef1(index)(arg.value.ref)
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

  private def makeTypedList(index: DomainIndex, ts: Typespace2, name: String, meta: RawConstMeta)(elref: IzTypeReference, value: List[RawVal]): Either[List[T2Fail], TypedVal.TCList] = {
    val defn = ts.index.get(elref.id).map(_.member) match {
      case Some(v) =>
        v
      case None =>
        ???
    }


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
          val valRef = IzTypeArg(IzTypeArgValue(elref))
          TypedVal.TCList(values, IzTypeReference.Generic(IzType.BuiltinGeneric.TList.id, Seq(valRef), None))
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
            val keyRef = IzTypeArg(IzTypeArgValue(IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id)))
            val valRef = IzTypeArg(IzTypeArgValue(inferred))
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
            val valRef = IzTypeArg(IzTypeArgValue(inferred))
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
      val upper = allIds.find {
        parent =>
          allIds.map(child => isParent(ts)(parent, child)).toList.biAggregate match {
            case Left(value) =>
              ???
            case Right(value) =>
              value.forall(identity)
          }
      }
      upper match {
        case Some(value) =>
          Right(value)
        case None =>
          Right(ANYTYPE)
        //Left(List(InferenceFailed(name, allIds, meta)))
      }
    }
  }

  def isParent(ts: Typespace2)(parent: IzTypeReference, child: IzTypeReference): Either[List[T2Fail], Boolean] = {
    (parent, child) match {
      case (p: IzTypeReference.Scalar, c: IzTypeReference.Scalar) =>
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
