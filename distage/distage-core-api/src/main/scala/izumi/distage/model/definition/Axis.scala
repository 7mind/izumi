package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

trait Axis {
  def name: String = getClass.getName.toLowerCase.split('$').last

  override final def toString: String = s"$name"
  @inline implicit final def self: Axis = this

  abstract class AxisValueDef extends AxisValue {
    override val axis: Axis = self
  }
}

object Axis {
  trait AxisValue {
    def axis: Axis
    def id: String = getClass.getName.toLowerCase.split('$').last

    override final def toString: String = s"$axis:$id"
  }
}

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
