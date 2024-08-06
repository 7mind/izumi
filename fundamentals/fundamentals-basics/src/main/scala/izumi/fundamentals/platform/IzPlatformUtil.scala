package izumi.fundamentals.platform

/** A marker trait for any helper collections provided by Izumi (e.g. IzEither)
  */
sealed trait IzPlatformUtil

/** A marker trait for packages with pure helpers
  */
sealed trait IzPlatformPureUtil extends IzPlatformUtil

/** A marker trait for packages with purely syntactic extensions
  *
  * All these helpers should be accessible through
  * {{{import izumi.fundamentals.preamble.*}}}
  */
trait IzPlatformSyntax extends IzPlatformPureUtil

/** A marker trait for helper function collection packages
  */
trait IzPlatformFunctionCollection extends IzPlatformPureUtil

/** A marker trait for packages with impure helpers (RNG access, clock access, filesystem access, etc)
  *
  * All these helpers should be added into DI context in [[izumi.distage.framework.platform.DistagePlatformModule]]
  * and should be by injection, not by addressing the singletons directly.
  */
trait IzPlatformEffectfulUtil extends IzPlatformUtil
