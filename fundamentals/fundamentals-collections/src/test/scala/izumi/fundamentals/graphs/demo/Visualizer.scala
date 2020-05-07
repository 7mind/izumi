//package izumi.fundamentals.graphs.demo
//
//import java.util.concurrent.CountDownLatch
//
//import izumi.fundamentals.graphs.GraphFixtures._
//import izumi.fundamentals.graphs.GraphTraversalError.UnrecoverableLoops
//import izumi.fundamentals.graphs.struct.IncidenceMatrix
//import izumi.fundamentals.graphs.tools.GC.GCInput
//import izumi.fundamentals.graphs.tools.LoopDetector.Impl
//import izumi.fundamentals.graphs.tools.random.RandomGraph
//import izumi.fundamentals.graphs.tools.{CycleEraser, GC, LoopBreaker}
//import izumi.fundamentals.graphs.ui.GVR
//import izumi.fundamentals.graphs.ui.LocationPolicy.Singleton
//import izumi.fundamentals.graphs.{DG, GraphError, GraphMeta}
//
//object Visualizer {
//  private val latch = new CountDownLatch(1)
//  private val onExit: () => Unit = () => latch.countDown()
//  private val lp = Singleton
//
//  def main(args: Array[String]): Unit = {
//    val emptyMeta = GraphMeta(Map.empty[Int, String])
//
//    val maybeCyclic = RandomGraph.makeDG[Int](10, 5)
//
//    val withoutLoops = new CycleEraser[Int](maybeCyclic, new LoopBreaker[Int] {
//      override def breakLoops(withLoops: IncidenceMatrix[Int]): Either[UnrecoverableLoops[Int], IncidenceMatrix[Int]] = {
//        val allLoops = Impl.findCyclesForNodes(withLoops.links.keySet, withLoops)
//        Right(withLoops.without(allLoops.flatMap(loop => loop.loops.map(_.loop.head))))
//      }
//    }).run()
//
//    val samples: Seq[(Either[GraphError[Int], IncidenceMatrix[Int]], String)] = Seq(
//      Right(collectableCyclic) -> "original",
//      new GC.GCTracer[Int].collect(GCInput(collectableCyclic.transposed, Set(6), Set.empty)).map(_.predcessorMatrix.transposed) -> "collected",
//      Right(RandomGraph.makeDAG[Int](10, 5)) -> "random dag",
//      Right(maybeCyclic) -> "random dg",
//      withoutLoops -> "no loops",
//    )
//
//    for ((g, t) <- samples) {
//      g match {
//        case Left(value) =>
//          System.err.println(value)
//        case Right(value) =>
//          GVR.render(DG.fromSucc(value, emptyMeta), lp, onExit, Some(t))
//      }
//    }
//
//    latch.await()
//    System.exit(0)
//  }
//}
//
