package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.il.IL.{ILDef, ILService}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il._
import fastparse.CharPredicates._
import fastparse.all._
import fastparse.{all, core}


case class ParsedDomain(domain: DomainDefinition) {
  def extend(defs: Seq[IL.Val]): ParsedDomain = {
    val types = defs.collect({ case d: ILDef => d.v })
    val services = defs.collect({ case d: ILService => d.v })

    val extendedDomain = domain.copy(
      types = domain.types ++ types
      , services = domain.services ++ services
    )

    this.copy(domain = extendedDomain)
  }
}

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
  final val defm = W("def")

  final val pkgIdentifier = P(symbol.rep(sep = ".")) //.map(v => AbstractId(v))
  final val fqIdentifier = P(pkgIdentifier ~ "#" ~/ symbol).map(v => AbstractId(v._1, v._2))
  final val shortIdentifier = P(symbol).map(v => AbstractId(v))
  final val identifier = P(fqIdentifier | shortIdentifier)

  final val domainId = P(domain ~/ pkgIdentifier)
    .map(v => ILDomainId(DomainId(v.init, v.last)))

  def toScalar(tid: TypeId): Scalar = {
    tid.name match {
      case "i32" if tid.pkg.isEmpty =>
        Primitive.TInt32
      case "i64" if tid.pkg.isEmpty =>
        Primitive.TInt64
      case "str" if tid.pkg.isEmpty =>
        Primitive.TString
      case _ =>
        AbstractId(tid.pkg, tid.name).toIdId
    }
  }

  final val fulltype: all.Parser[TypeId] = P(wso ~ identifier ~ wso ~ generic.rep(min = 0, max = 1) ~ wso).map {
    case (tid, params) if params.isEmpty =>
      tid.toTypeId
    case (tid, params) if tid.id == "set" && tid.pkg.isEmpty =>
      Generic.TSet(params.flatten.head)
    case (tid, params) if tid.id == "list" && tid.pkg.isEmpty =>
      Generic.TList(params.flatten.head)
    case (tid, params) if tid.id == "map" && tid.pkg.isEmpty =>
      Generic.TMap(toScalar(params.flatten.head), params.flatten.last)
  }

  final def generic: all.Parser[Seq[TypeId]] = P("[" ~/ wso ~ fulltype.rep(sep = ",") ~ wso ~ "]")

  final val field = P(wso ~ symbol ~ wso ~ ":" ~/ wso ~ fulltype ~ wso)
    .map {
      case (name, tpe) =>
        Field(tpe, name)
    }

  final val aggregate = P(field.rep(sep = Newline))
  final val mixed = P(wso ~ "+" ~/ wso ~ identifier ~ wso)
  final val composite = P(mixed.rep(sep = Newline))

  final val sigParam = P(wso ~ identifier ~ wso)
  final val signature = P(sigParam.rep(sep = ","))


  val defmethod = P(defm ~/ wso ~ symbol ~ "(" ~ wso ~ signature ~ wso ~ ")" ~ wso ~ ":" ~ wso ~ "(" ~ wso ~ signature ~ wso ~ ")" ~ wso).map {
    case (name, in, out) =>
      DefMethod.RPCMethod(name, DefMethod.Signature(in.map(_.toMixinId), out.map(_.toMixinId)))
  }
  val method: all.Parser[DefMethod] = P(wso ~ (defmethod) ~ wso)
  final val methods: Parser[Seq[DefMethod]] = P(method.rep(sep = Newline))

  final val enumBlock = P(enum ~/ symbol ~ wso ~ "{" ~ (empty ~ symbol ~ empty).rep(1) ~ "}")
    .map(v => ILDef(FinalDefinition.Enumeration(AbstractId(v._1).toEnumId, v._2.toList)))

  final val aliasBlock = P(alias ~/ symbol ~ wso ~ "=" ~ wsm ~ identifier)
    .map(v => ILDef(FinalDefinition.Alias(AbstractId(v._1).toAliasId, v._2.toTypeId)))

  final val idBlock = P(id ~/ symbol ~ wso ~ "{" ~ (empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Identifier(AbstractId(v._1).toIdId, v._2)))

  final val mixinBlock = P(mixin ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Interface(AbstractId(v._1).toMixinId, v._2._2, v._2._1.map(_.toMixinId))))

  final val dtoBlock = P(data ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.DTO(AbstractId(v._1).toDataId, v._2.map(_.toMixinId))))

  final val serviceBlock = P(service ~/ symbol ~ wso ~ "{" ~ (empty ~ methods ~ empty) ~ "}")
    .map(v => ILService(Service(AbstractId(v._1).toServiceId, v._2)))

  final val anyBlock: core.Parser[Val, Char, String] = enumBlock |
    aliasBlock |
    idBlock |
    mixinBlock |
    dtoBlock |
    serviceBlock

  final val modelDef = P(empty ~ anyBlock.rep(sep = empty) ~ empty ~ End)

  final val fullDomainDef = P(domainId ~ Newline ~ modelDef).map {
    case (did, defs) =>
      ParsedDomain(DomainDefinition(did.v, Seq.empty, Seq.empty)).extend(defs)
  }
}
