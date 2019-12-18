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

  def prodActivation: Activation = {
    Activation(
      Env -> Env.Prod,
      Repo -> Repo.Prod,
      ExternalApi -> ExternalApi.Prod,
    )
  }

  def testProdActivation: Activation = {
    Activation(
      Env -> Env.Test,
      Repo -> Repo.Prod,
      ExternalApi -> ExternalApi.Prod,
    )
  }

  def testDummyActivation: Activation = {
    Activation(
      Env -> Env.Test,
      Repo -> Repo.Dummy,
      ExternalApi -> ExternalApi.Mock,
    )
  }

}
