package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgName
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.results._


trait Interpreter {
  def dispatch(defn: RawTopLevelDefn.TypeDefn): TList
}

object Interpreter {

  case class Args(types: Map[IzTypeId, ProcessedOp], templateArgs: Map[IzTypeArgName, IzTypeReference])

}


class InterpreterImpl(
                       typedefSupport: TypedefSupport,
                       adtSupport: AdtSupport,
                       templateSupport: TemplateSupport,
                       cloneSupport: CloneSupport
                     ) extends Interpreter {


  def dispatch(defn: RawTopLevelDefn.TypeDefn): TList = {
    defn match {
      case RawTopLevelDefn.TLDBaseType(v) =>
        v match {
          case i: RawTypeDef.Interface =>
            typedefSupport.makeInterface(i, Seq.empty).asList

          case d: RawTypeDef.DTO =>
            typedefSupport.makeDto(d, Seq.empty).asList

          case a: RawTypeDef.Alias =>
            typedefSupport.makeAlias(a, Seq.empty).asList

          case e: RawTypeDef.Enumeration =>
            typedefSupport.makeEnum(e, Seq.empty).asList

          case i: RawTypeDef.Identifier =>
            typedefSupport.makeIdentifier(i, Seq.empty).asList

          case a: RawTypeDef.Adt =>
            adtSupport.makeAdt(a)
        }

      case t: RawTopLevelDefn.TLDTemplate =>
        templateSupport.makeTemplate(t.defn)

      case t: RawTopLevelDefn.TLDInstance =>
        templateSupport.makeInstance(t.defn)

      case c: RawTopLevelDefn.TLDNewtype =>
        cloneSupport.cloneType(c.defn)

      case RawTopLevelDefn.TLDForeignType(v) =>
        typedefSupport.makeForeign(v).asList
    }
  }


}
