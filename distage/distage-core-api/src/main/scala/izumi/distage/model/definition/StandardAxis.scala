package izumi.distage.model.definition

object StandardAxis {

  object Mode extends Axis {
    override def name: String = "mode"

    case object Prod extends AxisValueDef
    case object Test extends AxisValueDef
  }

  @deprecated("Use StandardAxis.Mode instead", "0.11.0")
  final val Env = Mode

  /**
    * Any entities which may store and persist state or "repositories".
    * E.g. databases, message queues, KV storages, file systems, etc.
    */
  object Repo extends Axis {
    override def name: String = "repo"

    case object Prod extends AxisValueDef
    case object Dummy extends AxisValueDef
  }

  /**
    * Third-party integrations which are not controlled by the application and provided "as is".
    * E.g. Facebook API, Google API, etc.
    */
  object World extends Axis {
    override def name: String = "world"

    case object Real extends AxisValueDef
    case object Dummy extends AxisValueDef
  }

  @deprecated("Use StandardAxis.Externals instead", "0.11.0")
  final val ExternalApi = World

  /**
    * Choice axis controlling whether third-party services that the application requires
    * should be provided by `distage-framework-docker` or another orchestrator when the application starts (`Scene.Managed`),
    * or whether it should try to connect to these services as if they already exist in the environment (`Scene.Provided`)
    *
    * The set of third-party services required by the application is called a `Scene`, etymology being that the running
    * third-party services that the application depends on are like a scene that is prepared for for the actor (the application)
    * to enter.
    */
  object Scene extends Axis {
    override def name: String = "scene"

    case object Managed extends AxisValueDef
    case object Provided extends AxisValueDef
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
      World -> World.Dummy,
      Scene -> Scene.Managed,
    )
  }

}
