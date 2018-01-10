package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.Tag

import scala.reflect.runtime.universe._

sealed trait Def {
}

object Def {
  case class SingletonBinding[BType: Tag, IType <: BType: Tag]() extends Def {
    def bindingType: Tag[BType] = typeTag[BType]
    def implementingType: Tag[IType] = typeTag[IType]
  }

}
