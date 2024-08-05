package izumi.distage.framework.platform

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.crypto.{IzHash, IzSha256Hash}
import izumi.fundamentals.platform.network.{IzDNS, IzSockets}
import izumi.fundamentals.platform.os.IzOs
import izumi.fundamentals.platform.{AbstractIzPlatform, IzPlatform}

class DistagePlatformModule extends ModuleDef {
  make[AbstractIzPlatform].fromValue(IzPlatform)
  make[IzDNS].fromValue(IzDNS)
  make[IzSockets].fromValue(IzSockets)
  make[IzOs].fromValue(IzOs)
  make[IzHash].from(() => IzSha256Hash.getImpl)

//  make[IzTime.type]
//    .aliased[IzTime]
//    .aliased[IzTimeOrdering]
//  make[IzTimeSafe.type]
//    .aliased[IzTimeSafe]
//    .aliased[IzTimeOrderingSafe]
}
