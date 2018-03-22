package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ILAstParsed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ILAstParsed._
import com.github.pshirshov.izumi.idealingua.model.il._
import fastparse.CharPredicates._
import fastparse.all._
import fastparse.{all, core}

import scala.language.implicitConversions


class ILParser {

  private implicit def toList[T](seq: Seq[T]): List[T] = seq.toList

  import IL._

  final val ws = P(" " | "\t")(sourcecode.Name("WS"))
  final val wss = P(ws.rep)
  final val wsm = P(ws.rep(1))

  final val NLC = P("\r\n" | "\n" | "\r")

  final val CommentChunk = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
  final val MultilineComment: P0 = P((wss ~ "/*" ~ CommentChunk.rep ~ "*/" ~ wss).rep(1))

  final val ShortComment = P(wss ~ "//" ~ CharsWhile(c => c != '\n' && c != '\r') ~ (NLC | End))

  final val SepInline = P(MultilineComment | wsm)
  final val SepInlineOpt = P(MultilineComment | wss)
  final val SepLineBase = P((NLC | ShortComment | (MultilineComment ~ (NLC | End))) ~ wss)
  final val SepLine = P(End | SepLineBase.rep(1))
  final val SepLineOpt = P(End | SepLineBase.rep)
  final val SepAnyOpt = P(SepInline | SepLineBase.rep)

  object kw {
    def kw(s: String): all.Parser[Unit] = P(s ~ SepInline)(sourcecode.Name(s"`$s`"))

    final val enum = kw("enum")
    final val adt = kw("adt")
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

  final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!


  final val pkgIdentifier = P(symbol.rep(sep = ".")) //.map(v => AbstractId(v))
  final val fqIdentifier = P(pkgIdentifier ~ "#" ~/ symbol).map(v => ILParsedId(v._1, v._2))
  final val shortIdentifier = P(symbol).map(v => ILParsedId(v))
  final val identifier = P(fqIdentifier | shortIdentifier)


  final val domainId = P(pkgIdentifier)
    .map(v => ILDomainId(DomainId(v.init, v.last)))
  final val domainBlock = P(kw.domain ~/ domainId)

  final val fulltype: all.Parser[AbstractTypeId] = P(SepInlineOpt ~ identifier ~ SepInlineOpt ~ generic.rep(min = 0, max = 1) ~ SepInlineOpt)
    .map(tp => tp._1.toGeneric(tp._2))


  final def generic: all.Parser[Seq[AbstractTypeId]] = P("[" ~/ SepInlineOpt ~ fulltype.rep(sep = ",") ~ SepInlineOpt ~ "]")

  final val field = P(SepInlineOpt ~ symbol ~ SepInlineOpt ~ ":" ~/ SepInlineOpt ~ fulltype ~ SepInlineOpt)
    .map {
      case (name, tpe) =>
        Field(tpe, name)
    }

  final val aggregate = P(field.rep(sep = SepLine))

  final val mixed = P(SepInlineOpt ~ "+" ~/ SepInlineOpt ~ identifier ~ SepInlineOpt)
  final val composite = P(mixed.rep(sep = SepLine))

  final val added = P(SepInlineOpt ~ "*" ~/ SepInlineOpt ~ identifier ~ SepInlineOpt)
  final val embedded = P(added.rep(sep = SepLine))


  final val sigParam = P(SepInlineOpt ~ identifier ~ SepInlineOpt)
  final val signature = P(sigParam.rep(sep = ","))


  final val defmethod = P(kw.defm ~/ SepInlineOpt ~ symbol ~ "(" ~ SepInlineOpt ~ signature ~ SepInlineOpt ~ ")" ~ SepInlineOpt ~ ":" ~ SepInlineOpt ~ "(" ~ SepInlineOpt ~ signature ~ SepInlineOpt ~ ")" ~ SepInlineOpt).map {
    case (name, in, out) =>
      DefMethod.RPCMethod(name, DefMethod.Signature(in.map(_.toMixinId), out.map(_.toMixinId)))
  }

  // other method kinds should be added here
  final val method: all.Parser[DefMethod] = P(SepInlineOpt ~ defmethod ~ SepInlineOpt)
  final val methods: Parser[Seq[DefMethod]] = P(method.rep(sep = SepLine))

  final val enumBlock = P(kw.enum ~/ symbol ~ SepInlineOpt ~ "{" ~ SepAnyOpt ~symbol.rep(min = 1, sep = SepAnyOpt) ~ SepAnyOpt ~ "}")
    .map(v => ILDef(Enumeration(ILParsedId(v._1).toEnumId, v._2.toList)))

  final val adtBlock = P(kw.adt ~/ symbol ~ SepInlineOpt ~ "{" ~ SepAnyOpt ~ identifier.rep(min = 1, sep = SepAnyOpt) ~ SepAnyOpt ~ "}")
    .map(v => ILDef(Adt(ILParsedId(v._1).toAdtId, v._2.map(_.toTypeId).toList)))

  final val aliasBlock = P(kw.alias ~/ symbol ~ SepInlineOpt ~ "=" ~ SepInlineOpt ~ identifier)
    .map(v => ILDef(Alias(ILParsedId(v._1).toAliasId, v._2.toTypeId)))

  final val idBlock = P(kw.id ~/ symbol ~ SepInlineOpt ~ "{" ~ (SepLineOpt ~ aggregate ~ SepLineOpt) ~ "}")
    .map(v => ILDef(Identifier(ILParsedId(v._1).toIdId, v._2)))

  final val mixinBlock = P(kw.mixin ~/ symbol ~ SepInlineOpt ~ "{" ~ (SepLineOpt ~ composite ~ SepLineOpt ~ embedded ~ SepLineOpt ~ aggregate ~ SepLineOpt) ~ "}")
    .map(v => ILDef(Interface(ILParsedId(v._1).toMixinId, v._2._3, v._2._1.map(_.toMixinId), v._2._2.map(_.toMixinId))))

  final val dtoBlock = P(kw.data ~/ symbol ~ SepInlineOpt ~ "{" ~ (SepLineOpt ~ composite ~ SepLineOpt ~ embedded ~ SepLineOpt) ~ "}")
    .map(v => ILDef(DTO(ILParsedId(v._1).toDataId, v._2._1.map(_.toMixinId), v._2._2.map(_.toMixinId))))

  final val serviceBlock = P(kw.service ~/ symbol ~ SepInlineOpt ~ "{" ~ (SepLineOpt ~ methods ~ SepLineOpt) ~ "}")
    .map(v => ILService(Service(ILParsedId(v._1).toServiceId, v._2)))

  final val includeBlock = P(kw.include ~/ SepInlineOpt ~ "\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
    .map(v => ILInclude(v))


  final val anyBlock: core.Parser[Val, Char, String] = enumBlock |
    adtBlock |
    aliasBlock |
    idBlock |
    mixinBlock |
    dtoBlock |
    serviceBlock |
    includeBlock

  final val importBlock = P(kw.`import` ~/ SepInlineOpt ~ domainId)

  final val modelDef = P(SepLineOpt ~ anyBlock.rep(sep = SepLineOpt) ~ SepLineOpt)

  final val fullDomainDef = P(domainBlock ~ SepLineOpt ~ importBlock.rep(sep = SepLineOpt) ~ modelDef).map {
    case (did, imports, defs) =>
      ParsedDomain(did, imports, defs)
  }
}
