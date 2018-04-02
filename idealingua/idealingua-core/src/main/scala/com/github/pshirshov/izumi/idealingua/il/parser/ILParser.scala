package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.model.{AlgebraicType, ParsedDomain, ParsedModel}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.Service._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainId
import fastparse.CharPredicates._
import fastparse.all._


class ILParser {

  import IL._

  object Symbols {
    final val NLC = P("\r\n" | "\n" | "\r")
  }

  object Comments {
    final lazy val MultilineComment: P0 = {
      val CommentChunk = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
      P("/*" ~ CommentChunk.rep ~ "*/").rep(1)
    }

    final lazy val ShortComment = P("//" ~ CharsWhile(c => c != '\n' && c != '\r') ~ (Symbols.NLC | End))
  }

  class Separators(main: Parser[Unit]) {
    private val ws = P(" " | "\t")(sourcecode.Name("WS"))
    private val wss = P(ws.rep)
    private val wsm = P(ws.rep(1))

    import Comments._
    private val WsComment = wss ~ MultilineComment ~ wss
    private val SepLineBase = P(main | (wss ~ ShortComment) | (WsComment ~ (main | End)))

    final val SepInline = P(WsComment | wsm)
    final val SepLine = P(End | SepLineBase.rep(1))
    final val SepAdt= P(ws | Symbols.NLC | "|")
    
    object opt {
      final val SepInlineOpt = P(WsComment | wss)
      final val SepLineOpt = P(End | SepLineBase.rep)
      final val SepAnyOpt = P(wss ~ (SepInline | SepLineBase.rep) ~ wss)
    }


  }

  object Separators extends Separators(Symbols.NLC)

  import Separators._

  object SigSeparators extends Separators(P(Symbols.NLC | ","))

  object kw {
    def kw(s: String): Parser[Unit] = P(s ~ SepInline)(sourcecode.Name(s"`$s`"))

    def kw(s: String, alt: String*): Parser[Unit] = {
      val alts = alt.foldLeft(P(s)) { case (acc, v) => acc | v }
      P(alts ~ SepInline)(sourcecode.Name(s"`$s | $alt`"))
    }

    final val domain = kw("domain", "package", "namespace")
    final val include = kw("include")
    final val `import` = kw("import")

    final val enum = kw("enum")
    final val adt = kw("adt", "choice")
    final val alias = kw("alias", "type", "using")
    final val id = kw("id")
    final val mixin = kw("mixin", "interface")
    final val data = kw("data", "dto", "struct")
    final val service = kw("service")

    final val defm = kw("def", "fn", "fun")

  }

  final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!


  final val pkgIdentifier = P(symbol.rep(sep = "."))
  final val fqIdentifier = P(pkgIdentifier ~ "#" ~/ symbol).map(v => ParsedId(v._1, v._2))
  final val shortIdentifier = P(symbol).map(v => ParsedId(v))
  final val identifier = P(fqIdentifier | shortIdentifier)

  final val fulltype: Parser[AbstractTypeId] = P(opt.SepInlineOpt ~ identifier ~ opt.SepInlineOpt ~ generic.rep(min = 0, max = 1) ~ opt.SepInlineOpt)
    .map(tp => tp._1.toGeneric(tp._2))


  final def generic: Parser[Seq[AbstractTypeId]] = P("[" ~/ opt.SepInlineOpt ~ fulltype.rep(sep = ",") ~ opt.SepInlineOpt ~ "]")

  val field: Parser[RawField] = P(symbol ~ opt.SepInlineOpt ~ ":" ~/ opt.SepInlineOpt ~ fulltype)
    .map {
      case (name, tpe) =>
        RawField(tpe, name)
    }


  def struct(sepEntry: Parser[Unit]): Parser[ParsedStruct] = {
    val sepInline = opt.SepInlineOpt
    val margin = opt.SepLineOpt

    val plus = P(("+" ~ "++".?) ~/ sepInline ~ identifier).map(_.toMixinId).map(StructOp.Extend)
    val embed = P(("*" | "...") ~/ sepInline ~ identifier).map(_.toMixinId).map(StructOp.Mix)
    val minus = P(("-" ~ "--".?) ~/ sepInline ~ (field | identifier)).map {
      case v: RawField =>
        StructOp.RemoveField(v)
      case i: ParsedId =>
        StructOp.Drop(i.toMixinId)
    }
    val plusField = field.map(StructOp.AddField)

    val anyPart = P(plusField | plus | embed | minus)

    P(margin ~ (sepInline ~ anyPart ~ sepInline).rep(sep = sepEntry) ~ margin)
      .map(ParsedStruct.apply)
  }

  def simpleStruct: Parser[RawSimpleStructure] = {
    val sepInline = opt.SepAnyOpt
    val embed = P(("*" | "...") ~/ sepInline ~ identifier).map(_.toMixinId).map(StructOp.Mix)
    val plusField = field.map(StructOp.AddField)

    val anyPart = P(plusField | embed)

    val sepInlineStruct = P(opt.SepInlineOpt ~ SigSeparators.SepLine ~ opt.SepInlineOpt)

    P((sepInline ~ anyPart ~ sepInline).rep(sep = sepInlineStruct))
      .map(ParsedStruct.apply).map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
  }

