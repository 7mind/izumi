package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.{BindDSLBase, BindOnlyNameableDSL}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.provisioning.{AbstractConstructor, AnyConstructor, ConcreteConstructor}

object MagicDSL {

  implicit final class MagicBindDSL[T, AfterBind](private val dsl: BindDSLBase[T, AfterBind]) extends AnyVal {
    def magically[I <: T: Tag: AnyConstructor]: AfterBind =
      implicitly[AnyConstructor[I]] match {
        case ctor: AbstractConstructor[I] =>
          dsl.provided[I](ctor.function)
        case _: ConcreteConstructor[I] =>
          dsl.as[I]
      }
  }

  implicit final class MagicBindingDSL[B <: AbstractModuleDef](private val dsl: B)(implicit ev: B => BindingDSL) {
    @inline
    def magic[T: Tag: AnyConstructor]: BindOnlyNameableDSL =
      implicitly[AnyConstructor[T]] match {
        case ctor: AbstractConstructor[T] =>
          dsl.bind[T].provided(ctor.function)
        case _: ConcreteConstructor[T] =>
          dsl.bind[T]
      }
  }

}
