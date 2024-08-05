package izumi.distage.framework.platform

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.crypto.{IzHash, IzSha256Hash}
import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.{AbstractIzPlatform, IzPlatform}

class DistagePlatformModule extends ModuleDef {
  // effectful
  make[AbstractIzPlatform].fromValue(IzPlatform)

  make[IzScala].fromValue(IzScala)

  // pure
  make[IzHash].from(() => IzSha256Hash.getImpl)

  // the rest of the pure helpers seem to be unnecessary
}
