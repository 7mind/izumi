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

  object sym {
    final val NLC = P("\r\n" | "\n" | "\r")
    final val String = P("\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
  }

  object comments {
    final lazy val MultilineComment: P0 = {
      val CommentChunk = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
      P("/*" ~ CommentChunk.rep ~ "*/").rep(1)
    }

    final lazy val ShortComment = P("//" ~ CharsWhile(c => c != '\n' && c != '\r') ~ (sym.NLC | End))
  }

  object sep {
    private val ws = P(" " | "\t")(sourcecode.Name("WS"))
    private val wss = P(ws.rep)

    import comments._

    private val WsComment = wss ~ MultilineComment ~ wss
    private val SepLineBase = P(sym.NLC | (WsComment ~ (sym.NLC | End) | (wss ~ ShortComment)))

    final val line = P(End | SepLineBase.rep(1))
    final val inline = P(WsComment | wss)
    final val any = P(End |(wss ~ (WsComment | SepLineBase).rep ~ wss))

    final val sepAdt = P(ws | sym.NLC | "|")
  }

  import sep._

  object kw {
    def kw(s: String): Parser[Unit] = P(s ~ inline)(sourcecode.Name(s"`$s`"))

    def kw(s: String, alt: String*): Parser[Unit] = {
      val alts = alt.foldLeft(P(s)) { case (acc, v) => acc | v }
      P(alts ~ inline)(sourcecode.Name(s"`$s | $alt`"))
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

    def apply[T](kw: Parser[Unit], defparser: Parser[T]): Parser[T] = {
      P(kw ~/ defparser)
    }
  }


  object ids {
    final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!
    final val pkgIdentifier = P(symbol.rep(sep = "."))
    final val fqIdentifier = P(pkgIdentifier ~ "#" ~/ symbol).map(v => ParsedId(v._1, v._2))
    final val shortIdentifier = P(symbol).map(v => ParsedId(v))
    final val identifier = P(fqIdentifier | shortIdentifier)

    final lazy val fulltype: Parser[AbstractTypeId] = P(inline ~ identifier ~ inline ~ generic.rep(min = 0, max = 1) ~ inline)
      .map(tp => tp._1.toGeneric(tp._2))

    final lazy val generic = P("[" ~/ inline ~ fulltype.rep(sep = ",") ~ inline ~ "]")
  }

  object defs {
    final val field = P(ids.symbol ~ inline ~ ":" ~/ inline ~ ids.fulltype)
      .map {
        case (name, tpe) =>
          RawField(tpe, name)
      }


    final val struct = {
      val sepEntry = line
      val sepInline = inline

      val plus = P(("&" ~ "&&".?) ~/ sepInline ~ ids.identifier).map(_.toMixinId).map(StructOp.Extend)
      val embed = P((("+" ~ "++".?) | "...") ~/ sepInline ~ ids.identifier).map(_.toMixinId).map(StructOp.Mix)
      val minus = P(("-" ~ "--".?) ~/ sepInline ~ (field | ids.identifier)).map {
        case v: RawField =>
          StructOp.RemoveField(v)
        case i: ParsedId =>
          StructOp.Drop(i.toMixinId)
      }
      val plusField = field.map(StructOp.AddField)

      val anyPart = P(plusField | plus | embed | minus)

      P((sepInline ~ anyPart ~ sepInline).rep(sep = sepEntry))
        .map(ParsedStruct.apply)
    }


    final val simpleStruct = {
      val sepInline = any
      val embed = P((("+" ~ "++".?) | "...") ~/ sepInline ~ ids.identifier).map(_.toMixinId).map(StructOp.Mix)
      val plusField = field.map(StructOp.AddField)
      val anyPart = P(plusField | embed)
      val sepInlineStruct = any ~ ",".? ~ any

      P((sepInline ~ anyPart ~ sepInline).rep(sep = sepInlineStruct))
        .map(ParsedStruct.apply).map(s => RawSimpleStructure(s.structure.concepts, s.structure.fields))
    }

    final val aggregate = P((inline ~ field ~ inline)
      .rep(sep = line))

    final val adt = P(ids.identifier.rep(min = 1, sep = sepAdt.rep(min = 1)))
      .map(v => AlgebraicType(v.map(_.toTypeId).toList))

    final val enum = P(ids.symbol.rep(min = 1, sep = any))
  }

  object structure {
    def enclosed[T](defparser: Parser[T]): Parser[T] = {
      P(("{" ~ any ~ defparser ~ any ~ "}") | "(" ~ any ~ defparser ~ any ~ ")")
    }

    def starting[T](keyword: Parser[Unit], defparser: Parser[T]): Parser[(ParsedId, T)] = {
      kw(keyword, ids.shortIdentifier ~ inline ~ defparser)
    }

    def block[T](keyword: Parser[Unit], defparser: Parser[T]): Parser[(ParsedId, T)] = {
      starting(keyword, enclosed(defparser))
    }
  }



  object services {
    final val sigSep = P("=>" | "->") // ":"
    final val inlineStruct = structure.enclosed(defs.simpleStruct)
    final val adtOut = structure.enclosed(defs.adt)

    final val defMethod = P(
      kw.defm ~ inline ~
        ids.symbol ~ any ~
        inlineStruct ~ any ~
        sigSep ~ any ~
        (adtOut | inlineStruct)
    ).map {
      case (id, in, out: RawSimpleStructure) =>
        DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Usual(out)))

      case (id, in, out: AlgebraicType) =>
        DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Algebraic(out.alternatives)))

      case f =>
        throw new IllegalStateException(s"Impossible case: $f")
    }


    final val sigParam = P(inline ~ ids.identifier ~ inline)
    final val signature = P(sigParam.rep(sep = ","))
    final val defmethodDeprecated = P(kw.defm ~ inline ~ ids.symbol ~ "(" ~ inline ~ signature ~ inline ~ ")" ~ inline ~
      ":" ~ inline ~ "(" ~ inline ~ signature ~ inline ~ ")" ~ inline)
      .map {
        case (name, in, out) =>
          DefMethod.DeprecatedMethod(name, DefMethod.DeprecatedSignature(in.map(_.toMixinId).toList, out.map(_.toMixinId).toList))
      }

    // other method kinds should be added here
    final val method: Parser[DefMethod] = P(defMethod | defmethodDeprecated)
    final val methods: Parser[Seq[DefMethod]] = P(any ~ method.rep(sep = any) ~ any)
  }

  object blocks {
    final val inclusion = kw(kw.include, sym.String)
      .map(v => ILInclude(v))

    final val aliasBlock = structure.starting(kw.alias, "=" ~ inline ~ ids.identifier)
      .map(v => ILDef(Alias(v._1.toAliasId, v._2.toTypeId)))

    final val mixinBlock = structure.block(kw.mixin, defs.struct)
      .map(v => ILDef(v._2.toInterface(v._1.toMixinId)))

    final val dtoBlock = structure.block(kw.data, defs.struct)
      .map(v => ILDef(v._2.toDto(v._1.toDataId)))

    final val adtBlock = structure.block(kw.adt, defs.adt)
      .map(v => ILDef(Adt(v._1.toAdtId, v._2.alternatives)))

    final val idBlock = structure.block(kw.id, defs.aggregate)
      .map(v => ILDef(Identifier(v._1.toIdId, v._2.toList)))

    final val enumBlock = structure.block(kw.enum, defs.enum)
      .map(v => ILDef(Enumeration(v._1.toEnumId, v._2.toList)))

    final val serviceBlock = structure.block(kw.service, services.methods)
      .map(v => ILService(Service(v._1.toServiceId, v._2.toList)))

    final val anyBlock: Parser[Val] = enumBlock |
      adtBlock |
      aliasBlock |
      idBlock |
      mixinBlock |
      dtoBlock |
      serviceBlock |
      inclusion
  }

  object domains {
    final val domainId = P(ids.pkgIdentifier)
      .map(v => ILDomainId(DomainId(v.init, v.last)))
    final val domainBlock = P(kw.domain ~/ domainId)
    final val importBlock = kw(kw.`import`, domainId)
  }

  final val modelDef = P(any ~ blocks.anyBlock.rep(sep = any) ~ any).map {
    defs =>
      ParsedModel(defs)
  }

  final val fullDomainDef = P(domains.domainBlock ~ any ~ domains.importBlock.rep(sep = any) ~ modelDef).map {
    case (did, imports, defs) =>
      ParsedDomain(did, imports, defs)
  }
}
