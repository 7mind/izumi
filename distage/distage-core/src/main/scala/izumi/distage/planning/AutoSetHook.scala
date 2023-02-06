package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding}
import izumi.distage.model.definition.{Binding, Identifier, ImplDef, ModuleBase}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.*
import izumi.distage.model.reflection.DIKey.{SetElementKey, SetKeyMeta}
import izumi.distage.planning.AutoSetHook.InclusionPredicate
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}
import izumi.reflect.Tag

/**
  * A hook that will collect all implementations with types that are {{{_ <: T}}} into a `Set[T]` set binding
  * available for summoning.
  *
  * This class is not intended to be used directly, there is a convenience helper, [[AutoSetModule]].
  *
  * Usage:
  *
  * {{{
  *   val collectCloseables = AutoSetHook[AutoCloseable]("closeables")
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
  * These Auto-Sets can be used (just as example) to implement custom lifecycles:
  *
  * {{{
  *   val locator = injector.produce(modules)
  *
  *   val closeables = locator.get[Set[AutoCloseable]]("closeables")
  *   try { locator.get[App].runMain() } finally {
  *     // reverse closeables list, Auto-Sets preserve order, in the order of *initialization*
  *     // Therefore resources should closed in the *opposite order*
  *     // e.g. if C depends on B depends on A, create: A -> B -> C, close: C -> B -> A
  *     closeables.reverse.foreach(_.close())
  *   }
  * }}}
  */
final case class AutoSetHook[BINDING: Tag] private (includeOnly: InclusionPredicate, name: Option[Identifier], weak: Boolean, pos: CodePosition) extends PlanningHook {
  protected val setElementType: SafeType = SafeType.get[BINDING]

  protected val setKey: DIKey = name match {
    case Some(value) => DIKey.get[Set[BINDING]].named(value)
    case None => DIKey.get[Set[BINDING]]
  }

  override def hookDefinition(definition: ModuleBase): ModuleBase = {
    val setMembers = findMatchingBindings(definition)

    val elements = if (setMembers.isEmpty) {
      Set.empty[Binding]
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
            val impl: ImplDef = ImplDef.ReferenceImpl(implType, b.key, weak = weak)
            Seq(SetElementBinding(SetElementKey(setKey, b.key, SetKeyMeta.WithAutoset(setKey)), impl, b.tags, b.origin))
          } else {
            Seq.empty
          }
      }

      elementOps
    }

    val declareSet = definition ++ ModuleBase.make(Set(EmptySetBinding(setKey, Set.empty, pos.position)))
    declareSet ++ ModuleBase.make(elements)
  }

  private def findMatchingBindings(definition: ModuleBase): Set[ImplBinding] = {
    definition.bindings
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
      }
      .filter(includeOnly.filter)
  }
}

object AutoSetHook {

  trait InclusionPredicate {
    def filter(b: Binding.ImplBinding): Boolean
  }

  object InclusionPredicate {
    object IncludeAny extends InclusionPredicate {
      override def filter(b: ImplBinding): Boolean = true
    }
  }

  def apply[T: Tag](implicit pos: CodePositionMaterializer): AutoSetHook[T] = {
    new AutoSetHook[T](InclusionPredicate.IncludeAny, None, true, pos.get)
  }

  def apply[T: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): AutoSetHook[T] = {
    new AutoSetHook[T](InclusionPredicate.IncludeAny, Some(name), true, pos.get)
  }

  def apply[T: Tag](
    includeOnly: InclusionPredicate = InclusionPredicate.IncludeAny,
    name: Identifier = null,
    weak: Boolean = true,
  )(implicit pos: CodePositionMaterializer
  ): AutoSetHook[T] = {
    new AutoSetHook[T](includeOnly, Option(name), weak, pos.get)
  }

}
