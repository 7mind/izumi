package izumi.distage.model.providers

import scala.language.implicitConversions

trait FunctoidSyntax[Ftoid[+K] <: AbstractFunctoid[K, Ftoid]] {
  implicit final def syntaxMapSame[A](functoid: Ftoid[A]): FunctoidSyntax.SyntaxMapSame[A, Ftoid] = new FunctoidSyntax.SyntaxMapSame[A, Ftoid](functoid)
}

object FunctoidSyntax {
  final class SyntaxMapSame[A, Ftoid[+K] <: AbstractFunctoid[K, Ftoid]](private val functoid: Ftoid[A]) extends AnyVal {
    def mapSame(f: A => A): Ftoid[A] = functoid.map(f)(functoid.returnTypeTag)
  }
}
