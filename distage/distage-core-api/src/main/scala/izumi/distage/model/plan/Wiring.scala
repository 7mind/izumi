package izumi.distage.model.plan

import izumi.distage.model.reflection.{Association, DIKey, Provider, SafeType}
import izumi.fundamentals.platform.language.unused

sealed trait Wiring {
  def instanceType: SafeType
  def associations: Seq[Association]
  def replaceKeys(f: Association => DIKey.BasicKey): Wiring

  def requiredKeys: Set[DIKey] = associations.map(_.key).toSet
}

object Wiring {
  sealed trait SingletonWiring extends Wiring {
    def instanceType: SafeType
    override def replaceKeys(f: Association => DIKey.BasicKey): SingletonWiring
  }
  object SingletonWiring {
    final case class Function(provider: Provider, associations: Seq[Association.Parameter]) extends SingletonWiring {
      override final def instanceType: SafeType = provider.ret

      override final def replaceKeys(f: Association => DIKey.BasicKey): Function = this.copy(associations = this.associations.map(a => a.withKey(f(a))))
    }
    final case class Instance(instanceType: SafeType, instance: Any) extends SingletonWiring {
      override final def associations: Seq[Association] = Seq.empty

      override final def replaceKeys(@unused f: Association => DIKey.BasicKey): Instance = this
    }
    final case class Reference(instanceType: SafeType, key: DIKey, weak: Boolean) extends SingletonWiring {
      override final def associations: Seq[Association] = Seq.empty

      override final val requiredKeys: Set[DIKey] = super.requiredKeys ++ Set(key)
      override final def replaceKeys(@unused f: Association => DIKey.BasicKey): Reference = this
    }
  }

  sealed trait MonadicWiring extends Wiring {
    def effectWiring: SingletonWiring
    def effectHKTypeCtor: SafeType
  }
  object MonadicWiring {
    final case class Effect(instanceType: SafeType, effectHKTypeCtor: SafeType, effectWiring: SingletonWiring) extends MonadicWiring {
      override final def associations: Seq[Association] = effectWiring.associations
      override final def requiredKeys: Set[DIKey] = effectWiring.requiredKeys

      override final def replaceKeys(f: Association => DIKey.BasicKey): Effect = copy(effectWiring = effectWiring.replaceKeys(f))
    }

    final case class Resource(instanceType: SafeType, effectHKTypeCtor: SafeType, effectWiring: SingletonWiring) extends MonadicWiring {
      override final def associations: Seq[Association] = effectWiring.associations
      override final def requiredKeys: Set[DIKey] = effectWiring.requiredKeys

      override final def replaceKeys(f: Association => DIKey.BasicKey): Resource = copy(effectWiring = effectWiring.replaceKeys(f))
    }
  }

}
