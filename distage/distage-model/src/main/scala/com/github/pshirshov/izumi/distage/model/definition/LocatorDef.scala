package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.AbstractLocator
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.ImplDef.InstanceImpl
import com.github.pshirshov.izumi.distage.model.exceptions.LocatorDefUninstantiatedBindingException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring.Instance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

import scala.collection.mutable

// TODO: shameless copypaste of [[ModuleDef]] for now; but we ARE able unify all of LocatorDef, ModuleDef, TypeLevelDSL and [[Bindings]] DSLs into one!
trait LocatorDef
  extends AbstractLocator with AbstractModuleDefDSL {

  import AbstractModuleDefDSL._

  protected def initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  override protected def unsafeLookup(key: DIKey): Option[Any] = {
    frozenMap.get(key)
  }

  override def instances: Seq[IdentifiedRef] = frozenInstances

  override lazy val plan: OrderedPlan = {
    val topology = PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Required), DependencyGraph(Map.empty, DependencyKind.Depends))

    val ops = frozenInstances.map {
      case IdentifiedRef(key, value) =>
        ReferenceInstance(key, Instance(key.tpe, value), None)
    }.toVector

    val moduleDef = Module.make(
      frozenInstances.map {
        case IdentifiedRef(key, value) =>
          Binding.SingletonBinding[DIKey](key, ImplDef.InstanceImpl(key.tpe, value))
      }.toSet
    )

    OrderedPlan(moduleDef, ops, topology)
  }

  override def parent: Option[Locator] = None

  private[this] final lazy val (frozenMap, frozenInstances): (Map[DIKey, Any], Seq[IdentifiedRef]) = {
    val map = new mutable.LinkedHashMap[DIKey, Any]

    freeze.foreach {
      case SingletonBinding(key, InstanceImpl(_, instance), _, _) =>
        map += (key -> instance)
      case SetElementBinding(key, InstanceImpl(_, instance), _, _) =>
        map += (key -> (map.getOrElse(key, Set.empty[Any]).asInstanceOf[Set[Any]] + instance))
      case e: EmptySetBinding[_] =>
        map.getOrElseUpdate(e.key, Set.empty[Any])
      case b =>
        throw new LocatorDefUninstantiatedBindingException(
          s"""Binding $b is not an instance binding, only forms `make[X].from(instance)` and `many[X].add(y).add(z)`
             |are supported, binding was defined at ${b.origin}""".stripMargin, b)
    }

    map.toMap -> map.toSeq.map(IdentifiedRef.tupled)
  }

  private[this] final def freeze: Set[Binding] = frozenState
}


object LocatorDef {

  import AbstractModuleDefDSL._

  // DSL state machine

  final class BindDSL[T]
  (
    protected val mutableState: SingletonRef
    , protected val binding: SingletonBinding[DIKey.TypeKey]
  ) extends BindDSLMutBase[T] {

    def named(name: String): BindNamedDSL[T] =
      replace(binding.copy(key = binding.key.named(name), tags = binding.tags)) {
        new BindNamedDSL[T](mutableState, _)
      }

  }

  final class BindNamedDSL[T]
  (
    protected val mutableState: SingletonRef
    , protected val binding: Binding.SingletonBinding[DIKey]
  ) extends BindDSLMutBase[T]

  sealed trait BindDSLMutBase[T] extends BindDSLBase[T, Unit] {
    protected def mutableState: SingletonRef

    protected val binding: SingletonBinding[DIKey]

    protected def replace[B <: Binding, S](newBinding: B)(newState: B => S): S = {
      mutableState.ref = newBinding
      newState(newBinding)
    }

    override protected def bind(impl: ImplDef): Unit =
      replace(binding.withImpl(impl))(_ => ())
  }

  final case class IdentSet[+D <: DIKey](key: D, tags: Set[String], pos: SourceFilePosition) {
    def sameIdent(binding: Binding): Boolean =
      key == binding.key && tags == binding.tags
  }

  final class SetDSL[T]
  (
    protected val mutableState: SetRef
    , protected val identifier: IdentSet[DIKey.TypeKey]
  ) extends SetDSLMutBase[T] {

    def named(name: String): SetNamedDSL[T] =
      replaceIdent(identifier.copy(key = identifier.key.named(name))) {
        new SetNamedDSL(mutableState, _)
      }

  }

  final class SetNamedDSL[T]
  (
    protected val mutableState: SetRef
    , protected val identifier: IdentSet[DIKey]
  ) extends SetDSLMutBase[T]

  final class SetElementDSL[T]
  (
    protected val mutableState: SetRef
    , protected val mutableCursor: SingletonRef
    , protected val identifier: IdentSet[DIKey]
  ) extends SetElementDSLMutBase[T]

  sealed trait SetElementDSLMutBase[T] extends SetDSLMutBase[T] {
    protected val mutableCursor: SingletonRef

    protected def replaceCursor(newBindingCursor: Binding): SetElementDSL[T] = {
      mutableCursor.ref = newBindingCursor

      new SetElementDSL[T](mutableState, mutableCursor, identifier)
    }
  }

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: SetRef

    protected def identifier: IdentSet[DIKey]

    protected def replaceIdent[D <: IdentSet[DIKey], S](newIdent: D)(nextState: D => S): S = {
      mutableState.emptySetBinding.ref = EmptySetBinding(newIdent.key, newIdent.tags, newIdent.pos)
      mutableState.all.foreach(r => r.ref = r.ref.withTarget(newIdent.key))

      nextState(newIdent)
    }

    override protected def appendElement(newElement: ImplDef)(implicit pos: CodePositionMaterializer): SetElementDSL[T] = {
      val newBinding: Binding = SetElementBinding(identifier.key, newElement)
      val mutableCursor = SingletonRef(newBinding)

      mutableState.all += mutableCursor

      new SetElementDSL[T](mutableState, mutableCursor, identifier)
    }
  }

  trait BindDSLBase[T, AfterBind] {
    final def from[I <: T : Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(SafeType.get[I], instance))

    protected def bind(impl: ImplDef): AfterBind
  }

  trait SetDSLBase[T, AfterAdd] {
    final def add[I <: T : Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance))

    protected def appendElement(newImpl: ImplDef)(implicit pos: CodePositionMaterializer): AfterAdd
  }


}
