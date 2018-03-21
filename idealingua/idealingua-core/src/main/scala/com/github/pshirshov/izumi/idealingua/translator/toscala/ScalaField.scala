package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.ExtendedField

import scala.meta.{Term, Type}

case class ScalaField(name: Term.Name, fieldType: Type, field: ExtendedField, conflicts: Set[ExtendedField])
