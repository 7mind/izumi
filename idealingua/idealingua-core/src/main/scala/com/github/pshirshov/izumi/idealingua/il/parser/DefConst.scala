package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax.Literals
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Identifiers, kw, sep}
import com.github.pshirshov.izumi.idealingua.model.common.ConstId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TLDConsts
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawVal.{CRef, RawValScalar}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import fastparse.NoWhitespace._
import fastparse._



class DefConst(context: IDLParserContext) extends Identifiers {

  import DefConst._
  import context._

  def defAnno[_: P]: P[RawAnno] = P(defPositions.positioned("@" ~ annoName ~ "(" ~ inline ~ constantsNoDoc ~ inline ~ ")"))
    .map {
      case (pos, (id, value)) =>
        defns.RawAnno(id, value, pos)
    }

  def defAnnos[_: P]: P[Seq[RawAnno]] = P(defAnno.rep(min = 1, sep = any) ~ NLC ~ inline).?.map(_.toSeq.flatten)

  def constBlock[_: P]: P[TLDConsts] = kw(kw.consts, inline ~ constBlockName ~ inline ~ enclosedConsts)
    .map {
      case (n, v) => TLDConsts(RawConstBlock(n, v.toList))
    }

  def constValue[_: P]: P[Aux] = P(("(" ~ inline ~ anyValue ~ inline ~ ")") | anyValue)

  private def const[_: P]: P[RawConst] = P(metaAgg.withMeta(constNoDoc)).map {
    case (meta, constVal) =>
      constVal.copy(meta = RawConstMeta(meta.doc.headOption, meta.position))
  }

  private def enclosedConsts[_: P]: P[Seq[RawConst]] = structure.aggregates.enclosed(constants)

  private def constants[_: P]: P[Seq[RawConst]] = P(const.rep(sep = sepStruct) ~ sepStruct.?)

  private def constantsNoDoc[_: P]: P[RawVal.CMap] = P(constNoDoc.rep(min = 0, sep = sepStruct) ~ sepStruct.?)
    .map(v => RawVal.CMap(v.map(c => (c.id.name, c.const)).toMap))

  def constId[_: P]: P[ConstId] = constName.map(n => ConstId(n))
  
  private def constNoDoc[_: P]: P[RawConst] = P(defPositions.positioned(constId ~ (inline ~ ":" ~ inline ~ typeReference).? ~ inline ~ "=" ~ inline ~ constValue))
    .map {
      case (pos, (name, tpe, value: Aux.ObjAux)) =>
        tpe match {
          case Some(typename) =>
            RawConst(name, RawVal.CTypedObject(typename, value.value.value), RawConstMeta(pos))
          case None =>
            RawConst(name, value.value, RawConstMeta(pos))
        }

      case (pos, (name, tpe, value: Aux.ListAux)) =>
        tpe match {
          case Some(typename) =>
            RawConst(name, RawVal.CTypedList(typename, value.value.value), RawConstMeta(pos))
          case None =>
            RawConst(name, value.value, RawConstMeta(pos))
        }

      case (pos, (name, tpe, Aux.Just(rv))) =>
        tpe match {
          case Some(typename) =>
            RawConst(name, RawVal.CTyped(typename, rv), RawConstMeta(pos))
          case None =>
            RawConst(name, rv, RawConstMeta(pos))
        }

      case (pos, (name, tpe, value: Aux.TObjAux)) =>
        tpe match {
          case Some(typename) =>
            RawConst(name, RawVal.CTypedObject(typename, value.value.value), RawConstMeta(pos))
          case None =>
            RawConst(name, value.value, RawConstMeta(pos))
        }

      case (pos, (name, tpe, value: Aux.TListAux)) =>
        tpe match {
          case Some(typename) =>
            RawConst(name, RawVal.CTypedList(typename, value.value.value), RawConstMeta(pos))
          case None =>
            RawConst(name, value.value, RawConstMeta(pos))
        }


      case (pos, (name, tpe, Aux.TJust(rv))) =>
        tpe match {
          case Some(typename) =>
            RawConst(name, RawVal.CTyped(typename, rv.value), RawConstMeta(pos))
          case None =>
            RawConst(name, rv, RawConstMeta(pos))
        }

    }


  private def justValue[_: P]: P[Aux] = P(literal | objdef | listdef)

  private def typedValue[_: P]: P[Aux] = (typeReference ~ inline ~ "(" ~ inline ~ justValue ~ inline ~ ")").map {
    case (id, agg) =>
      agg match {
        case Aux.Just(value) =>
          Aux.TJust(RawVal.CTyped(id, value))
        case Aux.ListAux(value) =>
          Aux.TListAux(RawVal.CTypedList(id, value.value))
        case Aux.ObjAux(value) =>
          Aux.TObjAux(RawVal.CTypedObject(id, value.value))
        case Aux.TListAux(value) =>
          Aux.TListAux(RawVal.CTypedList(id, value.value))
        case Aux.TObjAux(value) =>
          Aux.TObjAux(RawVal.CTypedObject(id, value.value))
        case Aux.TJust(value) =>
          Aux.TJust(RawVal.CTyped(id, value.value))
        case Aux.TRef(value) =>
          Aux.TRef(RawVal.CTypedRef(id, value.domain, value.scope, value.name))
        case Aux.Ref(value) =>
          Aux.TRef(RawVal.CTypedRef(id, value.domain, value.scope, value.name))
      }
  }

  private def refValue[_: P]: P[Aux] = P(domainId.? ~ "::" ~  (constBlockName ~ ".").? ~ constName).map {
    case (domain, block, name) =>
      Aux.Ref(CRef(domain, block, name))
  }

  private def anyValue[_: P]: P[Aux] = P(typedValue | justValue | refValue)


  private def literal[_: P]: P[Aux.Just] = {
    import Literals.Literals._
    NoCut(P(
      ("-".? ~ Float).!.map(_.toDouble).map(RawVal.CFloat) |
        ("-".? ~ Int).!.map { v =>
          if (v.toUpperCase.endsWith("L")) {
            RawVal.CLong(v.toLong)
          } else {
            RawVal.CInt(v.toInt)
          }
        } |
        Bool.!.map(_.toBoolean).map(RawVal.CBool) |
        Str.map(RawVal.CString)
    )).map(Aux.Just)
  }

  private def objdef[_: P]: P[Aux.ObjAux] = enclosedConsts.map {
    v =>
      Aux.ObjAux(RawVal.CMap(v.map(rc => rc.id.name -> rc.const).toMap))
  }

  private def listElements[_: P]: P[Seq[Aux]] = P(constValue.rep(sep = sep.sepStruct) ~ sep.sepStruct.?)

  private def listdef[_: P]: P[Aux.ListAux] = {
    structure.aggregates.inBrackets(listElements)
      .map {
        v =>
          Aux.ListAux(RawVal.CList(v.map(_.value).toList))
      }
  }
}

object DefConst {
  sealed trait Aux {
    def value: RawVal
  }

  object Aux {
    final case class ListAux(value: RawVal.CList) extends Aux

    final case class ObjAux(value: RawVal.CMap) extends Aux

    final case class Ref(value: RawVal.CRef) extends Aux

    final case class Just(value: RawValScalar) extends Aux

    final case class TListAux(value: RawVal.CTypedList) extends Aux

    final case class TObjAux(value: RawVal.CTypedObject) extends Aux

    final case class TJust(value: RawVal.CTyped) extends Aux

    final case class TRef(value: RawVal.CTypedRef) extends Aux

  }
}
