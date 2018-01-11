package org.bitbucket.pshirshov.izumi.di


import WithReflection._

object ConcreteSymbol {
  def unapply(arg: Symb): Option[Symb] = Some(arg).filter(isConcrete)
}

object AbstractSymbol {
  def unapply(arg: Symb): Option[Symb] = Some(arg).filter(isWireableAbstract)
}


object FactorySymbol {
  def unapply(arg: Symb): Option[(Symb, Seq[Symb])] = {
    Some(arg).filter(isFactory).map(f => (f, f.info.decls.filter(m => isFactoryMethod(f, m)).toSeq))
  }
}
