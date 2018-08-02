package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax.Literals
import com.github.pshirshov.izumi.idealingua.il.parser.structure.{Identifiers, kw, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILConst
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{Constants, RawConst, RawVal}
import fastparse.all._

trait DefConst extends Identifiers {
  final val literal = {
    import Literals.Literals._
    P(
      ("-".? ~ Float).!.map(_.toDouble).map(RawVal.CFloat) |
        ("-".? ~ Int).!.map { v =>
          if (v.toUpperCase.endsWith("L")) {
            RawVal.CLong(v.toLong)
          } else {
            RawVal.CInt(v.toInt)
          }
        } |
        Bool.!.map(_.toBoolean).map(RawVal.CBool) |
        Str.!.map(RawVal.CString)
    ).map(Agg.Just)
  }

  sealed trait Agg

  object Agg {

    final case class Just(v: RawVal[Any]) extends Agg

    final case class ListAgg(consts: RawVal.CList) extends Agg

    final case class ObjAgg(consts: RawVal.CMap) extends Agg

  }

  final def objdef: Parser[Agg.ObjAgg] = enclosedConsts.map {
    v =>
      Agg.ObjAgg(RawVal.CMap(v.map(rc => rc.id.name -> rc.const).toMap))
  }

  final def listdef: Parser[Agg.ListAgg] = {
    val t = P(literal | objdef | listdef).rep(sep = sep.sepStruct)

    structure.aggregates.enclosedB(t)
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

  final def value: Parser[Agg] = literal | objdef | listdef

  final def const: Parser[RawConst] = (MaybeDoc ~ idShort ~ (inline ~ ":" ~ inline ~ idGeneric).? ~ inline ~ "=" ~/ inline ~ value).map {
    case (doc, name, None, value: Agg.ObjAgg) =>
      RawConst(name.toConstId, value.consts, doc)

    case (doc, name, Some(typename), value: Agg.ObjAgg) =>
      RawConst(name.toConstId, RawVal.CTypedObject(typename, value.consts.value), doc)

    case (doc, name, None, value: Agg.ListAgg) =>
      RawConst(name.toConstId, value.consts, doc)

    case (doc, name, Some(typename), value: Agg.ListAgg) =>
      RawConst(name.toConstId, RawVal.CTypedList(typename, value.consts.value), doc)

    case (doc, name, None, Agg.Just(rv)) =>
      RawConst(name.toConstId, rv, doc)

    case (doc, name, Some(typename), Agg.Just(rv)) =>
      RawConst(name.toConstId, RawVal.CTyped(typename, rv.value), doc)
  }

  // other method kinds should be added here
  final val consts: Parser[Seq[RawConst]] = P(const.rep(sep = any))

  final def enclosedConsts: Parser[Seq[RawConst]] = structure.aggregates.enclosed(consts)

  final val constBlock = kw(kw.consts, inline ~ enclosedConsts)
    .map {
      v => ILConst(Constants(v.toList))
    }
}

object DefConst extends DefConst {
}
