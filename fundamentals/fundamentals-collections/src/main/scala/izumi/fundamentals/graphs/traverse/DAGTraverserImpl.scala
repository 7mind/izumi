//package izumi.fundamentals.graphs.traverse
//
//import cats._
//import cats.implicits._
//import DAGTraverser.{Interrupted, Marking, Meta, NodeFailure, NonProgress, TraverseFailure, TraverseState}
//import izumi.fundamentals.graphs.struct.IncidenceMatrix
//import scala.collection.compat._
//
//
//class DAGTraverserImpl[F[_], Node, Trace, Progress]
//(
//  traverseStrategy: TraverseStrategy[F, Node, Trace, Progress],
//  interruptionStrategy: InterruptionStrategy[F],
//)
//(implicit
// F: Monad[F],
// P: Parallel[F],
//) extends AbstractDagTraverser[F, Node, Trace, Progress] {
//
//  final def traverse(predcessors: IncidenceMatrix[Node]): F[Either[TraverseFailure[F, Node, Trace, Progress], Marking[Node, Trace]]] = {
//    val initial = TraverseState[F, Node, Trace, Progress](predcessors, Marking(Map.empty, Meta(0)), Map.empty)
//    continueTraversal(initial)
//  }
//
//  final def continueTraversal(initial: TraverseState[F, Node, Trace, Progress]): F[Either[TraverseFailure[F, Node, Trace, Progress], Marking[Node, Trace]]] = {
//    F.tailRecM(initial) {
//      state =>
//        if (state.isFinished) {
//          F.pure(Right(Right(state.marking)))
//        } else if (traverseStrategy.canContinue(state)) {
//          F.flatMap(interruptionStrategy.interrupt()) {
//            interrupt =>
//              if (interrupt) {
//                F.pure(Right(Left(Interrupted(state))))
//              } else {
//                F.map(doStep(state)) {
//                  nextState =>
//                    if (nextState.isFinished || nextState.active.nonEmpty) {
//                      Left(nextState)
//                    } else {
//                      Right(Left(NonProgress(state.marking)))
//                    }
//                }
//              }
//          }
//        } else {
//          F.pure(Right(Left(NodeFailure(state.marking))))
//        }
//    }
//  }
//
//  final def doStep(state: TraverseState[F, Node, Trace, Progress]): F[TraverseState[F, Node, Trace, Progress]] = {
//    val status: List[F[(Node, MPromise.Status[Progress, Trace])]] = state.active.toList.map {
//      case (n, s) =>
//        F.map(s.status)(cs => (n, cs))
//    }
//
//    for {
//      merged <- Parallel.parTraverse(status)(identity)
//      markedNodes = merged
//        .collect {
//          case (n, MPromise.Mark(m)) =>
//            (n, m)
//        }.toMap
//    } yield {
//      // for an async implementation some nodes may become marked after availableMarks is collected
//      val unfinished: Map[Node, MPromise.Status[Progress, Trace]] = merged.toMap -- markedNodes.keySet
//      val nextMarking = Marking(state.marking.trace ++ markedNodes, Meta(state.marking.meta.generation + 1))
//
//      val nextUnmarked = findNext(state.predcessors, nextMarking, (node, mark) => !state.marking.contains(node) && traverseStrategy.isGreen(node, mark))
//      val tasks = nextUnmarked.map(n => n -> traverseStrategy.mark(n)).toMap
//
//      val stillActive = state.active.view.filterKeys(unfinished.contains).toMap
//      val nextActive = tasks ++ stillActive
//
//      assert(tasks.keySet.intersect(unfinished.keySet).isEmpty)
//      assert(state.marking.trace.keySet.intersect(markedNodes.keySet).isEmpty)
//
//
//      val next = TraverseState(state.predcessors, nextMarking, nextActive)
//      next
//    }
//  }
//
//
//  final def findNext(predcessors: IncidenceMatrix[Node], state: Marking[Node, Trace], goodMark: (Node, Trace) => Boolean): Set[Node] = {
//    val unmarkedPart = predcessors.links -- state.trace.keySet
//    unmarkedPart
//      .filter {
//        case (_, pp) =>
//          pp.isEmpty || pp.forall {
//            p =>
//              state.get(p) match {
//                case Some(value) =>
//                  goodMark(p, value)
//                case None =>
//                  false
//              }
//          }
//      }
//      .keySet
//  }
//}
//
