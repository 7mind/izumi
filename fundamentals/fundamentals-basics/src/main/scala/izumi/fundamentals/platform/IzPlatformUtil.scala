package izumi.fundamentals.platform

/** A marker trait for any helper collections provided by Izumi (e.g. IzEither)
  */
sealed trait IzPlatformUtil

/** A marker trait for packages with pure helpers (syntax extensions, etc)
  */
trait IzPlatformPureUtil extends IzPlatformUtil

/** A marker trait for packages with impure helpers (RNG access, clock access, filesystem access, etc)
  */
trait IzPlatformEffectfulUtil extends IzPlatformUtil
