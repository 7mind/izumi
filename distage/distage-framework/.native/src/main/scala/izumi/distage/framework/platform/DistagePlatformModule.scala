package izumi.distage.framework.platform

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.crypto.{IzHash, IzHashFunction, IzSha256HashFunction}
import izumi.fundamentals.platform.exceptions.IzStack
import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.jvm.IzJvm
import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.network.IzDNS
import izumi.fundamentals.platform.os.IzOs
import izumi.fundamentals.platform.{AbstractIzPlatform, IzPlatform}

class DistagePlatformModule extends ModuleDef {
  make[AbstractIzPlatform].fromValue(IzPlatform)
  make[IzDNS].fromValue(IzDNS)
  make[IzFiles].fromValue(IzFiles)
  make[IzJvm].fromValue(IzJvm)
  make[IzOs].fromValue(IzOs)
  make[IzScala].fromValue(IzScala)
  make[IzStack].fromValue(IzStack)
  make[IzHash].fromValue(IzHash)
  make[IzHashFunction].from(() => IzSha256HashFunction.getImpl)
}
