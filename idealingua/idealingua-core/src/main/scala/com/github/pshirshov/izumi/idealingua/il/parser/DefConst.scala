package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax.Literals
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Identifiers, kw, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILConst
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{Constants, RawAnno, RawConst, RawVal}
import fastparse._
import fastparse.NoWhitespace._

sealed trait Agg {
  def value: RawVal[_]
}

object Agg {

  final case class Just(value: RawVal[Any]) extends Agg

  final case class ListAgg(value: RawVal.CList) extends Agg

  final case class ObjAgg(value: RawVal.CMap) extends Agg

}

trait DefConst extends Identifiers {
  def literal[_:P]: P[Agg.Just] = {
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



  def objdef[_:P]: P[Agg.ObjAgg] = enclosedConsts.map {
    v =>
      Agg.ObjAgg(RawVal.CMap(v.map(rc => rc.id.name -> rc.const).toMap))
  }

  def listMember[_:P]: P[Seq[Agg]] = P((literal | objdef /*| listdef*/).rep(sep = sep.sepStruct))

  def listdef[_:P]: P[Agg.ListAgg] = {
    structure.aggregates.enclosedB(listMember)
      .map {
        v =>
          val elements = v.map {
            case Agg.Just(jv) =>
              jv
            case Agg.ListAgg(cv) =>
              cv
            case Agg.ObjAgg(cv) =>
              cv
          }

          Agg.ListAgg(RawVal.CList(elements.toList))
      }
  }

  def value[_:P]: P[Agg] = literal | objdef | listdef

  def const[_:P]: P[RawConst] = (MaybeDoc ~ idShort ~ (inline ~ ":" ~ inline ~ idGeneric).? ~ inline ~ "=" ~ inline ~ value).map {
    case (doc, name, None, value: Agg.ObjAgg) =>
      RawConst(name.toConstId, value.value, doc)

    case (doc, name, Some(typename), value: Agg.ObjAgg) =>
      RawConst(name.toConstId, RawVal.CTypedObject(typename, value.value.value), doc)

    case (doc, name, None, value: Agg.ListAgg) =>
      RawConst(name.toConstId, value.value, doc)

    case (doc, name, Some(typename), value: Agg.ListAgg) =>
      RawConst(name.toConstId, RawVal.CTypedList(typename, value.value.value), doc)

    case (doc, name, None, Agg.Just(rv)) =>
      RawConst(name.toConstId, rv, doc)

    case (doc, name, Some(typename), Agg.Just(rv)) =>
      RawConst(name.toConstId, RawVal.CTyped(typename, rv.value), doc)
  }

  // other method kinds should be added here
  def consts[_:P]: P[Seq[RawConst]] = P(const.rep(sep = sepStruct))

  def enclosedConsts[_:P]: P[Seq[RawConst]] = structure.aggregates.enclosed(consts)

  def constBlock[_:P]: P[ILConst] = kw(kw.consts, inline ~ enclosedConsts)
    .map {
      v => ILConst(Constants(v.toList))
    }

  def simpleConst[_:P]: P[(String, RawVal[_])] = (idShort ~ inline ~ "=" ~ inline ~ value).map {
    case (k, v) =>
      k.name -> v.value
  }
  def simpleConsts[_:P]: P[RawVal.CMap] = simpleConst.rep(min = 0, sep = sepStruct)
    .map(v => RawVal.CMap(v.toMap))

  def defAnno[_:P]: P[RawAnno] = P("@" ~ idShort ~ "(" ~ inline ~ simpleConsts ~ inline ~")")
    .map {
      case (id, v) => RawAnno(id.name, v)
    }

  def defAnnos[_:P]: P[Seq[RawAnno]] = P(defAnno.rep(min = 1, sep = any) ~ NLC ~ inline).?.map(_.toSeq.flatten)
}

object DefConst extends DefConst {
}
