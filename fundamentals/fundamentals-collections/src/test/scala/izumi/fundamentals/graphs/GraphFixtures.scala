package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.MutationResolver.{Annotated, AxisPoint, MutSel, Node, SemiEdgeSeq, WithContext}

object GraphFixtures {
  final val directed = IncidenceMatrix(
    1 -> Set(2, 3),
    2 -> Set(1, 4),
    5 -> Set(6, 3),
    6 -> Set(5, 1),
    7 -> Set.empty[Int]
  )

  final val cyclic = IncidenceMatrix(
    1 -> Set(2),
    2 -> Set(1),
  )

  final val acyclic = IncidenceMatrix(
    1 -> Set.empty[Int],
    2 -> Set(1)
  )

  final val dag = IncidenceMatrix(
    1 -> Set(2, 3),
    2 -> Set(4),
    3 -> Set(4, 5),
  )

  final val collectableDag = IncidenceMatrix(
    1 -> Set(2, 3),
    2 -> Set(4),
    4 -> Set(5, 6),
  )

  final val collectedDag = IncidenceMatrix(
    1 -> Set(2),
    2 -> Set(4),
    4 -> Set(6),
    6 -> Set.empty[Int],
  )

  final val collectableCyclic = IncidenceMatrix(
    1 -> Set(2, 3),
    2 -> Set(4, 1),
    4 -> Set(5, 6),
    6 -> Set(6, 1)
  )

  final val collectedCyclic = IncidenceMatrix(
    1 -> Set(2),
    2 -> Set(1, 4),
    4 -> Set(6),
    6 -> Set(1, 6),
  )

  final val collectableLinear = IncidenceMatrix(
    1 -> Set(2),
    2 -> Set(3),
    3 -> Set(4),
    4 -> Set(5),
    5 -> Set(6),
  )

  final val mutators: SemiEdgeSeq[Annotated[String], String, Int] = SemiEdgeSeq(Seq(
    Annotated("app", None, Set.empty) -> Node(Set("s1"), 0),

    Annotated("s1", None, Set(AxisPoint("test", "dev"), AxisPoint("repo", "pg"))) -> Node(Set("shared"), 0),
    Annotated("s1", None, Set(AxisPoint("test", "prod"), AxisPoint("repo", "dynamo"))) -> Node(Set("shared"), 0),
    Annotated("s1", Some(3), Set.empty) -> Node(Set("s1", "s2"), 0),
    Annotated("s1", Some(4), Set(AxisPoint("test", "prod"), AxisPoint("repo", "dynamo"), AxisPoint("fruit", "apple"))) -> Node(Set("s1", "s2"), 0),
    Annotated("s1", Some(5), Set.empty) -> Node(Set("s1", "s2", "d1"), 0),
    Annotated("s1", Some(6), Set(AxisPoint("test", "bad"))) -> Node(Set("s1", "s2"), 0),

    Annotated("d1", None, Set.empty) -> Node(Set("s4"), 1),
    Annotated("s2", None, Set.empty) -> Node(Set("s3"), 1),
    Annotated("s3", None, Set.empty) -> Node(Set("s4"), 1),
  )
  )

  final val withLoop: SemiEdgeSeq[Annotated[String], String, Int] = SemiEdgeSeq(
    mutators.links ++ Seq(
      Annotated("s4", None, Set.empty) -> Node(Set("s1"), 0),
    )
  )

  final val complexMutators: SemiEdgeSeq[Annotated[String], String, Int] = SemiEdgeSeq(Seq(
    Annotated("a", None, Set.empty) -> Node(Set.empty[String], 2),
    Annotated("b", None, Set.empty) -> Node(Set("a"), 0),
    Annotated("a", Some(1), Set.empty) -> Node(Set("a"), 0),
    Annotated("a", Some(2), Set.empty) -> Node(Set("b"), 1),
    Annotated("a", Some(3), Set.empty) -> Node(Set("a"), 1),
  )
  )

  implicit object NShow extends NodeShow[WithContext[Int, String]] {
    override def show(t: WithContext[Int, String]): String = {
      s"${t.meta} [[${t.remaps}]]"
    }
  }

  implicit object AShow extends NodeShow[MutSel[String]] {
    override def show(t: MutSel[String]): String = {
//      val base = if (t.con.isEmpty) {
//        t.key
//      } else {
//        s"${t.key}${t.con.mkString("{", ",", "}")}"
//      }
      val base = t.key
      t.mut match {
        case Some(value) =>
          s"$base:${value}"
        case None =>
          base
      }

    }
  }
}



