package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.annotations._
import com.github.pshirshov.izumi.distage.config.model.exceptions.ConfigTranslationException
import com.github.pshirshov.izumi.distage.model.definition.{Binding, DIStageAnnotation}
import com.github.pshirshov.izumi.distage.model.exceptions.BadAnnotationException
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

/**
  * Finds parameters of bindings with @Conf, @ConfPath or @AutoConf annotations and replaces their keys with Conf IdKeys
  * To then be processed by `ConfigProvider` plan rewriter
  */
class ConfigReferenceExtractor(protected val reflectionProvider: ReflectionProvider.Runtime) extends PlanningHook {

  import u._

  override def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring = {
    wiring.replaceKeys(rewire(binding, _))
  }

  protected def rewire(binding: Binding.ImplBinding, association: Association): DIKey = {
    val confPathAnno = findAnno[ConfPath](association)
    val confAnno = findAnno[Conf](association)
    val autoConfAnno = findAnno[AutoConf](association)

    confPathAnno match {
      case Some(ann) =>
        findArgument(ann) match {
          case Some(path) =>
            association.wireWith match {
              case k: DIKey.TypeKey =>
                return k.named(ConfPathId(binding.key, association.name, path))

              case o =>
                throw new ConfigTranslationException(s"Cannot rewire @ConfPath parameter $association: unexpected binding $o", Seq())
            }
          case None =>
            throw new ConfigTranslationException(s"Cannot rewire @ConfPath parameter $association: undefined path", Seq())

        }

      case _ =>
    }

    confAnno match {
      case Some(ann) =>
        findArgument(ann) match {
          case Some(name) =>
            association.wireWith match {
              case k: DIKey.TypeKey =>
                return k.named(ConfId(binding.key, association.name, name))

              case o =>
                throw new ConfigTranslationException(s"Cannot rewire @Conf parameter $association: unexpected binding $o", Seq())
            }
          case None =>
            throw new ConfigTranslationException(s"Cannot rewire @Conf parameter $association: undefined name", Seq())

        }

      case _ =>
    }

    autoConfAnno match {
      case Some(_) =>
        association.wireWith match {
          case k: DIKey.TypeKey =>
            return k.named(AutoConfId(binding.key, association.name))

          case o =>
            throw new ConfigTranslationException(s"Cannot rewire @AutoConf parameter $association: unexpected binding $o", Seq())
        }

      case _ =>
    }

    association.wireWith
  }

  protected def findAnno[T: TypeTag](association: Association): Option[Annotation] = {
    association.context.symbol.findUniqueAnnotation(SafeType.get[T])
  }

  protected def findArgument(ann: Annotation): Option[String] = {
    AnnotationTools.findArgument(ann) {
      case Literal(Constant(str: String)) =>
        str
      case o =>
        throw new BadAnnotationException(ann.tree.tpe.toString, o)
    }
  }
}
