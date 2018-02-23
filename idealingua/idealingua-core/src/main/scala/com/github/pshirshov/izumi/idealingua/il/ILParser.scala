package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.il.IL.ILDomainId
import com.github.pshirshov.izumi.idealingua.model.common.{TypeId, UserType}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId, FinalDefinition, Service}
import fastparse.all._
import fastparse.CharPredicates._
import fastparse.{all, core}

//case class NamedFunction[T, V](f: T => V, name: String) extends (T => V){
//  def apply(t: T) = f(t)
//  override def toString() = name
//
//}

object IL {

  sealed trait Val

  case class ILDomainId(v: DomainId) extends Val

  case class ILService(v: Service) extends Val

  case class ILDef(v: FinalDefinition) extends Val

}

case class AbstractId(pkg: Seq[String], id: String) {
  def toDomainId: DomainId = DomainId(pkg, id)

  def toEnumId: EnumId = EnumId(pkg, id)

  def toAliasId: AliasId = AliasId(pkg, id)

  def toIdId: IdentifierId = IdentifierId(pkg, id)

  def toMixinId: InterfaceId = InterfaceId(pkg, id)

  def toDataId: DTOId = DTOId(pkg, id)

  def toTypeId: TypeId = UserType(pkg, id)

  def toServiceId: ServiceId = ServiceId(pkg, id)
}

object AbstractId {
  def apply(name: String): AbstractId = new AbstractId(Seq.empty, name)

  def apply(pkg: Seq[String]): AbstractId = new AbstractId(pkg.init, pkg.last)
}

//noinspection TypeAnnotation
class ILParser {

  import IL._

  final val ws = P(" " | "\t")(sourcecode.Name("WS"))
  final val NL = P("\r\n" | "\n" | "\r")
  final val wsm = P(ws.rep(1))
  final val wso = P(ws.rep)
  final val Newline = P(NL.rep(1))
  final val empty = P((ws | NL).rep)

  final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c)).rep).!

  def W(s: String) = P(s ~ wsm)(sourcecode.Name(s"`$s`"))

  final val enum = W("enum")
  final val alias = W("alias")
  final val id = W("id")
  final val mixin = W("mixin")
  final val data = W("data")
  final val service = W("service")
  final val domain = W("domain")

  final val identifier = P(symbol.rep(sep = ".")).map(v => AbstractId(v))

  final val domainId = P(domain ~/ identifier)
    .map(v => ILDomainId(v.toDomainId))

  final val field = P(wso ~ symbol ~ wso ~ ":" ~/ wso ~ identifier ~ wso)
  final val aggregate = P(field.rep(sep = Newline))
  final val mixed = P(wso ~ "+" ~/ wso ~ identifier ~ wso)
  final val composite = P(mixed.rep(sep = Newline))
  final val methods = P(empty)

  final val enumBlock = P(enum ~/ symbol ~ wso ~ "{" ~ (empty ~ symbol ~ empty).rep(1) ~ "}")
    .map(v => ILDef(FinalDefinition.Enumeration(AbstractId(v._1).toEnumId, v._2.toList)))

  final val aliasBlock = P(alias ~/ symbol ~ wso ~ "=" ~ wsm ~ identifier)
    .map(v => ILDef(FinalDefinition.Alias(AbstractId(v._1).toAliasId, v._2.toTypeId)))

  final val idBlock = P(id ~/ symbol ~ wso ~ "{" ~ (empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Identifier(AbstractId(v._1).toIdId, null)))

  final val mixinBlock = P(mixin ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Interface(AbstractId(v._1).toMixinId, null, v._2._1.map(_.toMixinId))))

  final val dtoBlock = P(data ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.DTO(AbstractId(v._1).toDataId, v._2.map(_.toMixinId))))

  final val serviceBlock = P(service ~/ symbol ~ wso ~ "{" ~ (empty ~ methods ~ empty) ~ "}")
    .map(v => ILService(Service(AbstractId(v).toServiceId, null)))

  final val anyBlock: core.Parser[Val, Char, String] = enumBlock |
    aliasBlock |
    idBlock |
    mixinBlock |
    dtoBlock |
    serviceBlock

  final val modelDef = P(empty ~ anyBlock.rep(sep = empty) ~ empty ~ End)

  final val fullDomainDef = P(domainId ~ Newline ~ empty ~ anyBlock.rep(sep = empty) ~ empty ~ End).map {
    v =>
      println(v)
      val types = v._2.collect({ case d: ILDef => d.v })
      val services = v._2.collect({ case d: ILService => d.v })
      DomainDefinition(v._1.v, types, services)
  }

  //final val symbol = P()
  //
  //final val StringChars = NamedFunction(!"\"\\".contains(_: Char), "StringChars")
  //
  //final val space         = P( CharsWhileIn(" \r\n").? )
  //final val digits        = P( CharsWhileIn("0123456789"))
  //final val exponent      = P( CharIn("eE") ~ CharIn("+-").? ~ digits )
  //final val fractional    = P( "." ~ digits )
  //final val integral      = P( "0" | CharIn('1' to '9') ~ digits.? )
  //
  //final val number = P( CharIn("+-").? ~ integral ~ fractional.? ~ exponent.? ).!.map(
  //    x => Js.Num(x.toDouble)
  //  )
  //
  //final val `null`        = P( "null" ).map(_ => Js.Null)
  //final val `false`       = P( "false" ).map(_ => Js.False)
  //final val `true`        = P( "true" ).map(_ => Js.True)
  //
  //final val hexDigit      = P( CharIn('0'to'9', 'a'to'f', 'A'to'F') )
  //final val unicodeEscape = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
  //final val escape        = P( "\\" ~ (CharIn("\"/\\bfnrt") | unicodeEscape) )
  //
  //final val strChars = P( CharsWhile(StringChars) )
  //final val string =
  //    P( space ~ "\"" ~/ (strChars | escape).rep.! ~ "\"").map(Js.Str)
  //
  //final val array =
  //    P( "[" ~/ jsonExpr.rep(sep=",".~/) ~ space ~ "]").map(Js.Arr(_:_*))
  //
  //final val pair = P( string.map(_.value) ~/ ":" ~/ jsonExpr )
  //
  //final val obj =
  //    P( "{" ~/ pair.rep(sep=",".~/) ~ space ~ "}").map(Js.Obj(_:_*))
  //
  //final val jsonExpr: P[Js.Val] = P(
  //    space ~ (obj | array | string | `true` | `false` | `null` | number) ~ space
  //  )
}
