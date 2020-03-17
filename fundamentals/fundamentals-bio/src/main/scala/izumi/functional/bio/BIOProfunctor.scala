package izumi.functional.bio

trait BIOProfunctor[FR[-_, +_, +_]] extends BIOLocal[FR] {
  @inline final def dimap[R1, R2, A1, A2, E](fab: FR[R1, E, A1])(f: R2 => R1)(g: A1 => A2): FR[R2, E, A2] = InnerF.map(contramap(fab)(f))(g)
}