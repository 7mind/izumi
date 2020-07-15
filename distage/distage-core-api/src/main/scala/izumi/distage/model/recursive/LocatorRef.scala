package izumi.distage.model.recursive

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.model.Locator
import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.reflection.DIKey

/**
  * This class allows you to summon a locator reference from any class in the object graph.
  *
  * Reference will be initialized after the provisioning process finishes,
  * so you cannot dereference it in constructor.
  *
  * Summoning the entire Locator is usually an anti-pattern, but may sometimes be necessary.
  */
class LocatorRef(private[distage] val ref: AtomicReference[Either[Locator, Locator]]) {
  def get: Locator = ref.get().getOrElse(throw new MissingInstanceException("Stable locator is not ready yet", DIKey.get[Locator]))
  def unsafeUnstableMutableLocator(): Locator = ref.get().merge
}
