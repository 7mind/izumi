package izumi.distage.model.definition

import izumi.distage.AbstractLocator
import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.ImplDef.InstanceImpl
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.SetIdAll
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction.{AliasTo, SetId, SetImpl}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL._
import izumi.distage.model.exceptions.LocatorDefUninstantiatedBindingException
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import izumi.distage.model.plan._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.plan.topology.DependencyGraph.DependencyKind
import izumi.distage.model.plan.topology.PlanTopology.PlanTopologyImmutable
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.{Locator, definition}
import izumi.fundamentals.platform.language.{CodePositionMaterializer, SourceFilePosition}
import izumi.fundamentals.reflection.Tags.{Tag, TagK}

import scala.collection.{immutable, mutable}

// TODO: shameless copypaste of [[ModuleDef]] for now; but we CAN unify all of LocatorDef, ModuleDef, TypeLevelDSL and Bindings DSLs into one...
trait LocatorDef
  extends AbstractLocator
    with AbstractBindingDefDSL[LocatorDef.BindDSL, LocatorDef.SetDSL] {

  override def finalizers[F[_]: TagK]: immutable.Seq[PlanInterpreter.Finalizer[F]] = Nil

  override private[definition] def _bindDSL[T](ref: SingletonRef): LocatorDef.BindDSL[T] =
    new definition.LocatorDef.BindDSL[T](ref, ref.key)

  override private[definition] def _setDSL[T](ref: SetRef): LocatorDef.SetDSL[T] =
    new definition.LocatorDef.SetDSL[T](ref)

  protected def initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  override protected def lookupLocalUnsafe(key: DIKey): Option[Any] = {
    frozenMap.get(key)
  }

  override def instances: immutable.Seq[IdentifiedRef] = frozenInstances.toList

  override lazy val plan: OrderedPlan = {
    val topology = PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Required), DependencyGraph(Map.empty, DependencyKind.Depends))

    val ops = frozenInstances.map {
      case IdentifiedRef(key, value) =>
        val origin = OperationOrigin.SyntheticBinding(Binding.SingletonBinding[DIKey](key, ImplDef.InstanceImpl(key.tpe, value), Set.empty, SourceFilePosition.unknown))
        UseInstance(key, Instance(key.tpe, value), origin)
    }.toVector

    OrderedPlan(ops, ops.map(_.target).toSet, topology)
  }

  override def parent: Option[Locator] = None

  private[this] final lazy val (frozenMap, frozenInstances): (Map[DIKey, Any], Seq[IdentifiedRef]) = {
    val map = new mutable.LinkedHashMap[DIKey, Any]

    frozenState.foreach {
      case SingletonBinding(key, InstanceImpl(_, instance), _, _) =>
        map += (key -> instance)
      case SetElementBinding(key, InstanceImpl(_, instance), _, _) =>
        val setKey = key.set
        map += (setKey -> (map.getOrElse(setKey, Set.empty[Any]).asInstanceOf[Set[Any]] + instance))
      case e: EmptySetBinding[_] =>
        map.getOrElseUpdate(e.key, Set.empty[Any])
      case b =>
        throw new LocatorDefUninstantiatedBindingException(
          s"""Binding $b is not an instance binding, only forms `make[X].fromValue(instance)` and `many[X].addValue(y).addValue(z)`
             |are supported, binding was defined at ${b.origin}""".stripMargin, b)
    }

    map.toMap -> map.toSeq.map(IdentifiedRef.tupled)
  }
}

object LocatorDef {

  // DSL state machine

  final class BindDSL[T](protected val mutableState: SingletonRef, protected val key: DIKey.TypeKey) extends BindDSLMutBindBase[T] {
    def named[I](name: I)(implicit idContract: IdContract[I]): BindNamedDSL[T] =
      addOp(SetId(name, idContract))(new BindNamedDSL[T](_, key.named(name)))
  }

  final class BindNamedDSL[T](protected val mutableState: SingletonRef, protected val key: DIKey) extends BindDSLMutBindBase[T]

  final class BindDSLAfterFrom[I](override protected val mutableState: SingletonRef, override protected val key: DIKey) extends BindDSLMutBase[I]

  sealed trait BindDSLMutBindBase[T] extends BindDSLBase[T, BindDSLAfterFrom[T]] with BindDSLMutBase[T] {
    override protected def bind(impl: ImplDef): BindDSLAfterFrom[T] =
      addOp(SetImpl(impl))(new BindDSLAfterFrom[T](_, key))
  }

  sealed trait BindDSLMutBase[T] {
    protected[this] def mutableState: SingletonRef
    protected[this] def key: DIKey

    def aliased[T1 >: T: Tag](implicit pos: CodePositionMaterializer): BindDSLAfterFrom[T] = {
      addOp(AliasTo(DIKey.get[T1], pos.get.position))(new BindDSLAfterFrom[T](_, key))
    }

    def aliased[T1 >: T: Tag](name: String)(implicit pos: CodePositionMaterializer): BindDSLAfterFrom[T] = {
      addOp(AliasTo(DIKey.get[T1].named(name), pos.get.position))(new BindDSLAfterFrom[T](_, key))
    }

    protected[this] final def addOp[R](op: SingletonInstruction)(newState: SingletonRef => R): R = {
      newState(mutableState.append(op))
    }
  }

  final class SetDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T] {
    def named[I](name: I)(implicit idContract: IdContract[I]): SetNamedDSL[T] =
      addOp(SetIdAll(name, idContract))(new SetNamedDSL(_))
  }

  final class SetNamedDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T]

  final class SetElementDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T]

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: SetRef

    protected def addOp[R](op: SetInstruction)(nextState: SetRef => R): R = {
      nextState(mutableState.appendOp(op))
    }

    override protected def appendElement(newElement: ImplDef)(implicit pos: CodePositionMaterializer): SetElementDSL[T] = {
      val mutableCursor = new SetElementRef(newElement, pos.get.position)
      new SetElementDSL[T](mutableState.appendElem(mutableCursor))
    }
  }

  trait BindDSLBase[T, AfterBind] {
    final def fromValue[I <: T: Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(SafeType.get[I], instance))

    protected def bind(impl: ImplDef): AfterBind
  }

  trait SetDSLBase[T, AfterAdd] {
    final def addValue[I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance))

    protected def appendElement(newImpl: ImplDef)(implicit pos: CodePositionMaterializer): AfterAdd
  }

}
