package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Interfaces
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext

import scala.meta._


class CompositeStructure(ctx: STContext, val fields: ScalaStruct) {
  val t: ScalaType = ctx.conv.toScala(fields.id)

  import ScalaField._

  val composite: Interfaces = fields.fields.superclasses.interfaces

  val constructors: List[Defn.Def] = {
    val struct = fields.fields

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    ctx.typespace.structure
      .constructors(struct)
      .map {
        cdef =>
          val constructorSignature = ctx.tools.makeParams(cdef)
          val fullConstructorCode = ctx.tools.makeConstructor(cdef)
          (cdef, constructorSignature, fullConstructorCode)
      }
      .distinctBy(_._2.types) // a fix for synthetic cornercase: https://github.com/pshirshov/izumi-r2/issues/334
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
