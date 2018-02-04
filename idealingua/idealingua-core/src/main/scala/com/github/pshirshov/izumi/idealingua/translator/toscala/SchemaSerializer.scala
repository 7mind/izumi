package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition, Service}

import scala.meta._

object SchemaSerializer {
  def toAst(defn: DomainDefinition): Term = {
    q"""???"""
  }

  def toAst(defn: Service): Term = {
    q"???" // TODO: serialize&parse?..
//    q"""Service(
//        ServiceId(Seq(..${defn.id.pkg.toList.map(p => Lit.String(p))}), ${Lit.String(defn.id.name)})
//        , List.empty
//             )
//      """
  }

  def toAst(defn: FinalDefinition): Term = {
    q"""???"""
  }
}
