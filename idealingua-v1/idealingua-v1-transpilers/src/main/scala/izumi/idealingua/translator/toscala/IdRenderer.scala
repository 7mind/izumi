package izumi.idealingua.translator.toscala

import izumi.idealingua.model.common.PrimitiveId
import izumi.idealingua.model.common.TypeId.{EnumId, IdentifierId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.TypeDef.Identifier
import izumi.idealingua.translator.toscala.products.{CogenProduct, RenderableCogenProduct}

import scala.meta._

class IdRenderer(ctx: STContext) {
  import ctx._
  import conv._


  def renderIdentifier(i: Identifier): RenderableCogenProduct = {
    val fields = typespace.structure.structure(i).toScala
    val decls = fields.all.toParams
    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)
    val tools = t.within(s"${i.id.name}Extensions")

    val qqTools = q"""implicit class ${tools.typeName}(_value: ${t.typeFull}) { }"""

    val sortedFields = fields.all.sortBy(_.field.field.name)

    val parsers = sortedFields
      .zipWithIndex
      .map { case (field, idx) => (field, idx, field.field.field.typeId) }
      .map {
        case (field, idx, t: EnumId) =>
          q"${field.name} = ${conv.toScala(t).termFull}.parse(parts(${Lit.Int(idx)}))"
        case (field, idx, t: IdentifierId) =>
          q"${field.name} = ${conv.toScala(t).termFull}.parse(parts(${Lit.Int(idx)}))"
        case (field, idx, _: PrimitiveId) =>
          q"${field.name} = parsePart[${field.fieldType}](parts(${Lit.Int(idx)}), classOf[${field.fieldType}])"
        case o =>
          throw new IDLException(s"Impossible case/id field: $o")
      }

    val parts = sortedFields.map(fi => q"this.${fi.name}")

    val superClasses = List(rt.generated.init(), rt.tIDLIdentifier.init())

    val errorInterp = Term.Interpolate(Term.Name("s"), List(Lit.String("Serialized form of "), Lit.String(s" should start with $typeName#")), List(Term.Name("name")))


    val qqCompanion =
      q"""object ${t.termName} {
            def parse(s: String): ${t.typeName} = {
              import ${rt.tIDLIdentifier.termBase}._
              if (!s.startsWith(${Lit.String(typeName.toString + "#")})) {
                val name = ${Lit.String(i.id.toString)}
                throw new IllegalArgumentException($errorInterp)
              }
              val withoutPrefix = s.substring(s.indexOf("#") + 1)
              val parts = withoutPrefix.split(':').map(part => unescape(part))
              ${t.termName}(..$parsers)
            }
      }"""

    val qqIdentifier =
      q"""final case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              import ${rt.tIDLIdentifier.termBase}._
              val suffix = Seq(..$parts).map(part => escape(part.toString)).mkString(":")
              $interp
            }
         }"""


    ext.extend(i, CogenProduct(qqIdentifier, qqCompanion, qqTools, List.empty), _.handleIdentifier)
  }

}
