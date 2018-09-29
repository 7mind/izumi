package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.definition.dsl.ModuleDefDSL.{BindDSLBase, SetDSLBase}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor

object StaticDSL {

  implicit final class MagicBindDSL[T, AfterBind](private val dsl: BindDSLBase[T, AfterBind]) extends AnyVal {
    def stat[I <: T: Tag: AnyConstructor]: AfterBind =
      dsl.from[I](AnyConstructor[I].provider)
  }

  implicit final class MagicSetDSL[T, AfterAdd](private val dsl: SetDSLBase[T, AfterAdd]) extends AnyVal {
    def addStatic[I <: T: Tag: AnyConstructor]: AfterAdd =
      dsl.add[I](AnyConstructor[I].provider)
  }

  implicit final class MagicBinding(private val binding: ImplBinding) extends AnyVal {
    def withStaticImpl[T: Tag: AnyConstructor]: ImplBinding =
      binding.withImpl[T](AnyConstructor[T].provider)
  }

}
