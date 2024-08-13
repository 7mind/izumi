package izumi.distage.bootstrap

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.nonempty.NESet

sealed trait BootstrapRootsMode

object BootstrapRootsMode {
  case object UseGC extends BootstrapRootsMode
  case object Everything extends BootstrapRootsMode

  /** Never use this unless you know exactly what you are doing!
    */
  case class UNSAFE_Custom(keys: NESet[DIKey]) extends BootstrapRootsMode
}
