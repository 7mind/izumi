//package izumi.fundamentals.graphs
//
//import cats.effect.IO
//import izumi.fundamentals.graphs.traverse.DAGTraverser.TraverseState
//import izumi.fundamentals.graphs.traverse.{DAGTraverserImpl, InterruptionStrategy, MPromise, TraverseStrategy}
//import org.scalatest.wordspec.AnyWordSpec
//
//
//class TraverseTest extends AnyWordSpec {
//  import TraverseTest._
//
//  "Traverser" should {
//    "traverse DAGs" in {
//      assert(traverse.traverse(GraphFixtures.acyclic.transposed).unsafeRunSync().isRight)
//    }
//
//    "fail to traverse cyclic graphs" in {
//      assert(traverse.traverse(GraphFixtures.cyclic.transposed).unsafeRunSync().isLeft)
//    }
//  }
//}
//
//
//
//
//object TraverseTest {
//  final val traverseStrategy = new TraverseStrategy[IO, Int, NodeStatus.NodeDone, NodeStatus.NodeProgress]() {
//    override def mark(n: Int): MPromise[IO, NodeStatus.NodeProgress, NodeStatus.NodeDone] = {
//      new Suspended(MPromise.Mark(NodeStatus.Green()))
//    }
//
//    override def isGreen(node: Int, mark: NodeStatus.NodeDone): Boolean = mark match {
//      case NodeStatus.Green() =>
//        true
//      case _ =>
//        false
//    }
//
//    override def canContinue(state: TraverseState[IO, Int, NodeStatus.NodeDone, NodeStatus.NodeProgress]): Boolean = {
//      state.active.nonEmpty || state.predecessors.links.keySet.diff(state.marking.trace.keySet).nonEmpty
//    }
//  }
//
//  final val interruptionStrategy = new InterruptionStrategy[IO] {
//    override def interrupt(): IO[Boolean] = IO.pure(false)
//  }
//
//  implicit val ec = IO.contextShift(scala.concurrent.ExecutionContext.global)
//  final val traverse = new DAGTraverserImpl(traverseStrategy, interruptionStrategy)
//
//  class Suspended(value: => MPromise.Status[ NodeStatus.NodeProgress, NodeStatus.NodeDone]) extends MPromise[IO, NodeStatus.NodeProgress, NodeStatus.NodeDone] {
//    override def status: IO[MPromise.Status[NodeStatus.NodeProgress, NodeStatus.NodeDone]] = IO(value)
//  }
//
//  sealed trait NodeStatus
//
//  object NodeStatus {
//
//    sealed trait NodeProgress extends NodeStatus
//
//    case class Yellow() extends NodeDone
//
//    sealed trait NodeDone extends NodeStatus
//
//    case class Green() extends NodeDone
//
//    case class Red() extends NodeDone
//
//  }
//
//}
