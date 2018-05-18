package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef.{BindDSLBase, SetDSLBase}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.provisioning.{AbstractConstructor, AnyConstructor, ConcreteConstructor}

object MagicDSL {

  implicit final class MagicBindDSL[T, AfterBind](private val dsl: BindDSLBase[T, AfterBind]) extends AnyVal {
    def magically(implicit ev: AnyConstructor[T], ev1: Tag[T]): AfterBind =
      fromMagic[T]

    def fromMagic[I <: T: Tag: AnyConstructor]: AfterBind =
      AnyConstructor[I] match {
        case ctor: AbstractConstructor[I] =>
          dsl.from[I](ctor.function)
        case _: ConcreteConstructor[I] =>
          dsl.from[I]
      }
  }

  // FIXME: add tests
  // FIXME: modify cursor instead of adding new element
  implicit final class MagicSetDSL[T, AfterAdd](private val dsl: SetDSLBase[T, AfterAdd]) extends AnyVal {
    def addMagic[I <: T: Tag: AnyConstructor]: AfterAdd =
      AnyConstructor[I] match {
        case ctor: AbstractConstructor[I] =>
          dsl.add(ctor.function)
        case _: ConcreteConstructor[I] =>
          dsl.add[I]
      }
  }

  implicit final class MagicBinding(private val binding: ImplBinding) extends AnyVal {
    def fromMagic[T: Tag: AnyConstructor]: ImplBinding =
      AnyConstructor[T] match {
        case ctor: AbstractConstructor[T] =>
          binding.withImpl(ctor.function)
        case _: ConcreteConstructor[T] =>
          binding.withImpl[T]
      }
  }

}
