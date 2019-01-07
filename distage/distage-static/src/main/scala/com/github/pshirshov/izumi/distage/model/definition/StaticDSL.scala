package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.definition.StaticDSL.{StaticBindDSL, StaticBinding, StaticSetDSL}
import com.github.pshirshov.izumi.distage.model.definition.dsl.ModuleDefDSL.{BindDSLBase, SetDSLBase}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor

import scala.language.implicitConversions

trait StaticDSL {

  @inline implicit final def ToMagicBindDSL[T, AfterBind](dsl: BindDSLBase[T, AfterBind]): StaticBindDSL[T, AfterBind] =
    new StaticBindDSL[T, AfterBind](dsl)

  @inline implicit final def ToMagicSetDSL[T, AfterAdd, AfterMultiAdd](dsl: SetDSLBase[T, AfterAdd, AfterMultiAdd]): StaticSetDSL[T, AfterAdd, AfterMultiAdd] =
    new StaticSetDSL[T, AfterAdd, AfterMultiAdd](dsl)

  @inline implicit final def ToMagicBinding(binding: ImplBinding): StaticBinding =
    new StaticBinding(binding)
}

object StaticDSL extends StaticDSL {

  final class StaticBindDSL[T, AfterBind](private val dsl: BindDSLBase[T, AfterBind]) extends AnyVal {
    def stat[I <: T: Tag: AnyConstructor]: AfterBind =
      dsl.from[I](AnyConstructor[I].provider)
  }

  final class StaticSetDSL[T, AfterAdd, AfterMultiAdd](private val dsl: SetDSLBase[T, AfterAdd, AfterMultiAdd]) extends AnyVal {
    def addStatic[I <: T: Tag: AnyConstructor]: AfterAdd =
      dsl.add[I](AnyConstructor[I].provider)
  }

  final class StaticBinding(private val binding: ImplBinding) extends AnyVal {
    def withStaticImpl[T: Tag: AnyConstructor]: ImplBinding =
      binding.withImpl[T](AnyConstructor[T].provider)
  }

}
