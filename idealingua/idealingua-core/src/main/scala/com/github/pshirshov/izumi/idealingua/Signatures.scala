package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.Field
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition.{Alias, Identifier, Interface}


trait Signatures {
  def signature(fields: Seq[Field]): Long

  def compositeSignature(dto: Seq[Interface]): Long

  def signature(dto: Interface): Long
  def signature(dto: Alias): Long
  def signature(dto: Identifier): Long
}

// TODO: not like so
object Signatures extends Signatures {
  override def signature(fields: Seq[Field]): Long = {
    fields.hashCode()
  }


  override def compositeSignature(dto: Seq[Interface]): Long = {
    dto.hashCode() //dto.map(signature).product
  }


  override def signature(dto: Interface): Long = {
    dto.hashCode() //signature(dto.fields)
  }

  override def signature(dto: Alias): Long = {
    dto.hashCode()
  }

  override def signature(dto: Identifier): Long = {
    dto.hashCode() //signature(dto.structure.fields)
  }
}