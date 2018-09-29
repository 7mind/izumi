package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.AbstractLocator
import com.github.pshirshov.izumi.distage.model.{Locator, definition}
import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.ImplDef.InstanceImpl
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL._
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.SetIdAll
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction.{SetId, SetImpl}
import com.github.pshirshov.izumi.distage.model.exceptions.LocatorDefUninstantiatedBindingException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring.Instance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

import scala.collection.mutable

// TODO: shameless copypaste of [[ModuleDef]] for now; but we ARE able to unify all of LocatorDef, ModuleDef, TypeLevelDSL and [[Bindings]] DSLs into one!
trait LocatorDef
  extends AbstractLocator with AbstractBindingDefDSL {

  override private[definition] type BindDSL[T] = LocatorDef.BindDSL[T]
  override private[definition] type SetDSL[T] = LocatorDef.SetDSL[T]

  override private[definition] def _bindDSL[T: RuntimeDIUniverse.Tag](ref: SingletonRef): BindDSL[T] =
    new definition.LocatorDef.BindDSL[T](ref, ref.key)

  override private[definition] def _setDSL[T: RuntimeDIUniverse.Tag](ref: SetRef): SetDSL[T] =
    new definition.LocatorDef.SetDSL[T](ref)

  protected def initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  override protected def unsafeLookup(key: DIKey): Option[Any] = {
    frozenMap.get(key)
  }

  override def instances: Seq[IdentifiedRef] = frozenInstances.toList

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

    frozenState.foreach {
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
}


object LocatorDef {

  // DSL state machine

  final class BindDSL[T](protected val mutableState: SingletonRef, protected val key: DIKey.TypeKey) extends BindDSLMutBase[T] {
    def named(name: String): BindNamedDSL[T] =
      addOp(SetId(name))(new BindNamedDSL[T](_, key.named(name)))
  }

  final class BindNamedDSL[T](protected val mutableState: SingletonRef, protected val key: DIKey) extends BindDSLMutBase[T]

  sealed trait BindDSLMutBase[T] extends BindDSLBase[T, Unit] {
    protected def mutableState: SingletonRef

    protected def key: DIKey

    override protected def bind(impl: ImplDef): Unit =
      addOp(SetImpl(impl))(_ => ())

    protected def addOp[R](op: SingletonInstruction)(newState: SingletonRef => R): R = {
      newState(mutableState.append(op))
    }
  }

  final class SetDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T] {
    def named(name: String): SetNamedDSL[T] =
      addOp(SetIdAll(name))(new SetNamedDSL(_))
  }

  final class SetNamedDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T]

  final class SetElementDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T]

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: SetRef

    protected def addOp[R](op: SetInstruction)(nextState: SetRef => R): R = {
      nextState(mutableState.appendOp(op))
    }

    override protected def appendElement(newElement: ImplDef)(implicit pos: CodePositionMaterializer): SetElementDSL[T] = {
      val mutableCursor = SetElementRef(newElement, pos.get.position)
      new SetElementDSL[T](mutableState.appendElem(mutableCursor))
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