  final val aggregate = P((opt.SepInlineOpt ~ field ~ opt.SepInlineOpt).rep(sep = SepLine))
  final val adt: Parser[AlgebraicType] = {
    P(opt.SepAnyOpt ~ identifier.rep(min = 1, sep = SepAdt.rep(min = 1)) ~ opt.SepAnyOpt).map(v => AlgebraicType(v.map(_.toTypeId).toList))
  }


  object services {
    final val sigSep = P("=>" | "->") // ":"
    final val wsAny = P(opt.SepInlineOpt ~ opt.SepLineOpt ~ opt.SepInlineOpt)
    final val inlineStruct = P("(" ~ simpleStruct ~ ")")
    final val adtOut = P("(" ~ adt ~ ")")

    final val defmethodEx = P(
      kw.defm ~ opt.SepInlineOpt ~
        symbol ~ wsAny ~
        inlineStruct ~ wsAny ~
        sigSep ~ wsAny ~
        (adtOut | inlineStruct)
    ).map {
      case (id, in, out: RawSimpleStructure) =>
        DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Usual(out)))

      case (id, in, out: AlgebraicType) =>
        DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Algebraic(out.alternatives)))

      case f =>
        throw new IllegalStateException(s"Impossible case: $f")
    }


    final val sigParam = P(opt.SepInlineOpt ~ identifier ~ opt.SepInlineOpt)
    final val signature = P(sigParam.rep(sep = ","))
    final val defmethod = P(kw.defm ~ opt.SepInlineOpt ~ symbol ~ "(" ~ opt.SepInlineOpt ~ signature ~ opt.SepInlineOpt ~ ")" ~ opt.SepInlineOpt ~
      ":" ~ opt.SepInlineOpt ~ "(" ~ opt.SepInlineOpt ~ signature ~ opt.SepInlineOpt ~ ")" ~ opt.SepInlineOpt)
      .map {
        case (name, in, out) =>
          DefMethod.DeprecatedMethod(name, DefMethod.DeprecatedSignature(in.map(_.toMixinId).toList, out.map(_.toMixinId).toList))
      }

    // other method kinds should be added here
    final val method: Parser[DefMethod] = P(opt.SepInlineOpt ~ (defmethod | defmethodEx) ~ opt.SepInlineOpt)
    final val methods: Parser[Seq[DefMethod]] = P(method.rep(sep = SepLine))
  }


  object blocks {
    final val includeBlock = P(kw.include ~/ opt.SepInlineOpt ~ "\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
      .map(v => ILInclude(v))

    final val blockStruct = struct(SepLine)

    final val mixinBlock = P(kw.mixin ~/ shortIdentifier ~ opt.SepInlineOpt ~ "{" ~ blockStruct ~ "}")
      .map(v => ILDef(v._2.toInterface(v._1.toMixinId)))

    final val dtoBlock = P(kw.data ~/ shortIdentifier ~ opt.SepInlineOpt ~ "{" ~ blockStruct ~ "}")
      .map(v => ILDef(v._2.toDto(v._1.toDataId)))

    final val idBlock = P(kw.id ~/ shortIdentifier ~ opt.SepInlineOpt ~ "{" ~ (opt.SepLineOpt ~ aggregate ~ opt.SepLineOpt) ~ "}")
      .map(v => ILDef(Identifier(v._1.toIdId, v._2.toList)))

    final val enumBlock = P(kw.enum ~/ shortIdentifier ~ opt.SepInlineOpt ~ "{" ~ opt.SepAnyOpt ~ symbol.rep(min = 1, sep = opt.SepAnyOpt) ~ opt.SepAnyOpt ~ "}")
      .map(v => ILDef(Enumeration(v._1.toEnumId, v._2.toList)))

    final val adtBlock = P(kw.adt ~/ shortIdentifier ~ opt.SepInlineOpt ~ "{" ~ adt ~ "}")
      .map(v => ILDef(Adt(v._1.toAdtId, v._2.alternatives)))

    final val aliasBlock = P(kw.alias ~/ shortIdentifier ~ opt.SepInlineOpt ~ "=" ~ opt.SepInlineOpt ~ identifier)
      .map(v => ILDef(Alias(v._1.toAliasId, v._2.toTypeId)))

    final val serviceBlock = P(kw.service ~/ shortIdentifier ~ opt.SepInlineOpt ~ "{" ~ (opt.SepLineOpt ~ services.methods ~ opt.SepLineOpt) ~ "}")
      .map(v => ILService(Service(v._1.toServiceId, v._2.toList)))

    final val anyBlock: Parser[Val] = enumBlock |
      adtBlock |
      aliasBlock |
      idBlock |
      mixinBlock |
      dtoBlock |
      serviceBlock |
      includeBlock
  }

  object domains {
    final val domainId = P(pkgIdentifier)
      .map(v => ILDomainId(DomainId(v.init, v.last)))

    final val domainBlock = P(kw.domain ~/ domainId)
    final val importBlock = P(kw.`import` ~/ opt.SepInlineOpt ~ domainId)

  }

  final val modelDef = P(opt.SepLineOpt ~ blocks.anyBlock.rep(sep = opt.SepLineOpt) ~ opt.SepLineOpt).map {
    defs =>
      ParsedModel(defs)
  }

  final val fullDomainDef = P(domains.domainBlock ~ opt.SepLineOpt ~ domains.importBlock.rep(sep = opt.SepLineOpt) ~ modelDef).map {
    case (did, imports, defs) =>
      ParsedDomain(did, imports, defs)
  }
}
