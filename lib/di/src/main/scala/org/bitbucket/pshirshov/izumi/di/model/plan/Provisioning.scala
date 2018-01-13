package org.bitbucket.pshirshov.izumi.di.model.plan

sealed trait Provisioning[G, B] {
  @inline def map[G1](f: G => G1): Provisioning[G1, B]
}

object Provisioning {

  case class Possible[G, B](possible: G) extends Provisioning[G, B] {
    @inline override def map[G1](f: G => G1): Provisioning[G1, B] = Possible(f(possible))
  }

  case class Impossible[G, B](impossible: B) extends Provisioning[G, B] {
    @inline override def map[G1](f: G => G1): Provisioning[G1, B] = Impossible(impossible)
  }

}