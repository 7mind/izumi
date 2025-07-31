package izumi.distage.framework.platform

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.crypto.IzHash
import izumi.fundamentals.platform.exceptions.IzStack
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.jvm.{IzClasspath, IzJvm}
import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.network.{IzDNS, IzSockets}
import izumi.fundamentals.platform.os.IzOs
import izumi.fundamentals.platform.uuid.IzUUID
import izumi.fundamentals.platform.{AbstractIzPlatform, IzPlatform}

class DistagePlatformModule extends ModuleDef {
  // effectful
  make[AbstractIzPlatform].from(IzPlatform)
  make[IzClasspath].from(IzClasspath)
  make[IzDNS].from(IzDNS)
  make[IzFiles].from(IzFiles)
  make[IzJvm].from(IzJvm)
  make[IzOs].from(IzOs)
  make[IzScala].from(IzScala)
  make[IzSockets].from(IzSockets)
  make[IzStack].from(IzStack)
  make[IzUUID].from(IzUUID)

  // pure
  make[IzHash].from(IzHash)

  // the rest of the pure helpers seem to be unnecessary
}
