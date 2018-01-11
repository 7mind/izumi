package org.bitbucket.pshirshov.izumi.di.reflection
import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.plan.Association

trait ReflectionProvider {

  def symbolDeps(symbl: Symb): Seq[Association]

  def isConcrete(symb: Symb): Boolean

  def isWireableAbstract(symb: Symb): Boolean

  def isFactory(symb: Symb): Boolean
}
