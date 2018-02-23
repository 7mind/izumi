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

  val ws = P(" " | "\t")(sourcecode.Name("WS"))
  val NL = P("\r\n" | "\n" | "\r")
  val wsm = P(ws.rep(1))
  val wso = P(ws.rep)
  val Newline = P(NL.rep(1))
  val empty = P((ws | NL).rep)

  val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c)).rep).!
  def W(s: String) = P(s ~ wsm)(sourcecode.Name(s"`$s`"))

  val enum = W("enum")
  val alias = W("alias")
  val id = W("id")
  val mixin = W("mixin")
  val data = W("data")
  val service = W("service")
  val domain = W("domain")

  val identifier = P(symbol.rep(sep = ".")).map(v => AbstractId(v))

  val domainId = P(domain ~/ identifier)
    .map(v => ILDomainId(v.toDomainId))

  val aggregate = P(empty)
  val composite = P(empty)
  val methods = P(empty)

  val enumBlock = P(enum ~/ symbol ~ wso ~ "{" ~ (empty ~ symbol ~ empty).rep(1) ~ "}")
    .map(v => ILDef(FinalDefinition.Enumeration(AbstractId(v._1).toEnumId, v._2.toList)))

  val aliasBlock = P(alias ~/ symbol ~ wso ~ "=" ~ wsm ~ identifier)
    .map(v => ILDef(FinalDefinition.Alias(AbstractId(v._1).toAliasId, v._2.toTypeId)))

  val idBlock = P(id ~/ symbol ~ wso ~ "{" ~ (empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Identifier(AbstractId(v).toIdId, null)))

  val mixinBlock = P(mixin ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Interface(AbstractId(v).toMixinId, null, null)))

  val dtoBlock = P(data ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.DTO(AbstractId(v).toDataId, null)))

  val serviceBlock = P(service ~/ symbol ~ wso ~ "{" ~ (empty ~ methods ~ empty) ~ "}")
    .map(v => ILService(Service(AbstractId(v).toServiceId, null)))

  val anyBlock: core.Parser[Val, Char, String] = enumBlock |
    aliasBlock |
    idBlock |
    mixinBlock |
    dtoBlock |
    serviceBlock

  val expr = P(domainId ~ Newline ~ empty ~ anyBlock.rep(sep = empty) ~ empty ~ End).map {
    v =>
      println(v)
      val types = v._2.collect({ case d: ILDef => d.v })
      val services = v._2.collect({ case d: ILService => d.v })
      DomainDefinition(v._1.v, types, services)
  }

  //  val symbol = P()
  //
  //  val StringChars = NamedFunction(!"\"\\".contains(_: Char), "StringChars")
  //
  //  val space         = P( CharsWhileIn(" \r\n").? )
  //  val digits        = P( CharsWhileIn("0123456789"))
  //  val exponent      = P( CharIn("eE") ~ CharIn("+-").? ~ digits )
  //  val fractional    = P( "." ~ digits )
  //  val integral      = P( "0" | CharIn('1' to '9') ~ digits.? )
  //
  //  val number = P( CharIn("+-").? ~ integral ~ fractional.? ~ exponent.? ).!.map(
  //    x => Js.Num(x.toDouble)
  //  )
  //
  //  val `null`        = P( "null" ).map(_ => Js.Null)
  //  val `false`       = P( "false" ).map(_ => Js.False)
  //  val `true`        = P( "true" ).map(_ => Js.True)
  //
  //  val hexDigit      = P( CharIn('0'to'9', 'a'to'f', 'A'to'F') )
  //  val unicodeEscape = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
  //  val escape        = P( "\\" ~ (CharIn("\"/\\bfnrt") | unicodeEscape) )
  //
  //  val strChars = P( CharsWhile(StringChars) )
  //  val string =
  //    P( space ~ "\"" ~/ (strChars | escape).rep.! ~ "\"").map(Js.Str)
  //
  //  val array =
  //    P( "[" ~/ jsonExpr.rep(sep=",".~/) ~ space ~ "]").map(Js.Arr(_:_*))
  //
  //  val pair = P( string.map(_.value) ~/ ":" ~/ jsonExpr )
  //
  //  val obj =
  //    P( "{" ~/ pair.rep(sep=",".~/) ~ space ~ "}").map(Js.Obj(_:_*))
  //
  //  val jsonExpr: P[Js.Val] = P(
  //    space ~ (obj | array | string | `true` | `false` | `null` | number) ~ space
  //  )
}
