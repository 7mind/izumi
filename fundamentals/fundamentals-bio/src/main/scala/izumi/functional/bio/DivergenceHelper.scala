package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.Nondivergent

trait DivergenceHelper {
  type Divergence = Nondivergent
}
object DivergenceHelper {
  type Divergent
  type Nondivergent
}
