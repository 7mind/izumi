package izumi.distage.model

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.immutable.Queue

/** Holds the object graph created by executing a `plan`
  *
  * @see [[Injector]]
  * @see [[Planner]]
  * @see [[Producer]]
  **/
trait Locator {
  /** Instances in order of creation
    *
    * @return *Only* instances contained in this Locator, *NOT* instances in [[parent]] Locators. All the keys must be unique
    */
  def instances: collection.Seq[IdentifiedRef]

  /** ALL instances contained in this locator and in ALL the parent locators, including injector bootstrap environment.
    * Keys may be not unique.
    *
    * @see [[izumi.distage.bootstrap.BootstrapLocator]]
    */
  final def allInstances: collection.Seq[IdentifiedRef] =
    parent.map(_.allInstances).getOrElse(Seq.empty) ++ instances

  def index: Map[RuntimeDIUniverse.DIKey, Any] = instances.map(i => i.key -> i.value).toMap

  def plan: OrderedPlan

  def parent: Option[Locator]

  def lookupInstanceOrThrow[T: Tag](key: DIKey): T

  def lookupInstance[T: Tag](key: DIKey): Option[T]

  def find[T: Tag]: Option[T]

  def find[T: Tag](id: String): Option[T]

  def get[T: Tag]: T

  def get[T: Tag](id: String): T

  protected[distage] def finalizers[F[_]: TagK]: collection.Seq[Finalizer[F]]

  protected[distage] def lookup[T: Tag](key: DIKey): Option[TypedRef[T]]

  /**
    * Run function `f` filling all the arguments from locator contents.
    *
    * Works similarly to provider bindings.
    *
    * @see [[ProviderMagnet]]
    */
  final def run[T](function: ProviderMagnet[T]): T = {
    val fn = function.get
    fn.fun(fn.diKeys.map {
      key =>
        lookupInstanceOrThrow[Any](key)
    }).asInstanceOf[T]
  }

  final def runOption[T](function: ProviderMagnet[T]): Option[T] = {
    val fn = function.get
    val args: Option[Queue[Any]] = fn.diKeys.foldLeft(Option(Queue.empty[Any])) {
      (maybeQueue, key) =>
        maybeQueue.flatMap {
          q =>
            lookupInstance[Any](key)
              .map(q :+ _)
        }
    }
    args.map(fn.fun(_).asInstanceOf[T])
  }
}

object Locator {

  /**
    * This class allows you to summon a locator reference from any class in the object graph.
    *
    * Reference will be initialized after the provisioning process finishes,
    * so you cannot dereference it in constructor.
    *
    * Summoning the entire Locator is usually an anti-pattern, but may sometimes be necessary.
    */
  class LocatorRef() {
    protected[distage] val ref: AtomicReference[Locator] = new AtomicReference[Locator]()
    def get: Locator = ref.get()
  }

}
