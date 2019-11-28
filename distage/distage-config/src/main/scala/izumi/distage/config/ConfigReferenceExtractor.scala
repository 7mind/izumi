package izumi.distage.config

import izumi.distage.config.annotations._
import izumi.distage.config.model.exceptions.ConfigTranslationException
import izumi.distage.model.definition.Binding
import izumi.distage.model.exceptions.BadIdAnnotationException
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.reflection.AnnotationTools

/**
  * Finds parameters of bindings with @Conf, @ConfPath or @AutoConf annotations and replaces their keys with Conf IdKeys
  * To then be processed by `ConfigProvider` plan rewriter
  */
class ConfigReferenceExtractor extends PlanningHook {

  import scala.reflect.runtime.universe._

  override def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring = {
    wiring.replaceKeys(rewire(binding, _))
  }

  protected def rewire(binding: Binding.ImplBinding, association: Association): DIKey.BasicKey = {
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
    association.context.symbol.findUniqueAnnotation(typeOf[T])
  }

  protected def findArgument(ann: Annotation): Option[String] = {
    AnnotationTools.findArgument(ann) {
      case Literal(Constant(str: String)) =>
        str
      case o =>
        throw new BadIdAnnotationException(ann.tree.tpe.toString, o)
    }
  }
}
