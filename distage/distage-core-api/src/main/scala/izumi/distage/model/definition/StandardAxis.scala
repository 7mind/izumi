package izumi.distage.model.definition

object StandardAxis {

  /** Describes a generic choice between production and test implementations of a component */
  object Mode extends Axis {
    override def name: String = "mode"

    case object Prod extends AxisChoiceDef
    case object Test extends AxisChoiceDef
  }

  /**
    * Any entities which may store and persist state or "repositories".
    * e.g. databases, message queues, KV storages, file systems, etc.
    *
    * Those may typically have both in-memory `Dummy` implementations
    * and heavyweight `Prod` implementations using external databases.
    */
  object Repo extends Axis {
    override def name: String = "repo"

    case object Prod extends AxisChoiceDef
    case object Dummy extends AxisChoiceDef
  }

  /**
    * Third-party integrations which are not controlled by the application and provided "as is".
    * e.g. Facebook API, Google API, etc.
    *
    * Those may contact a `Real` external integration or a `Mock` one with predefined responses.
    */
  object World extends Axis {
    override def name: String = "world"

    case object Real extends AxisChoiceDef
    case object Mock extends AxisChoiceDef
  }

  /**
    * Describes whether external services required by the application
    * should be set-up on the fly by an orchestrator library such as `distage-framework-docker` (`Scene.Managed`),
    * or whether the application should try to connect to external services
    * as if they already exist in the environment (`Scene.Provided`).
    *
    * We call a set of external services required by the application a `Scene`,
    * etymology being that the running external services required by the application
    * are like a "scene" that the "staff" (the orchestrator) must prepare
    * for the "actor" (the application) to enter.
    */
  object Scene extends Axis {
    override def name: String = "scene"

    case object Managed extends AxisChoiceDef
    case object Provided extends AxisChoiceDef
  }

  def prodActivation: Activation = {
    Activation(
      Mode -> Mode.Prod,
      Repo -> Repo.Prod,
      World -> World.Real,
      Scene -> Scene.Provided,
    )
  }

  def testProdActivation: Activation = {
    Activation(
      Mode -> Mode.Test,
      Repo -> Repo.Prod,
      World -> World.Real,
      Scene -> Scene.Managed,
    )
  }

  def testDummyActivation: Activation = {
    Activation(
      Mode -> Mode.Test,
      Repo -> Repo.Dummy,
      World -> World.Mock,
      Scene -> Scene.Managed,
    )
  }

  @deprecated("Use `distage.StandardAxis.Mode` instead", "1.0")
  lazy val Env: Mode.type = Mode

  @deprecated("Use `distage.StandardAxis.World` instead", "1.0")
  lazy val ExternalApi: World.type = World

}
