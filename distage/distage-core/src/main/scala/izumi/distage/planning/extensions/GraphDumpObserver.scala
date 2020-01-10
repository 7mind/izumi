//package izumi.distage.planning.extensions
//
//import java.nio.charset.StandardCharsets
//import java.nio.file.{Files, Paths}
//import java.util.concurrent.atomic.AtomicReference
//
//import distage._
//import izumi.distage.model.plan.ExecutableOp.{MonadicOp, ProxyOp}
//import izumi.distage.model.plan.initial.PrePlan
//import izumi.distage.model.plan.repr.KeyMinimizer
//import izumi.distage.model.plan.{OrderedPlan => _, SemiPlan => _, _}
//import izumi.distage.model.planning.{PlanAnalyzer, PlanningObserver}
//import izumi.distage.model.reflection.universe.RuntimeDIUniverse
//import izumi.distage.planning.extensions.GraphDumpObserver.RenderedDot
//import izumi.fundamentals.graphs.dotml.Digraph
//import izumi.fundamentals.platform.language.Quirks._
//
//import scala.collection.mutable
//
//final class GraphDumpObserver
//(
//  planAnalyzer: PlanAnalyzer
//) extends PlanningObserver {
//  private[this] val beforeFinalization = new AtomicReference[SemiPlan](null)
//
//  override def onSuccessfulStep(next: PrePlan): Unit = {}
//
//  override def onPhase00PlanCompleted(plan: PrePlan): Unit = synchronized {
//    beforeFinalization.set(null)
//  }
//
//  override def onPhase05PreGC(plan: SemiPlan): Unit = synchronized {
//    beforeFinalization.set(plan)
//  }
//
//  override def onPhase10PostGC(plan: SemiPlan): Unit = {}
//
//  override def onPhase20Customization(plan: SemiPlan): Unit = {}
//
//  override def onPhase50PreForwarding(plan: SemiPlan): Unit = {}
//
//  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = synchronized {
//    val dotfileFull = render(finalPlan, withGc = true)
//    val dotfileMin = render(finalPlan, withGc = false)
//    save(dotfileFull, "full")
//    save(dotfileMin, "nogc")
//  }
//
//  def save(dotfile: RenderedDot, kind: String): Unit = {
//    val name = s"plan-${System.currentTimeMillis()}-$kind.gv"
//    val last = Paths.get(s"target", s"plan-last-$kind.gv")
//
//    Paths.get("target").toFile.mkdirs().discard()
//
//    val path = Paths.get(s"target", name)
//    Files.write(path, dotfile.raw.getBytes(StandardCharsets.UTF_8)).discard()
//    Files.deleteIfExists(last).discard()
//    Files.createLink(last, path).discard()
//  }
//
//  def render(finalPlan: OrderedPlan, withGc: Boolean): RenderedDot = {
//    val g = new Digraph(graphAttr = mutable.Map("rankdir" -> "TB"))
//
//    val legend = new Digraph("cluster_legend", graphAttr = mutable.Map("label" -> "Legend", "style" -> "dotted"))
//    legend.node("normal", "Regular", mutable.Map("style" -> "filled", "shape" -> "box", "fillcolor" -> "darkolivegreen3"))
//    legend.node("weak", "Weak", mutable.Map("style" -> "dashed", "shape" -> "box"))
//    legend.node("collected", "Removed by GC", mutable.Map("style" -> "filled", "shape" -> "box", "fillcolor" -> "coral1"))
//    legend.node("root", "GC Root", mutable.Map("style" -> "filled", "shape" -> "box", "fillcolor" -> "gold", "peripheries" -> "2"))
//
//    Seq("normal", "weak", "root", "collected").sliding(2).foreach {
//      p =>
//        legend.edge(p.head, p.last, attrs = mutable.Map("style" -> "invis"))
//    }
//
//    val main = new Digraph("cluster_main", graphAttr = mutable.Map("label" -> "Context", "shape" -> "box"))
//    val collected = new Digraph("cluster_collected", graphAttr = mutable.Map("label" -> "Collected", "style" -> "dotted"))
//
//    val preGcPlan = beforeFinalization.get()
//    val preTopology = planAnalyzer.topology(preGcPlan.steps)
//
//    val originalKeys = preTopology.dependencies.graph.keys
//    val goodKeys = finalPlan.keys
//
//    val missingKeys = originalKeys.toSet.diff(goodKeys)
//    val missingKeysSeq = missingKeys.toSeq
//
//    val km = new KeyMinimizer(goodKeys ++ originalKeys)
//    val roots = finalPlan.declaredRoots
//
//    goodKeys.foreach {
//      k =>
//
//        val rootStyle = if (roots.contains(k)) {
//          Map("fillcolor" -> "gold", "peripheries" -> "2")
//        } else {
//          Map("fillcolor" -> "darkolivegreen3")
//        }
//        val attrs = mutable.Map("style" -> "filled", "shape" -> "box") ++ rootStyle
//
//        val op = finalPlan.toSemi.index(k)
//        val name = km.renderKey(k)
//        modify(name, attrs, op)
//        main.node(name, attrs = attrs)
//    }
//
//    finalPlan.topology.dependencies.graph.foreach {
//      case (k, deps) =>
//        deps.foreach {
//          d =>
//            main.edge(km.renderKey(k), km.renderKey(d))
//        }
//    }
//
//    if (withGc) {
//      missingKeysSeq.foreach {
//        k =>
//          val attrs = mutable.Map("style" -> "filled", "shape" -> "box", "fillcolor" -> "coral1")
//          val op = preGcPlan.index(k)
//          val name = km.renderKey(k)
//          modify(name, attrs, op)
//          collected.node(name, attrs = attrs)
//      }
//
//      preTopology.dependencies.graph.foreach {
//        case (k, deps) =>
//          deps.foreach {
//            d =>
//              if ((missingKeys.contains(k) && !missingKeys.contains(d)) || (missingKeys.contains(d) && !missingKeys.contains(k))) {
//                collected.edge(km.renderKey(k), km.renderKey(d), attrs = mutable.Map("color" -> "coral1"))
//              } else if (missingKeys.contains(d) && missingKeys.contains(k)) {
//                collected.edge(km.renderKey(k), km.renderKey(d))
//              }
//          }
//      }
//    }
//
//    g.subGraph(main)
//    if (withGc) {
//      g.subGraph(collected)
//    }
//    g.subGraph(legend)
//    val res = g.source()
//    new RenderedDot(res)
//  }
//
//  private[this] def modify(name: String, attrs: mutable.Map[String, String], op: ExecutableOp): Unit = {
//    val label = op match {
//      case op: ExecutableOp.InstantiationOp =>
//        op match {
//          case ExecutableOp.CreateSet(_, _, _, _) =>
//            "newset"
//          case op: ExecutableOp.WiringOp =>
//            op.wiring match {
//              case RuntimeDIUniverse.Wiring.SingletonWiring.Function(_, _) =>
//                "lambda"
//              case RuntimeDIUniverse.Wiring.SingletonWiring.Instance(_, _) =>
//                "instance"
//              case RuntimeDIUniverse.Wiring.SingletonWiring.Reference(_, _, weak) =>
//                if (weak) {
//                  attrs.put("style", "filled,dashed")
//                }
//                "ref"
//            }
//        case op: ExecutableOp.MonadicOp =>
//          op match {
//            case _: MonadicOp.ExecuteEffect =>
//              "effect"
//            case _: MonadicOp.AllocateResource =>
//              "resource"
//          }
//        }
//
//      case ExecutableOp.ImportDependency(_, _, _) =>
//        "import"
//
//      case op: ExecutableOp.ProxyOp =>
//        op match {
//          case ProxyOp.MakeProxy(_, _, _, byNameAllowed) =>
//            if (byNameAllowed) {
//              "byname"
//            } else {
//              "proxy"
//            }
//
//          case ProxyOp.InitProxy(_, _, _, _) =>
//            "init"
//
//        }
//
//
//    }
//    attrs.put("label", s"$name:=$label").discard()
//  }
//
//}
//
//object GraphDumpObserver {
//  final class RenderedDot(val raw: String) extends AnyVal
//}
//
