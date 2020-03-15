package izumi.distage.model.plan.initial

import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.reflection.DIKey

final case class NextOps(
                          sets: Map[DIKey, CreateSet],
                          provisions: Seq[InstantiationOp],
                        )
