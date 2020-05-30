package izumi.distage.model.definition

import izumi.distage.model.reflection.IdContract
import scala.language.implicitConversions

/**
  * Type of a name for a named instance, which can be any type that implements [[IdContract]] typeclass, e.g. [[String]]
  *
  * Example:
  * {{{
  *   implicit val idInt: IdContract[Int] = new IdContract.IdContractImpl[Int]
  *
  *   val module = new ModuleDef {
  *     make[Int].named(3).from(3)
  *     make[Int].named(5).from(5)
  *   }
  * }}}
  */
abstract class Identifier {
  type Id
  def id: Id
  def idContract: IdContract[Id]
}

object Identifier {
  implicit def fromIdContract[I](id0: I)(implicit ev: IdContract[I]): Identifier { type Id = I } = new Identifier {
    override type Id = I
    override val id: I = id0
    override val idContract: IdContract[I] = ev
  }
}
