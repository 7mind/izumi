package izumi.idealingua.translator.toscala.types

import izumi.idealingua.model.il.ast.typed.Interfaces
import izumi.idealingua.translator.toscala.STContext
import izumi.fundamentals.collections.IzCollections._

import scala.meta._


class CompositeStructure(ctx: STContext, val fields: ScalaStruct) {
  val t: ScalaType = ctx.conv.toScala(fields.id)

  import ScalaField._

  val composite: Interfaces = fields.fields.superclasses.interfaces

  val constructors: List[Defn.Def] = {
    val struct = fields.fields

    ctx.typespace.structure
      .constructors(struct)
      .map {
        cdef =>
          val constructorSignature = ctx.tools.makeParams(cdef)
          val fullConstructorCode = ctx.tools.makeConstructor(cdef)
          (cdef, constructorSignature, fullConstructorCode)
      }
      .distinctBy(_._2.types) // a fix for synthetic cornercase: https://github.com/7mind/izumi/issues/334
      .map {
      case (_, constructorSignature, fullConstructorCode) =>
        val instantiator = q" new ${t.typeFull}(..$fullConstructorCode) "

        q"""def apply(..${constructorSignature.params}): ${t.typeFull} = {
           ..${constructorSignature.assertion}
           $instantiator
         }"""
    }
  }


  val decls: List[Term.Param] = fields.all.toParams

  val names: List[Term.Name] = fields.all.toNames
}
