package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.il.IL.{ILDef, ILInclude, ILService}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il._
import fastparse.CharPredicates._
import fastparse.all._
import fastparse.{all, core}


class ILParser {

  import IL._

  final val ws = P(" " | "\t")(sourcecode.Name("WS"))
  final val NL = P("\r\n" | "\n" | "\r")
  final val wsm = P(ws.rep(1))
  final val wso = P(ws.rep)
  final val Newline = P(NL.rep(1))
  final val empty = P((ws | NL).rep)

  object kw {
    def kw(s: String): all.Parser[Unit] = P(s ~ wsm)(sourcecode.Name(s"`$s`"))

    final val enum = kw("enum")
    final val alias = kw("alias")
    final val id = kw("id")
    final val mixin = kw("mixin")
    final val data = kw("data")
    final val service = kw("service")
    final val domain = kw("domain")
    final val defm = kw("def")
    final val include = kw("include")
    final val `import` = kw("import")
  }

  final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c)).rep).!


  final val pkgIdentifier = P(symbol.rep(sep = ".")) //.map(v => AbstractId(v))
  final val fqIdentifier = P(pkgIdentifier ~ "#" ~/ symbol).map(v => AbstractId(v._1, v._2))
  final val shortIdentifier = P(symbol).map(v => AbstractId(v))
  final val identifier = P(fqIdentifier | shortIdentifier)

  final val domainId = P(kw.domain ~/ pkgIdentifier)
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


  final val defmethod = P(kw.defm ~/ wso ~ symbol ~ "(" ~ wso ~ signature ~ wso ~ ")" ~ wso ~ ":" ~ wso ~ "(" ~ wso ~ signature ~ wso ~ ")" ~ wso).map {
    case (name, in, out) =>
      DefMethod.RPCMethod(name, DefMethod.Signature(in.map(_.toMixinId), out.map(_.toMixinId)))
  }

  // other method kinds should be added here
  final val method: all.Parser[DefMethod] = P(wso ~ defmethod ~ wso)
  final val methods: Parser[Seq[DefMethod]] = P(method.rep(sep = Newline))

  final val enumBlock = P(kw.enum ~/ symbol ~ wso ~ "{" ~ (empty ~ symbol ~ empty).rep(1) ~ "}")
    .map(v => ILDef(FinalDefinition.Enumeration(AbstractId(v._1).toEnumId, v._2.toList)))

  final val aliasBlock = P(kw.alias ~/ symbol ~ wso ~ "=" ~ wsm ~ identifier)
    .map(v => ILDef(FinalDefinition.Alias(AbstractId(v._1).toAliasId, v._2.toTypeId)))

  final val idBlock = P(kw.id ~/ symbol ~ wso ~ "{" ~ (empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Identifier(AbstractId(v._1).toIdId, v._2)))

  final val mixinBlock = P(kw.mixin ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty ~ aggregate ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.Interface(AbstractId(v._1).toMixinId, v._2._2, v._2._1.map(_.toMixinId))))

  final val dtoBlock = P(kw.data ~/ symbol ~ wso ~ "{" ~ (empty ~ composite ~ empty) ~ "}")
    .map(v => ILDef(FinalDefinition.DTO(AbstractId(v._1).toDataId, v._2.map(_.toMixinId))))

  final val serviceBlock = P(kw.service ~/ symbol ~ wso ~ "{" ~ (empty ~ methods ~ empty) ~ "}")
    .map(v => ILService(Service(AbstractId(v._1).toServiceId, v._2)))

  final val includeBlock = P(kw.include ~/ wso ~ "\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
    .map(v => ILInclude(v))


  final val anyBlock: core.Parser[Val, Char, String] = enumBlock |
    aliasBlock |
    idBlock |
    mixinBlock |
    dtoBlock |
    serviceBlock |
    includeBlock

  final val importBlock = P(kw.`import` ~/ wso ~ "\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")

  final val modelDef = P(empty ~ anyBlock.rep(sep = empty) ~ empty ~ End)

  final val fullDomainDef = P(domainId ~ Newline ~ importBlock.rep(sep = empty) ~ modelDef).map {
    case (did, imports, defs) =>
      ParsedDomain(did, imports, defs)
  }
}
