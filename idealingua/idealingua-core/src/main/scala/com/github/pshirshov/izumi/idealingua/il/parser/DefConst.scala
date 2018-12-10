package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax.Literals
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Identifiers, kw, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.TopLevelDefn.TLDConsts
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{Constants, RawAnno, RawConst, RawVal}
import fastparse._
import fastparse.NoWhitespace._

sealed trait Agg {
  def value: RawVal
}

object Agg {

  final case class Just(value: RawVal) extends Agg

  final case class ListAgg(value: RawVal.CList) extends Agg

  final case class ObjAgg(value: RawVal.CMap) extends Agg

  final case class TListAgg(value: RawVal.CTypedList) extends Agg

  final case class TObjAgg(value: RawVal.CTypedObject) extends Agg

  final case class TJust(value: RawVal.CTyped) extends Agg

}

class DefConst(context: IDLParserContext) extends Identifiers {
  import context._
  import defPositions._
  def justValue[_: P]: P[Agg] = P(literal | objdef | listdef)

  def typedValue[_: P]: P[Agg] = (idGeneric ~ inline ~ "(" ~ inline ~ justValue ~ inline ~ ")").map {
    case (id, agg) =>
      agg match {
        case Agg.Just(value) =>
          Agg.TJust(RawVal.CTyped(id, value))
        case Agg.ListAgg(value) =>
          Agg.TListAgg(RawVal.CTypedList(id, value.value))
        case Agg.ObjAgg(value) =>
          Agg.TObjAgg(RawVal.CTypedObject(id, value.value))
        case Agg.TListAgg(value) =>
          Agg.TListAgg(RawVal.CTypedList(id, value.value))
        case Agg.TObjAgg(value) =>
          Agg.TObjAgg(RawVal.CTypedObject(id, value.value))
        case Agg.TJust(value) =>
          Agg.TJust(RawVal.CTyped(id, value))

      }
  }


  def anyValue[_: P]: P[Agg] = P(typedValue | justValue)

  def value[_: P]: P[Agg] = P(("(" ~ inline ~ anyValue ~ inline ~ ")") | anyValue)

  def literal[_: P]: P[Agg.Just] = {
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
    )).map(Agg.Just)
  }

  def objdef[_: P]: P[Agg.ObjAgg] = enclosedConsts.map {
    v =>
      Agg.ObjAgg(RawVal.CMap(v.map(rc => rc.id.name -> rc.const).toMap))
  }

  def listElements[_: P]: P[Seq[Agg]] = P(value.rep(sep = sep.sepStruct) ~ sep.sepStruct.?)

  def listdef[_: P]: P[Agg.ListAgg] = {
    structure.aggregates.enclosedB(listElements)
      .map {
        v =>
          Agg.ListAgg(RawVal.CList(v.map(_.value).toList))
      }
  }


  // other method kinds should be added here
  def consts[_: P]: P[Seq[RawConst]] = P(const.rep(sep = sepStruct) ~ sepStruct.?)

  def enclosedConsts[_: P]: P[Seq[RawConst]] = structure.aggregates.enclosed(consts)

  def constBlock[_: P]: P[TLDConsts] = kw(kw.consts, inline ~ enclosedConsts)
    .map {
      v => TLDConsts(Constants(v.toList))
    }

  def simpleConsts[_: P]: P[RawVal.CMap] = P(simpleConst.rep(min = 0, sep = sepStruct) ~ sepStruct.?)
    .map(v => RawVal.CMap(v.map(c => (c.id.name, c.const)).toMap))

  def defAnno[_: P]: P[RawAnno] = P(positioned("@" ~ idShort ~ "(" ~ inline ~ simpleConsts ~ inline ~ ")"))
    .map {
      case (pos, (id, value)) =>
        RawAnno(id.name, value, pos)
    }

  def defAnnos[_: P]: P[Seq[RawAnno]] = P(defAnno.rep(min = 1, sep = any) ~ NLC ~ inline).?.map(_.toSeq.flatten)


  def simpleConst[_: P]: P[RawConst] = P(idShort ~ (inline ~ ":" ~ inline ~ idGeneric).? ~ inline ~ "=" ~ inline ~ value).map {
    case (name, tpe, value: Agg.ObjAgg) =>
      tpe match {
        case Some(typename) =>
          RawConst(name.toConstId, RawVal.CTypedObject(typename, value.value.value), None)
        case None =>
          RawConst(name.toConstId, value.value, None)
      }

    case (name, tpe, value: Agg.ListAgg) =>
      tpe match {
        case Some(typename) =>
          RawConst(name.toConstId, RawVal.CTypedList(typename, value.value.value), None)
        case None =>
          RawConst(name.toConstId, value.value, None)
      }


    case (name, tpe, Agg.Just(rv)) =>
      tpe match {
        case Some(typename) =>
          RawConst(name.toConstId, RawVal.CTyped(typename, rv), None)
        case None =>
          RawConst(name.toConstId, rv, None)
      }

    case (name, tpe, value: Agg.TObjAgg) =>
      tpe match {
        case Some(typename) =>
          RawConst(name.toConstId, RawVal.CTypedObject(typename, value.value.value), None)
        case None =>
          RawConst(name.toConstId, value.value, None)
      }

    case (name, tpe, value: Agg.TListAgg) =>
      tpe match {
        case Some(typename) =>
          RawConst(name.toConstId, RawVal.CTypedList(typename, value.value.value), None)
        case None =>
          RawConst(name.toConstId, value.value, None)
      }


    case (name, tpe, Agg.TJust(rv)) =>
      tpe match {
        case Some(typename) =>
          RawConst(name.toConstId, RawVal.CTyped(typename, rv), None)
        case None =>
          RawConst(name.toConstId, rv, None)
      }

  }


  def const[_: P]: P[RawConst] = P(MaybeDoc ~ simpleConst).map {
    case (doc, constVal) =>
      constVal.copy(doc = doc)
  }

}
