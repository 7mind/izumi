package izumi.fundamentals.platform

/** A marker trait for any helper collections provided by Izumi (e.g. IzEither)
  */
sealed trait IzPlatformUtil

/** A marker trait for packages with pure helpers
  */
sealed trait IzPlatformPureUtil extends IzPlatformUtil

/** A marker trait for packages with purely syntactic extensions
  */
trait IzPlatformSyntax extends IzPlatformPureUtil

/** A marker trait for helper function collection packages
  */
trait IzPlatformFunctionCollection extends IzPlatformPureUtil

/** A marker trait for packages with impure helpers (RNG access, clock access, filesystem access, etc)
  */
trait IzPlatformEffectfulUtil extends IzPlatformUtil
