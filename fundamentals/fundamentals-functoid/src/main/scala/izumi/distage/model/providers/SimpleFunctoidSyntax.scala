package izumi.distage.model.providers

import scala.language.implicitConversions

trait SimpleFunctoidSyntax[Ftoid[+X] <: AbstractFunctoid[X, Ftoid]] {
  implicit final def syntaxMapSame[A](functoid: Ftoid[A]): SimpleFunctoidSyntax.SyntaxMapSame[A, Ftoid] = new SimpleFunctoidSyntax.SyntaxMapSame[A, Ftoid](functoid)
}

object SimpleFunctoidSyntax {
  final class SyntaxMapSame[A, Ftoid[+X] <: AbstractFunctoid[X, Ftoid]](private val functoid: Ftoid[A]) extends AnyVal {
    def mapSame(f: A => A): Ftoid[A] = functoid.map(f)(functoid.returnTypeTag)
  }
}
