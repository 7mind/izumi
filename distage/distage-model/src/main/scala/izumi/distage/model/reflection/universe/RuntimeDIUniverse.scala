package izumi.distage.model.reflection.universe

import izumi.distage.model.definition.DIStageAnnotation
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.reflection.Tags

trait RuntimeDIUniverse extends DIUniverse {
  override final val u: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe
  override val tags: Tags.type

  class IdContractImpl[T] extends IdContract[T] {
    override def repr(value: T): String = value.toString
  }

  override implicit val stringIdContract: IdContract[String] = new IdContractImpl[String]
  override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]

  override protected val typeOfDistageAnnotation: TypeNative = u.typeOf[DIStageAnnotation]

  val identityEffectType: SafeType = SafeType.getK[Identity]
}

object RuntimeDIUniverse extends RuntimeDIUniverse {
  // must be moved to object and lazy because of a Scalac bug otherwise:
  // [error] /Users/kai/src/izumi-r2/distage/distage-model/src/main/scala/izumi/distage/model/definition/dsl/ModuleDefDSL.scala:541:15: Internal error: unable to find the outer accessor symbol of trait SetDSLBase
  // [error]     final def weakEffect[F[_]: TagK, I <: T : Tag](implicit pos: CodePositionMaterializer): AfterAdd =
  // [error]               ^
  // [error] /Users/kai/src/izumi-r2/distage/distage-model/src/main/scala/izumi/distage/model/definition/dsl/ModuleDefDSL.scala:542:17: Implementation restriction: access of value tags in class R from trait SetDSLBase in object ModuleDefDSL, would require illegal premature access to the unconstructed `this` of class R in package universe
  // [error]       weakEffect[F, I, F[I]]
  override final lazy val tags: Tags.type = Tags
}
