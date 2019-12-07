package izumi.distage.model.reflection.universe

import izumi.fundamentals.platform.functional.Identity

package object RuntimeDIUniverse {
  lazy val identityEffectType: SafeType = SafeType.getK[Identity]
}
