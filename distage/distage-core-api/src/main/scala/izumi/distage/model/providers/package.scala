package izumi.distage.model

package object providers {

  object distageFunctoid extends FunctoidTemplate with FunctoidLifecycleAdaptersTemplate with FunctoidConstructorsTemplate

  type Functoid[+A] = distageFunctoid.Functoid[A]
  val Functoid: distageFunctoid.Functoid.type = distageFunctoid.Functoid

}
