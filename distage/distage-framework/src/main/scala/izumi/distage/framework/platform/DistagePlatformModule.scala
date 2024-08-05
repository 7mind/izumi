package izumi.distage.framework.platform

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.crypto.{IzHash, IzSha256Hash}
import izumi.fundamentals.platform.exceptions.IzStack
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.jvm.{IzClasspath, IzJvm}
import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.network.{IzDNS, IzSockets}
import izumi.fundamentals.platform.os.IzOs
import izumi.fundamentals.platform.{AbstractIzPlatform, IzPlatform}

class DistagePlatformModule extends ModuleDef {
  // effectful
  make[AbstractIzPlatform].fromValue(IzPlatform)
  make[IzClasspath].fromValue(IzClasspath)
  make[IzDNS].fromValue(IzDNS)
  make[IzFiles].fromValue(IzFiles)
  make[IzJvm].fromValue(IzJvm)
  make[IzOs].fromValue(IzOs)
  make[IzScala].fromValue(IzScala)
  make[IzSockets].fromValue(IzSockets)
  make[IzStack].fromValue(IzStack)

  // pure
  make[IzHash].from(() => IzSha256Hash.getImpl)

  // the rest of the pure helpers seem to be unnecessary
}
