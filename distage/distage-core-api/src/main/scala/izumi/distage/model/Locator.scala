package izumi.distage.model

import izumi.distage.AbstractLocator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.Plan
import izumi.distage.model.plan.repr.LocatorFormatter
import izumi.distage.model.providers.Functoid
import izumi.distage.model.provisioning.OpStatus
import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.{DIKey, GenericTypedRef}
import izumi.functional.Renderable
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiPrimitives
import izumi.reflect.{Tag, TagK}

import scala.collection.immutable
import scala.collection.immutable.Queue

/**
  * The object graph created by executing a `plan`.
  * Can be queried for contained objects.
  *
  * @see [[izumi.distage.model.Injector]]
  * @see [[izumi.distage.model.Planner]]
  * @see [[izumi.distage.model.Producer]]
  */
trait Locator {

  def get[T: Tag]: T
  def get[T: Tag](id: Identifier): T

  def find[T: Tag]: Option[T]
  def find[T: Tag](id: Identifier): Option[T]

  def lookupInstanceOrThrow[T: Tag](key: DIKey): T
  def lookupInstance[T: Tag](key: DIKey): Option[T]

  def finalizers[F[_]: TagK]: collection.Seq[Finalizer[F]]
  private[distage] def lookupLocal[T: Tag](key: DIKey): Option[GenericTypedRef[T]]

  def lookupRefOrThrow[T: Tag](key: DIKey): GenericTypedRef[T]
  def lookupRef[T: Tag](key: DIKey): Option[GenericTypedRef[T]]

  def isPrivate(key: DIKey): Boolean

  /** The plan that produced this object graph */
  def plan: Plan
  def parent: Option[Locator]
  def meta: LocatorMeta

  /**
    * Objects in this locator in order of creation
    *
    * @return *Only* instances directly contained in `this` Locator, *NOT* instances in its [[parent]] Locators.
    *         Returned keys will be unique.
    */
  def instances: immutable.Seq[IdentifiedRef]

  /**
    * @return *Only* instances directly contained in `this` Locator, *NOT* instances in its [[parent]] Locators.
    *         Returned keys will be unique.
    */
  def index: Map[DIKey, Any]

  /**
    * @return ALL instances contained in `this` locator and in all the [[parent]] locators, including injector bootstrap environment.
    *         Returned keys may overlap if parent locators contain objects for the same key. Instances from parent locators will be
    *         earlier in the list than instances from this locator.
    *
    * @see [[izumi.distage.bootstrap.BootstrapLocator]]
    */
  final def allInstances: immutable.Seq[IdentifiedRef] = {
    parent.map(_.allInstances).getOrElse(immutable.Seq.empty) ++ instances
  }

  /**
    * Run `function` filling all the arguments from the object graph.
    *
    * Works similarly to function bindings in [[izumi.distage.model.definition.ModuleDef]].
    *
    * {{{
    *   objects.run {
    *     (hellower: Hellower, bye: Byer) =>
    *       hellower.hello()
    *       byer.bye()
    *   }
    * }}}
    *
    * @see [[izumi.distage.model.providers.Functoid]]
    */
  final def run[T](function: Functoid[T]): T = {
    val fn = function.get
    fn.unsafeApply(fn.diKeys.map {
      key =>
        lookupRefOrThrow[Any](key)
    }).asInstanceOf[T]
  }

  /** Same as [[run]] but returns `None` if any of the arguments could not be fulfilled */
  final def runOption[T](function: Functoid[T]): Option[T] = {
    val fn = function.get
    val args: Option[Queue[GenericTypedRef[Any]]] = fn.diKeys.foldLeft(Option(Queue.empty[GenericTypedRef[Any]])) {
      (maybeQueue, key) =>
        maybeQueue.flatMap {
          queue =>
            lookupRef[Any](key).map(queue :+ _)
        }
    }
    args.map(fn.unsafeApply(_).asInstanceOf[T])
  }

  final def depth: Int = {
    var d = -1
    var loc: Option[Locator] = Some(this)
    while (loc.nonEmpty) {
      d = d + 1
      loc = loc.get.parent
    }
    d
  }

  def render()(implicit ev: Renderable[Locator]): String = ev.render(this)

  override def toString: String = {
    this.render()
  }
}

object Locator {
  implicit final class SyntaxLocatorRun[F[_]](private val resource: Lifecycle[F, Locator]) extends AnyVal {
    def run[B](function: Functoid[F[B]])(implicit F: QuasiPrimitives[F]): F[B] =
      resource.use(_.run(function))
  }

  @inline implicit final def defaultFormatter: Renderable[Locator] = LocatorFormatter

  val empty: AbstractLocator = new AbstractLocator {
    override protected def lookupLocalUnsafe(key: DIKey): Option[Any] = None
    override def instances: immutable.Seq[IdentifiedRef] = Nil
    override def plan: Plan = Plan.empty
    override def parent: Option[Locator] = None
    override def finalizers[F[_]: TagK]: Seq[Finalizer[F]] = Nil
    override def index: Map[DIKey, Any] = Map.empty
    override def meta: LocatorMeta = LocatorMeta.empty
    override def isPrivate(key: DIKey): Boolean = false
  }

  /** @param timings How long it took to instantiate each component */
  final case class LocatorMeta(
    status: Map[DIKey, OpStatus]
  ) extends AnyVal
  object LocatorMeta {
    def empty: LocatorMeta = LocatorMeta(Map.empty)
  }
}
