package candidate

import cats.effect.IO
import distage.DIKey
import izumi.distage.plugins.PluginDef
import izumi.distage.testkit.model.TestConfig
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.uuid.IzUUID
import scala.concurrent.duration.*

final class HeldResource(val id: String)

final class HeldPlugin extends PluginDef {
  make[HeldResource].fromResource { () =>
    Lifecycle.make[IO, HeldResource](IO {
      val value = new HeldResource(IzUUID.generateTimeUUID().toString)
      println("SDK_DI_HELD_ACQUIRE owner=" + value.id)
      value
    }) { value =>
      IO { Platform.finalizerMarker("enter"); println("SDK_DI_FINALIZER_ENTER owner=" + value.id) } *>
        IO.sleep(5.seconds) *>
        IO { println("SDK_DI_FINALIZER_EXIT owner=" + value.id); Platform.finalizerMarker("exit") }
    }
  }
}

trait HeldConfigured extends Configured {
  abstract override protected def config: TestConfig = super.config.copy(
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[SharedResource], DIKey[HeldResource])),
  )
}
