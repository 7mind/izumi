package izumi.distage.model.provisioning

import izumi.distage.model.plan.Plan
import izumi.distage.model.reflection.DIKey

import scala.collection.Map

trait ProvisioningKeyProvider {
  /** Return the object referred by `key` if available
    *
    * @param makeByName Wrap the result into a `Function0`,
    *                   if already a by-name proxy, return unchanged.
    *                   If `false` and the value is a by-name proxy - it will be executed
    */
  def fetchKey(key: DIKey, makeByName: Boolean): Option[Any]

  /** Directly access a value from the current context, without unpacking by-names * */
  def fetchUnsafe(key: DIKey): Option[Any]

  /** Lookup value through the chain of Locators, possibly retrieving it from a parent Locator */
  def importKey(key: DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[DIKey]): ProvisioningKeyProvider

  def instances: Map[DIKey, Any]

  def plan: Plan
}
