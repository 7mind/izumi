package izumi.distage.model.effect

/** Scala.js does not support running effects synchronously */
private[distage] trait DIEffectRunner[F[_]]
private[distage] object DIEffectRunner
