package izumi.distage.model.definition

trait ModuleMake[T <: ModuleBase] extends ModuleMake.Aux[T, T]

object ModuleMake {
  def apply[T <: ModuleBase: ModuleMake]: ModuleMake[T] = implicitly

  sealed trait Aux[-S, +T <: ModuleBase] {
    def make(bindings: Set[Binding]): T

    final def empty: T = make(Set.empty)

    private[Aux] type DivergenceRedirect
  }
  object Aux {
    // emulate bivariance, the only purpose of the first parameter is to initiate the search in its companion object
    // otherwise the parameter will be ignored when deciding compatibility of instances
    // Note: searching for a type with refinement `{ type DivergenceRedirect }` causes Scalac to detect divergence
    //       ONLY for the refined type, and keep looking for the unrefined version, basically this works around spurious
    //       divergence errors.
    @inline implicit def makeSelf[T <: ModuleBase](implicit T: ModuleMake.Aux[Nothing, T] {type DivergenceRedirect}): ModuleMake.Aux[T, T] =
      T.asInstanceOf[ModuleMake.Aux[T, T]]
  }
}
