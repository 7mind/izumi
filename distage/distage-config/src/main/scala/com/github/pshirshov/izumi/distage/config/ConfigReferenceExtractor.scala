package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.annotations._
import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

class ConfigReferenceExtractor(protected val reflectionProvider: ReflectionProvider.Runtime) extends PlanningHook {

  import u._

  override def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring =
    wiring.replaceKeys(rewire(binding, _))

  protected def findAnno[T: TypeTag](association: Association): Option[Annotation] =
    association.context.symbol.findAnnotation(SafeType.get[T])

  protected def findArgument(ann: Annotation): Option[String] =
    AnnotationTools.findArgument(ann) {
      case Literal(Constant(str: String)) =>
        str
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
                return k.named(ConfPathId(binding.key, association, path))

              case o =>
                throw new DIException(s"Cannot rewire @ConfPath parameter $association: unexpected binding $o", null)
            }
          case None =>
            throw new DIException(s"Cannot rewire @ConfPath parameter $association: undefined path", null)

        }

      case _ =>
    }

    confAnno match {
      case Some(ann) =>
        findArgument(ann) match {
          case Some(name) =>
            association.wireWith match {
              case k: DIKey.TypeKey =>
                return k.named(ConfId(binding.key, association, name))

              case o =>
                throw new DIException(s"Cannot rewire @Conf parameter $association: unexpected binding $o", null)
            }
          case None =>
            throw new DIException(s"Cannot rewire @Conf parameter $association: undefined name", null)

        }

      case _ =>
    }

    autoConfAnno match {
      case Some(_) =>
        association.wireWith match {
          case k: DIKey.TypeKey =>
            return k.named(AutoConfId(binding.key, association))

          case o =>
            throw new DIException(s"Cannot rewire @AutoConf parameter $association: unexpected binding $o", null)
        }

      case _ =>
    }

    association.wireWith
  }

}
