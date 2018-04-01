package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.parsing.ILAstParsed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.parsing.ILAstParsed._
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.parsing.{ILAstParsed, ILParsedId}
import fastparse.CharPredicates._
import fastparse.all._

import scala.language.implicitConversions

sealed trait StructOp

object StructOp {

  case class Extend(tpe: TypeId.InterfaceId) extends StructOp

  case class Mix(tpe: TypeId.InterfaceId) extends StructOp

  case class Drop(tpe: TypeId.InterfaceId) extends StructOp

  case class AddField(field: Field) extends StructOp

  case class RemoveField(field: Field) extends StructOp

}

case class ParsedStruct(inherited: List[InterfaceId], mixed: List[InterfaceId], removed: List[InterfaceId], fields: List[Field], removedFields: List[Field]) {
  def toInterface(id: InterfaceId): ILAstParsed.Interface = {
    Interface(id, fields, inherited, mixed)
  }

  def toDto(id: DTOId): ILAstParsed.DTO = {
    DTO(id, fields, inherited, mixed)
  }
}

object ParsedStruct {
  def apply(v: Seq[StructOp]): ParsedStruct = {
    import StructOp._
    ParsedStruct(
      v.collect({ case Extend(i) => i }).toList
      , v.collect({ case Mix(i) => i }).toList
      , v.collect({ case Drop(i) => i }).toList
      , v.collect({ case AddField(i) => i }).toList
      , v.collect({ case RemoveField(i) => i }).toList
    )
  }
}


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
    def kw(s: String): Parser[Unit] = P(s ~ SepInline)(sourcecode.Name(s"`$s`"))

    def kw(s: String, alt: String*): Parser[Unit] = {
      val alts = alt.foldLeft(P(s)) { case (acc, v) => acc | v }
      P(alts ~ SepInline)(sourcecode.Name(s"`$s | $alt`"))
    }

    final val domain = kw("domain", "package", "namespace")
    final val include = kw("include")
    final val `import` = kw("import")

    final val enum = kw("enum")
    final val adt = kw("adt")
    final val alias = kw("alias", "type", "using")
    final val id = kw("id")
    final val mixin = kw("mixin")
    final val data = kw("data")
    final val service = kw("service")

    final val defm = kw("def", "fn", "fun")

  }

  final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!


  final val pkgIdentifier = P(symbol.rep(sep = "."))
  final val fqIdentifier = P(pkgIdentifier ~ "#" ~/ symbol).map(v => ILParsedId(v._1, v._2))
  final val shortIdentifier = P(symbol).map(v => ILParsedId(v))
  final val identifier = P(fqIdentifier | shortIdentifier)

  final val fulltype: Parser[AbstractTypeId] = P(SepInlineOpt ~ identifier ~ SepInlineOpt ~ generic.rep(min = 0, max = 1) ~ SepInlineOpt)
    .map(tp => tp._1.toGeneric(tp._2))


  final def generic: Parser[Seq[AbstractTypeId]] = P("[" ~/ SepInlineOpt ~ fulltype.rep(sep = ",") ~ SepInlineOpt ~ "]")

  val field: Parser[Field] = P(symbol ~ SepInlineOpt ~ ":" ~/ SepInlineOpt ~ fulltype)
    .map {
      case (name, tpe) =>
        Field(tpe, name)
    }


  def struct(sepEntry: Parser[Unit]): Parser[ParsedStruct] = {
    val sepInline = SepInlineOpt
    val margin = SepLineOpt

    val plus = P(("+" ~ "++".?) ~/ sepInline ~ identifier).map(_.toMixinId).map(StructOp.Extend)
    val embed = P(("*" | "...") ~/ sepInline ~ identifier).map(_.toMixinId).map(StructOp.Mix)
    val minus = P(("-" ~ "--".?) ~/ sepInline ~ (field | identifier)).map {
      case v: Field =>
        StructOp.RemoveField(v)
      case i: ILParsedId =>
        StructOp.Drop(i.toMixinId)
    }
    val plusField = field.map(StructOp.AddField)

    val anyPart = P(plusField | plus | embed | minus)

    P(margin ~ (sepInline ~ anyPart ~ sepInline).rep(sep = sepEntry) ~ margin)
      .map {
        v =>
          ParsedStruct(v)
      }
  }

  final val aggregate = P((SepInlineOpt ~ field ~ SepInlineOpt).rep(sep = SepLine))

  object services {
    final val sigParam = P(SepInlineOpt ~ identifier ~ SepInlineOpt)
    final val signature = P(sigParam.rep(sep = ","))


    final val defmethod = P(kw.defm ~/ SepInlineOpt ~ symbol ~ "(" ~ SepInlineOpt ~ signature ~ SepInlineOpt ~ ")" ~ SepInlineOpt ~ ":" ~ SepInlineOpt ~ "(" ~ SepInlineOpt ~ signature ~ SepInlineOpt ~ ")" ~ SepInlineOpt).map {
      case (name, in, out) =>
        DefMethod.RPCMethod(name, DefMethod.Signature(in.map(_.toMixinId), out.map(_.toMixinId)))
    }

    // other method kinds should be added here
    final val method: Parser[DefMethod] = P(SepInlineOpt ~ defmethod ~ SepInlineOpt)
    final val methods: Parser[Seq[DefMethod]] = P(method.rep(sep = SepLine))
  }

  object blocks {
    final val includeBlock = P(kw.include ~/ SepInlineOpt ~ "\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
      .map(v => ILInclude(v))

    final val blockStruct = struct(SepLine)

    final val mixinBlock = P(kw.mixin ~/ shortIdentifier ~ SepInlineOpt ~ "{" ~ blockStruct ~ "}")
      .map(v => ILDef(v._2.toInterface(v._1.toMixinId)))

    final val dtoBlock = P(kw.data ~/ shortIdentifier ~ SepInlineOpt ~ "{" ~ blockStruct ~ "}")
      .map(v => ILDef(v._2.toDto(v._1.toDataId)))

    final val idBlock = P(kw.id ~/ shortIdentifier ~ SepInlineOpt ~ "{" ~ (SepLineOpt ~ aggregate ~ SepLineOpt) ~ "}")
      .map(v => ILDef(Identifier(v._1.toIdId, v._2)))

    final val enumBlock = P(kw.enum ~/ shortIdentifier ~ SepInlineOpt ~ "{" ~ SepAnyOpt ~ symbol.rep(min = 1, sep = SepAnyOpt) ~ SepAnyOpt ~ "}")
      .map(v => ILDef(Enumeration(v._1.toEnumId, v._2.toList)))

    final val adtBlock = P(kw.adt ~/ shortIdentifier ~ SepInlineOpt ~ "{" ~ SepAnyOpt ~ identifier.rep(min = 1, sep = SepAnyOpt) ~ SepAnyOpt ~ "}")
      .map(v => ILDef(Adt(v._1.toAdtId, v._2.map(_.toTypeId).toList)))

    final val aliasBlock = P(kw.alias ~/ shortIdentifier ~ SepInlineOpt ~ "=" ~ SepInlineOpt ~ identifier)
      .map(v => ILDef(Alias(v._1.toAliasId, v._2.toTypeId)))

    final val serviceBlock = P(kw.service ~/ shortIdentifier ~ SepInlineOpt ~ "{" ~ (SepLineOpt ~ services.methods ~ SepLineOpt) ~ "}")
      .map(v => ILService(Service(v._1.toServiceId, v._2)))

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
    final val importBlock = P(kw.`import` ~/ SepInlineOpt ~ domainId)

  }

  final val modelDef = P(SepLineOpt ~ blocks.anyBlock.rep(sep = SepLineOpt) ~ SepLineOpt).map {
    defs =>
      ParsedModel(defs)
  }

  final val fullDomainDef = P(domains.domainBlock ~ SepLineOpt ~ domains.importBlock.rep(sep = SepLineOpt) ~ modelDef).map {
    case (did, imports, defs) =>
      ParsedDomain(did, imports, defs)
  }
}
