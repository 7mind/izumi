package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.{SigParam, SigParamSource}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Interfaces
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext

import scala.meta._


class CompositeStructure(ctx: STContext, val fields: ScalaStruct) {
  val t: ScalaType = ctx.conv.toScala(fields.id)

  import ScalaField._

  val composite: Interfaces = fields.fields.superclasses.interfaces

  val constructors: List[Defn.Def] = {
    val local = fields.fields.localOrAmbigious
    val localNames = local.map(_.field.name).distinct
    val localNamesSet = localNames.toSet


    val constructorCode = fields.fields.all
      .filterNot(f => localNamesSet.contains(f.field.name))
      .map(f => SigParam(f.field.name, SigParamSource(f.defn.definedBy, ctx.typespace.tools.idToParaName(f.defn.definedBy)), Some(f.field.name)))

    val constructorCodeNonUnique = local
      .map(f => SigParam(f.field.name, SigParamSource(f.field.typeId, f.field.name), None))

    val cdef = ctx.typespace.tools.mkConverter(List.empty, constructorCode ++ constructorCodeNonUnique, fields.id)
    val constructorSignature: List[Term.Param] =  ctx.tools.makeParams(cdef)
    val fullConstructorCode = ctx.tools.makeConstructor(cdef) //.allFields.map(ctx.tools.toAssignment)

    val instantiator =
      q"""
         ${t.termFull}(..$fullConstructorCode)
         """

    List(
      q"""def apply(..$constructorSignature): ${t.typeFull} = {
         $instantiator
         }"""
    )

  }

  val decls: List[Term.Param] = fields.all.toParams

  val names: List[Term.Name] = fields.all.toNames
}
