package izumi.functional.bio.syntax

/**
  * Empty stub traits mirroring the Scala-3-only extension-method traits of the
  * same name. They exist solely so that typeclass traits such as
  * [[izumi.functional.bio.Monad2]] can use `... with Monad2ExtensionMethods`
  * in a shared (`src/main/scala`) source without breaking Scala 2 compilation.
  *
  * On Scala 2, all syntax comes from [[Syntax2]] via implicit classes —
  * these stubs contribute nothing. On Scala 3 the traits are deliberately
  * standalone (no mutual inheritance) so that sibling typeclass givens in
  * scope can't present the same inherited extension method via more than one
  * member path — see [[Syntax2]]'s Scala-3 docstring.
  */
trait Functor2ExtensionMethods
trait Bifunctor2ExtensionMethods
trait Applicative2ExtensionMethods
trait Guarantee2ExtensionMethods
trait ApplicativeError2ExtensionMethods
trait Monad2ExtensionMethods
trait Error2ExtensionMethods
trait Bracket2ExtensionMethods
trait Panic2ExtensionMethods
trait IO2ExtensionMethods
trait Parallel2ExtensionMethods
trait Concurrent2ExtensionMethods
trait WeakTemporal2ExtensionMethods
trait Temporal2ExtensionMethods
trait Fork2ExtensionMethods

/** Scala 2 stub for the Scala-3-only "InnerF" shadow extension-method trait.
  * Empty because Scala 2 routes all syntax through [[Syntax2]]'s implicit
  * classes.
  */
trait InnerFExtensionMethodsFromWeakTemporal2
