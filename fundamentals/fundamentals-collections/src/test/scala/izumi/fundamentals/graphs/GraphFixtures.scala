package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.struct.IncidenceMatrix

object GraphFixtures {
  final val directed = IncidenceMatrix(
    1 -> Set(2, 3),
    2 -> Set(1, 4),
    5 -> Set(6, 3),
    6 -> Set(5, 1),
    7 -> Set.empty[Int],
  )

  final val cyclic = IncidenceMatrix(
    1 -> Set(2),
    2 -> Set(1),
  )

  final val acyclic = IncidenceMatrix(
    1 -> Set.empty[Int],
    2 -> Set(1),
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
    6 -> Set(6, 1),
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
}
