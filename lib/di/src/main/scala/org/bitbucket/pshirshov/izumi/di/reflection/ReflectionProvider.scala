package org.bitbucket.pshirshov.izumi.di.reflection
import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.model.plan.Association

trait ReflectionProvider {

  def symbolDeps(symbl: TypeFull): Seq[Association]

  def isConcrete(symb: TypeFull): Boolean

  def isWireableAbstract(symb: TypeFull): Boolean

  def isFactory(symb: TypeFull): Boolean
}
