package com.github.pshirshov.izumi.distage.planning.extensions

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanningObserver}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.graphs.dotml.Digraph
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import distage.{DIKey, Id}

import scala.collection.mutable

class GraphObserver(planAnalyzer: PlanAnalyzer, @Id("gc.roots") roots: Set[RuntimeDIUniverse.DIKey]) extends PlanningObserver {
  private val beforeFinalization = new AtomicReference[SemiPlan](null)

  override def onSuccessfulStep(next: DodgyPlan): Unit = {}

  override def onPhase00PlanCompleted(plan: DodgyPlan): Unit = synchronized {
    beforeFinalization.set(null)
  }

  override def onPhase05PreFinalization(plan: SemiPlan): Unit = synchronized {
    beforeFinalization.set(plan)
  }

  override def onPhase10PostFinalization(plan: SemiPlan): Unit = {}

  override def onPhase20Customization(plan: SemiPlan): Unit = {}

  override def onPhase50PreForwarding(plan: SemiPlan): Unit = {}

  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = synchronized {
    val dotfile = render(finalPlan)
    val name = s"plan-${System.currentTimeMillis()}.gv"
    val path = Paths.get(s"target", name)
    val last = Paths.get(s"target", "plan-last.gv")

    import Quirks._

    Files.write(path, dotfile.getBytes(StandardCharsets.UTF_8)).discard()
    Files.deleteIfExists(last).discard()
    Files.createLink(last, path).discard()
  }

  private def render(finalPlan: OrderedPlan): String = {
    val g = new Digraph()

    val preTopology = planAnalyzer.topology(beforeFinalization.get().steps)

    val removedKeys = preTopology.dependencies.graph.keySet
    val goodKeys = finalPlan.topology.dependencies.graph.keySet
    val missingKeys = removedKeys.diff(goodKeys)

    goodKeys.foreach {
      k =>

        val rootStyle = if (roots.contains(k)) {
          Map("fillcolor" -> "dodgerblue1", "peripheries" -> "2")
        } else {
          Map("fillcolor" -> "darkolivegreen3")
        }
        val attrs = mutable.Map("style" -> "filled", "shape" -> "box") ++ rootStyle

        g.node(render(k), attrs = attrs)
    }


    finalPlan.topology.dependencies.graph.foreach {
      case (k, deps) =>
        deps.foreach {
          d =>
            g.edge(render(k), render(d))
        }
    }

    missingKeys.foreach {
      k =>
        val attrs = mutable.Map("style" -> "filled", "shape" -> "box", "fillcolor" -> "coral1")
        g.node(render(k), attrs = attrs)
    }

    preTopology.dependencies.graph.foreach {
      case (k, deps) =>
        deps.foreach {
          d =>
            if ((missingKeys.contains(k) && !missingKeys.contains(d)) || (missingKeys.contains(d) && !missingKeys.contains(k))) {
              g.edge(render(k), render(d), attrs = mutable.Map("color" -> "coral1"))
            }
        }
    }

    g.source()
  }

  private def render(key: DIKey): String = {
    key match {
      case DIKey.TypeKey(tpe) =>
        s"${render(tpe)}"

      case DIKey.IdKey(tpe, id) =>
        s"${render(tpe)}#$id"

      case DIKey.ProxyElementKey(proxied, _) =>
        s"proxy: ${render(proxied)}"

      case DIKey.SetElementKey(set, reference) =>
        s"set: ${render(set)}/${render(reference)}"
    }
  }

  private def render(tpe: RuntimeDIUniverse.SafeType): String = {
    render(tpe.tpe)
  }
  private def render(tpe: RuntimeDIUniverse.TypeNative): String = {
    val args = if (tpe.typeArgs.nonEmpty) {
      tpe.typeArgs.map(render).mkString("[", ",", "]")
    } else {
      ""
    }
    tpe.typeSymbol.name + args
  }
}

