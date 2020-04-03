package izumi.distage.model.recursive

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.model.Locator

/**
  * This class allows you to summon a locator reference from any class in the object graph.
  *
  * Reference will be initialized after the provisioning process finishes,
  * so you cannot dereference it in constructor.
  *
  * Summoning the entire Locator is usually an anti-pattern, but may sometimes be necessary.
  */
class LocatorRef(instance: Option[Locator]) {
  private[distage] val ref: AtomicReference[Locator] = new AtomicReference[Locator](instance.orNull)

  def get: Locator = ref.get()
}




