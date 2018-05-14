package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

class ConfigReferenceExtractor(protected val reflectionProvider: ReflectionProvider.Runtime) extends PlanningHook {
  import u._
  override def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring = {
    wiring match {
      case w: Wiring.UnaryWiring.Constructor =>
        val parameters = reflectionProvider.constructorParameters(w.instanceType)
          .map(p => p.name -> p)
          .toMap

        val newAssociactions = w.associations.map {
          a =>
            val parameter = parameters(a.name)

            a.copy(wireWith = rewire(binding, parameter, a))
        }

        w.copy(associations = newAssociactions)

      case w =>
        w
    }
  }


  protected def rewire(binding: Binding.ImplBinding, reflected: Association.ExtendedParameter, association: Association.Parameter): DIKey = {
    val autoConfAnno = AnnotationTools.find(RuntimeDIUniverse.u)(typeOf[AutoConf], reflected.symb)
    val confAnno = AnnotationTools.find(RuntimeDIUniverse.u)(typeOf[Conf], reflected.symb)

    autoConfAnno.map {
      _ =>
        association.wireWith match {
          case k: DIKey.TypeKey =>
            k.named(AutoConfId(binding.key, association))

          case o =>
            throw new DIException(s"Cannot rewire @AutoConf parameter $reflected: unexpected binding $o", null)
        }
    }.orElse {
      confAnno.flatMap {
        _.tree.children.tail.collectFirst {
          case Literal(Constant(name: String)) =>
            name
        }
      }.map {
        ann =>
          association.wireWith match {
            case k: DIKey.TypeKey =>
              k.named(ConfId(binding.key, association, ann))

            case o =>
              throw new DIException(s"Cannot rewire @Conf parameter $reflected: unexpected binding $o", null)
          }
      }
    }.getOrElse {
      association.wireWith
    }
  }

}
