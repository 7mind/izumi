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

  override def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring = {
    wiring match {
      case w: Wiring.UnaryWiring.Constructor =>
        val parameters = reflectionProvider.constructorParameters(w.instanceType)
          .map(p => p.symbol.name -> p)
          .toMap

        val newAssociactions = w.associations.map {
          a =>
            val parameter = parameters(a.symbol.name)

            a.copy(wireWith = rewire(binding, parameter, a))
        }

        w.copy(associations = newAssociactions)

      case w =>
        w
    }
  }

  protected def findAnno[T: TypeTag](association: Association.Parameter): Option[Annotation] =
    association.symbol.findAnnotation(SafeType.get[T])

  protected def rewire(binding: Binding.ImplBinding, reflected: Association.Parameter, association: Association.Parameter): DIKey = {
    val confPathAnno = findAnno[ConfPath](association)
    val confAnno = findAnno[Conf](association)
    val autoConfAnno = findAnno[AutoConf](association)

    // TODO: can we decopypaste?
    confPathAnno match {
      case Some(ann) =>
        ann.tree.children.tail.collectFirst {
          case Literal(Constant(path: String)) =>
            path
        } match {
          case Some(path) =>
            association.wireWith match {
              case k: DIKey.TypeKey =>
                return k.named(ConfPathId(binding.key, association, path))

              case o =>
                throw new DIException(s"Cannot rewire @ConfPath parameter $reflected: unexpected binding $o", null)
            }
          case None =>
            throw new DIException(s"Cannot rewire @ConfPath parameter $reflected: undefined path", null)

        }

      case _ =>
    }

    confAnno match {
      case Some(ann) =>
        ann.tree.children.tail.collectFirst {
          case Literal(Constant(name: String)) =>
            name
        } match {
          case Some(name) =>
            association.wireWith match {
              case k: DIKey.TypeKey =>
                return k.named(ConfId(binding.key, association, name))

              case o =>
                throw new DIException(s"Cannot rewire @Conf parameter $reflected: unexpected binding $o", null)
            }
          case None =>
            throw new DIException(s"Cannot rewire @Conf parameter $reflected: undefined name", null)

        }

      case _ =>
    }

    autoConfAnno match {
      case Some(_) =>
        association.wireWith match {
          case k: DIKey.TypeKey =>
            return k.named(AutoConfId(binding.key, association))

          case o =>
            throw new DIException(s"Cannot rewire @AutoConf parameter $reflected: unexpected binding $o", null)
        }

      case _ =>
    }


    association.wireWith
  }

}
