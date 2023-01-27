package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding}
import izumi.distage.model.definition.{Binding, ImplDef, ModuleBase}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.*
import izumi.distage.model.reflection.DIKey.{SetElementKey, SetKeyMeta}
import izumi.distage.planning.AutoSetHook.AutoSetHookFilter
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}
import izumi.reflect.Tag

/**
  * A hook that will collect all implementations with types that are {{{_ <: T}}} into a `Set[T]` set binding
  * available for summoning
  *
  * Usage:
  *
  * {{{
  *   val collectCloseables = AutoSetHook[AutoCloseable]
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
class AutoSetHook[INSTANCE: Tag, BINDING: Tag](protected val includeOnly: AutoSetHookFilter, protected val pos: CodePosition) extends PlanningHook {
  protected val instanceType: SafeType = SafeType.get[INSTANCE]
  protected val setElementType: SafeType = SafeType.get[BINDING]
  protected val setKey: DIKey = DIKey.get[Set[BINDING]]

  override def equals(obj: Any): Boolean = obj match {
    case a: AutoSetHook[_, _] =>
      a.instanceType == instanceType &&
      a.setElementType == setElementType &&
      a.setKey == setKey &&
      a.includeOnly == includeOnly &&
      a.pos == pos
    case _ => false
  }

  override def hashCode(): Int = (includeOnly, pos, instanceType, setElementType, setKey).hashCode()

  override def hookDefinition(definition: ModuleBase): ModuleBase = {
    val setMembers: Set[Binding.ImplBinding] = definition.bindings
      .flatMap {
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
        case _: EmptySetBinding[?] =>
          None
      }.filter(includeOnly.filter)

    if (setMembers.isEmpty) {
      definition
    } else {
      val elementOps: Set[Binding] = setMembers.flatMap {
        b =>
          val isAutosetElement = b.key match {
            case k: SetElementKey =>
              k.disambiguator match {
                case _: SetKeyMeta.WithAutoset =>
                  true
                case _ =>
                  false
              }
            case _ =>
              false
          }

          if (!isAutosetElement) {
            val implType = b.implementation.implType
            val impl: ImplDef = ImplDef.ReferenceImpl(implType, b.key, weak = true)
            Seq(SetElementBinding(SetElementKey(setKey, b.key, SetKeyMeta.WithAutoset(setKey)), impl, b.tags, b.origin))
          } else {
            Seq.empty
          }
      }

      val ops: Set[Binding] = Set(EmptySetBinding(setKey, Set.empty, pos.position))
      definition ++ ModuleBase.make(ops ++ elementOps)
    }

  }
}

object AutoSetHook {
  trait AutoSetHookFilter {
    def filter(b: Binding.ImplBinding): Boolean
  }
  object AutoSetHookFilter {
    object empty extends AutoSetHookFilter {
      override def filter(b: ImplBinding): Boolean = true
    }
  }
  def apply[INSTANCE: Tag, BINDING: Tag](includeOnly: AutoSetHookFilter)(implicit pos: CodePositionMaterializer): AutoSetHook[INSTANCE, BINDING] = {
    new AutoSetHook[INSTANCE, BINDING](includeOnly, pos.get)
  }

  def apply[INSTANCE: Tag](implicit pos: CodePositionMaterializer): AutoSetHook[INSTANCE, INSTANCE] = {
    new AutoSetHook(AutoSetHookFilter.empty, pos.get)
  }

  def apply[INSTANCE: Tag, BINDING: Tag](implicit pos: CodePositionMaterializer): AutoSetHook[INSTANCE, BINDING] = {
    new AutoSetHook[INSTANCE, BINDING](AutoSetHookFilter.empty, pos.get)
  }
}
