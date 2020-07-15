package izumi.distage.model

import izumi.distage.AbstractLocator
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.{DIKey, TypedRef}
import izumi.reflect.{Tag, TagK}

import scala.collection.immutable
import scala.collection.immutable.Queue

/**
  * The object graph created by executing a `plan`.
  * Can be queried for contained objects.
  *
  * @see [[Injector]]
  * @see [[Planner]]
  * @see [[Producer]]
  */
trait Locator {

  /** Instances in order of creation
    *
    * @return *Only* instances contained in this Locator, *NOT* instances in [[parent]] Locators. All the keys must be unique
    */
  def instances: collection.Seq[IdentifiedRef]

  def plan: OrderedPlan
  def parent: Option[Locator]

  def lookupInstanceOrThrow[T: Tag](key: DIKey): T
  def lookupInstance[T: Tag](key: DIKey): Option[T]

  def find[T: Tag]: Option[T]
  def find[T: Tag](id: Identifier): Option[T]

  def get[T: Tag]: T
  def get[T: Tag](id: Identifier): T

  def finalizers[F[_]: TagK]: collection.Seq[Finalizer[F]]
  private[distage] def lookupLocal[T: Tag](key: DIKey): Option[TypedRef[T]]

  def lookupRefOrThrow[T: Tag](key: DIKey): TypedRef[T]
  def lookupRef[T: Tag](key: DIKey): Option[TypedRef[T]]

  def index: Map[DIKey, Any] = instances.map(i => i.key -> i.value).toMap

  /** ALL instances contained in this locator and in ALL the parent locators, including injector bootstrap environment.
    * Returned keys may overlap, if parent locators contain objects for the same key.
    *
    * @see [[izumi.distage.bootstrap.BootstrapLocator]]
    */
  final def allInstances: immutable.Seq[IdentifiedRef] = {
    parent.map(_.allInstances).getOrElse(immutable.Seq.empty) ++ instances
  }

  /**
    * Run `function` filling all the arguments from locator contents.
    *
    * Works similarly to provider bindings.
    *
    * @see [[ProviderMagnet]]
    */
  final def run[T](function: ProviderMagnet[T]): T = {
    val fn = function.get
    fn.unsafeApply(fn.diKeys.map {
      key =>
        lookupRefOrThrow[Any](key)
    }).asInstanceOf[T]
  }

  final def runOption[T](function: ProviderMagnet[T]): Option[T] = {
    val fn = function.get
    val args: Option[Queue[TypedRef[Any]]] = fn.diKeys.foldLeft(Option(Queue.empty[TypedRef[Any]])) {
      (maybeQueue, key) =>
        maybeQueue.flatMap {
          queue =>
            lookupRef[Any](key).map(queue :+ _)
        }
    }
    args.map(fn.unsafeApply(_).asInstanceOf[T])
  }
}

object Locator {
  val empty: AbstractLocator = new AbstractLocator {
    override protected def lookupLocalUnsafe(key: DIKey): Option[Any] = None
    override def instances: Seq[IdentifiedRef] = Nil
    override def plan: OrderedPlan = OrderedPlan.empty
    override def parent: Option[Locator] = None
    override def finalizers[F[_]: TagK]: Seq[Finalizer[F]] = Nil
  }
}
