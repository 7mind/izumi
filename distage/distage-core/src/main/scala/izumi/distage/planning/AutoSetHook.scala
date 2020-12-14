package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding}
import izumi.distage.model.definition.{Binding, ImplDef, ModuleBase}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.DIKey.SetElementKey
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

/**
  * A hook that will collect all implementations with types that are {{{_ <: T}}} into a `Set[T]` set binding
  * available for summoning
  *
  * Usage:
  *
  * {{{
  *   val collectCloseables = new AutoSetHook[AutoCloseable, AutoCloseable]
  *
  *   val injector = Injector(new BootstrapModuleDef {
  *     many[PlanningHook]
  *       .add(collectCloseables)
  *   })
  * }}}
  *
  * Then, in any class created from `injector`:
  *
  * {{{
  *   class App(allCloseables: Set[AutoCloseable]) {
  *     ...
  *   }
  * }}}
  *
  * These Auto-Sets can be used to implement custom lifetimes:
  *
  * {{{
  *   val locator = injector.produce(modules)
  *
  *   val closeables = locator.get[Set[AutoCloseable]]
  *   try { locator.get[App].runMain() } finally {
  *     // reverse closeables list, Auto-Sets preserve order, in the order of *initialization*
  *     // Therefore resources should closed in the *opposite order*
  *     // e.g. if C depends on B depends on A, create: A -> B -> C, close: C -> B -> A
  *     closeables.reverse.foreach(_.close())
  *   }
  * }}}
  *
  * Auto-Sets are NOT subject to Garbage Collection, they are assembled
  * *after* garbage collection is done, as such they can't contain garbage by construction
  * and they cannot be designated as GC root keys.
  */
class AutoSetHook[INSTANCE: Tag, BINDING: Tag](implicit pos: CodePositionMaterializer) extends PlanningHook {
  protected val instanceType: SafeType = SafeType.get[INSTANCE]
  protected val setElementType: SafeType = SafeType.get[BINDING]
  protected val setKey: DIKey = DIKey.get[Set[BINDING]]

  override def hookDefinition(definition: ModuleBase): ModuleBase = {
    val setMembers: Set[Binding.ImplBinding] = definition.bindings.flatMap {
      case i: ImplBinding =>
        i.implementation match {
          case implDef: ImplDef.DirectImplDef =>
            val implType = implDef.implType
            if (implType <:< setElementType) {
              Some(i)
            } else {
              None
            }

          case implDef: ImplDef.RecursiveImplDef =>
            if (implDef.implType <:< setElementType) {
              Some(i)
            } else {
              None
            }
        }
      case _: EmptySetBinding[_] =>
        None

    }

    if (setMembers.isEmpty) {
      definition
    } else {
      val position = pos.get.position

      val elementOps: Set[Binding] = setMembers.map {
        b =>
          val implType = b.implementation.implType
          val impl: ImplDef = ImplDef.ReferenceImpl(implType, b.key, weak = true)

          SetElementBinding(SetElementKey(setKey, b.key, None), impl, Set.empty, position)
      }
      val ops: Set[Binding] = Set(EmptySetBinding(setKey, Set.empty, position))
      definition ++ ModuleBase.make(ops ++ elementOps)
    }

  }
}
