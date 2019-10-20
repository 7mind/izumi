package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

import scala.language.implicitConversions

sealed trait BindingTag

object BindingTag {
  implicit def apply(tag: AxisValue): BindingTag = AxisTag(tag)

  case class AxisTag(choice: AxisValue) extends BindingTag
}

sealed trait AxisBase {
  def name: String

  override final def toString: String = s"$name"
  implicit final def self: AxisBase = this
}

trait Axis[+MM <: AxisValue] extends AxisBase

object Axis {
  trait AxisValue {
    def axis: AxisBase

    def id: String = {
      val n = getClass.getName.toLowerCase
      n.split('$').last
    }

    override def toString: String = s"$axis:$id"
  }
}

object StandardAxis {

  abstract class Env(implicit val axis: AxisBase) extends AxisValue

  object Env extends Axis[Env] {
    override def name: String = "env"

    case object Prod extends Env
    case object Test extends Env
  }

  abstract class Repo(implicit val axis: AxisBase) extends AxisValue

  object Repo extends Axis[Repo] {
    override def name: String = "repo"

    case object Prod extends Repo
    case object Dummy extends Repo
  }

  abstract class ExternalApi(implicit val axis: AxisBase) extends AxisValue

  object ExternalApi extends Axis[ExternalApi] {
    override def name: String = "api"

    case object Prod extends ExternalApi
    case object Mock extends ExternalApi
  }

  def prodActivation: Map[AxisBase, AxisValue] = {
    Map(
      Env -> Env.Prod,
      Repo -> Repo.Prod,
      ExternalApi -> ExternalApi.Prod,
    )
  }

  def testProdActivation: Map[AxisBase, AxisValue] = {
    Map(
      Env -> Env.Test,
      Repo -> Repo.Prod,
      ExternalApi -> ExternalApi.Prod,
    )
  }

  def testDummyActivation: Map[AxisBase, AxisValue] = {
    Map(
      Env -> Env.Test,
      Repo -> Repo.Dummy,
      ExternalApi -> ExternalApi.Mock,
    )
  }

}
