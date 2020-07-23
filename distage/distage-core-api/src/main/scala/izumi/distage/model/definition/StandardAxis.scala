package izumi.distage.model.definition

object StandardAxis {

  object Env extends Axis {
    override def name: String = "env"

    case object Prod extends AxisValueDef
    case object Test extends AxisValueDef
  }

  object Repo extends Axis {
    override def name: String = "repo"

    case object Prod extends AxisValueDef
    case object Dummy extends AxisValueDef
  }

  object ExternalApi extends Axis {
    override def name: String = "api"

    case object Prod extends AxisValueDef
    case object Mock extends AxisValueDef
  }

  object Cycles extends Axis {
    override def name: String = "cycles"

    /** Enable cglib proxies, but try to resolve cycles using by-name parameters if they can be used */
    case object Proxy extends AxisValueDef

    /** Disable cglib proxies, allow only by-name parameters to resolve cycles */
    case object Byname extends AxisValueDef

    /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
    case object Disable extends AxisValueDef
  }

  def prodActivation: Activation = {
    Activation(
      Env -> Env.Prod,
      Repo -> Repo.Prod,
      ExternalApi -> ExternalApi.Prod,
      Cycles -> Cycles.Proxy,
    )
  }

  def testProdActivation: Activation = {
    Activation(
      Env -> Env.Test,
      Repo -> Repo.Prod,
      ExternalApi -> ExternalApi.Prod,
      Cycles -> Cycles.Proxy,
    )
  }

  def testDummyActivation: Activation = {
    Activation(
      Env -> Env.Test,
      Repo -> Repo.Dummy,
      ExternalApi -> ExternalApi.Mock,
      Cycles -> Cycles.Proxy,
    )
  }

}
