package izumi.distage.model.definition

import izumi.distage.AbstractLocator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.ImplDef.InstanceImpl
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.SetIdAll
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction.{AliasTo, SetId, SetImpl}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.*
import izumi.distage.model.exceptions.dsl.LocatorDefUninstantiatedBindingException
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import izumi.distage.model.plan.Wiring.SingletonWiring.Instance
import izumi.distage.model.plan.*
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.*
import izumi.distage.model.{Locator, PlannerInput}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{DG, GraphMeta}
import izumi.fundamentals.platform.language.{CodePositionMaterializer, SourceFilePosition}
import izumi.reflect.{Tag, TagK}

import scala.collection.{immutable, mutable}

// TODO: shameless copypaste of [[ModuleDef]] for now; but we CAN unify all of LocatorDef, ModuleDef, TypeLevelDSL and Bindings DSLs into one...
trait LocatorDef extends AbstractLocator with AbstractBindingDefDSL[LocatorDef.BindDSL, LocatorDef.BindDSLUnnamedAfterFrom, LocatorDef.SetDSL] {

  override def meta: LocatorMeta = LocatorMeta.empty

  override def finalizers[F[_]: TagK]: immutable.Seq[PlanInterpreter.Finalizer[F]] = Nil

  override private[definition] final def _bindDSL[T](ref: SingletonRef): LocatorDef.BindDSL[T] = new LocatorDef.BindDSL[T](ref)
  override private[definition] final def _bindDSLAfterFrom[T](ref: SingletonRef): LocatorDef.BindDSLUnnamedAfterFrom[T] = new LocatorDef.BindDSLUnnamedAfterFrom(ref)
  override private[definition] final def _setDSL[T](ref: SetRef): LocatorDef.SetDSL[T] = new LocatorDef.SetDSL[T](ref)

  protected def initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  override protected def lookupLocalUnsafe(key: DIKey): Option[Any] = {
    frozenMap.get(key)
  }

  override def instances: immutable.Seq[IdentifiedRef] = frozenInstances
  override def index: Map[DIKey, Any] = frozenMap

  /** The plan that produced this object graph */
  override def plan: Plan = {
    val ops = frozenInstances.map {
      case IdentifiedRef(key, value) =>
        val binding = Binding.SingletonBinding[DIKey](key, ImplDef.InstanceImpl(key.tpe, value), Set.empty, SourceFilePosition.unknown)
        val origin = OperationOrigin.SyntheticBinding(binding)
        (UseInstance(key, Instance(key.tpe, value), origin), binding)
    }.toVector

    val s = IncidenceMatrix(ops.map(op => (op._1.target, Set.empty[DIKey])).toMap)
    val nodes = ops.map(op => (op._1.target, op._1))
    Plan(DG(s, s.transposed, GraphMeta(nodes.toMap)), PlannerInput(Module.make(ops.map(_._2).toSet), Activation.empty, Roots.Everything))
  }

  override def parent: Option[Locator] = None

  private[this] final lazy val (frozenMap, frozenInstances): (Map[DIKey, Any], immutable.Seq[IdentifiedRef]) = {
    val map = new mutable.LinkedHashMap[DIKey, Any]

    frozenState.foreach {
      case SingletonBinding(key, InstanceImpl(_, instance), _, _, false) =>
        map += (key -> instance)
      case SetElementBinding(key, InstanceImpl(_, instance), _, _) =>
        val setKey = key.set
        map += (setKey -> (map.getOrElse(setKey, Set.empty[Any]).asInstanceOf[Set[Any]] + instance))
      case e: EmptySetBinding[?] =>
        map.getOrElseUpdate(e.key, Set.empty[Any])
      case b =>
        throw new LocatorDefUninstantiatedBindingException(
          s"""Binding $b is not an instance binding, only forms `make[X].fromValue(instance)` and `many[X].addValue(y).addValue(z)`
             |are supported, binding was defined at ${b.origin}""".stripMargin,
          b,
        )
    }

    map.toMap -> map.iterator.map { case (k, v) => IdentifiedRef(k, v) }.toList
  }
}

object LocatorDef {

  // DSL state machine

  final class BindDSL[T](protected val mutableState: SingletonRef) extends BindDSLBase[T, BindDSLUnnamedAfterFrom[T]] with BindDSLMutBase[T] {
    def named(name: Identifier): BindNamedDSL[T] =
      addOp(SetId(name))(new BindNamedDSL[T](_))

    override protected def bind(impl: ImplDef): BindDSLUnnamedAfterFrom[T] =
      addOp(SetImpl(impl))(new BindDSLUnnamedAfterFrom[T](_))
  }

  final class BindNamedDSL[T](protected val mutableState: SingletonRef) extends BindDSLBase[T, BindDSLNamedAfterFrom[T]] with BindDSLMutBase[T] {
    override protected def bind(impl: ImplDef): BindDSLNamedAfterFrom[T] =
      addOp(SetImpl(impl))(new BindDSLNamedAfterFrom[T](_))
  }

  final class BindDSLUnnamedAfterFrom[T](override protected val mutableState: SingletonRef) extends BindDSLMutBase[T] {
    def named(name: Identifier): BindNamedDSL[T] =
      addOp(SetId(name))(new BindNamedDSL[T](_))
  }

  final class BindDSLNamedAfterFrom[T](override protected val mutableState: SingletonRef) extends BindDSLMutBase[T]
  final class BindDSLAfterAlias[T](override protected val mutableState: SingletonRef) extends BindDSLMutBase[T]

  sealed trait BindDSLMutBase[T] {
    protected[this] def mutableState: SingletonRef

    def aliased[T1 >: T: Tag](implicit pos: CodePositionMaterializer): BindDSLAfterAlias[T] = {
      addOp(AliasTo(DIKey.get[T1], pos.get.position))(new BindDSLAfterAlias[T](_))
    }

    def aliased[T1 >: T: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): BindDSLAfterAlias[T] = {
      addOp(AliasTo(DIKey.get[T1].named(name), pos.get.position))(new BindDSLAfterAlias[T](_))
    }

    protected[this] final def addOp[R](op: SingletonInstruction)(newState: SingletonRef => R): R = {
      newState(mutableState.append(op))
    }
  }

  final class SetDSL[T](protected val mutableState: SetRef) extends SetDSLMutBase[T] {
    def named(name: Identifier): SetNamedDSL[T] =
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
