package izumi.distage.model.plan

import izumi.distage.model.reflection.{DIKey, LinkedParameter, Provider, SafeType}

sealed trait Wiring {
  def instanceType: SafeType
  def associations: Seq[LinkedParameter]
  def replaceKeys(f: DIKey => DIKey): Wiring
  def requiredKeys: Set[DIKey]
}

object Wiring {
  sealed trait SingletonWiring extends Wiring {
    def instanceType: SafeType
    override def replaceKeys(f: DIKey => DIKey): SingletonWiring
  }
  object SingletonWiring {
    final case class Function(provider: Provider) extends SingletonWiring {
      override def instanceType: SafeType = provider.ret
      override def requiredKeys: Set[DIKey] = associations.map(_.key).toSet
      override def associations: Seq[LinkedParameter] = provider.parameters

      override def replaceKeys(f: DIKey => DIKey): Function = {
        this.copy(provider = this.provider.replaceKeys(f))
      }
    }

    final case class Reference(instanceType: SafeType, key: DIKey, weak: Boolean) extends SingletonWiring {
      override def associations: Seq[LinkedParameter] = Seq.empty
      override val requiredKeys: Set[DIKey] = Set(key)
      override def replaceKeys(f: DIKey => DIKey): Reference = {
        this.copy(key = f(this.key))
      }
    }

    final case class Instance(instanceType: SafeType, instance: Any) extends SingletonWiring {
      override def associations: Seq[LinkedParameter] = Seq.empty
      override def requiredKeys: Set[DIKey] = Set.empty
      override def replaceKeys(f: DIKey => DIKey): Instance = this
    }
  }

  sealed trait MonadicWiring extends Wiring {
    def effectWiring: SingletonWiring
    def effectHKTypeCtor: SafeType
  }

  object MonadicWiring {
    final case class Effect(instanceType: SafeType, effectHKTypeCtor: SafeType, effectWiring: SingletonWiring) extends MonadicWiring {
      override def associations: Seq[LinkedParameter] = effectWiring.associations
      override def requiredKeys: Set[DIKey] = effectWiring.requiredKeys

      override def replaceKeys(f: DIKey => DIKey): Effect = copy(effectWiring = effectWiring.replaceKeys(f))
    }

    final case class Resource(instanceType: SafeType, effectHKTypeCtor: SafeType, effectWiring: SingletonWiring) extends MonadicWiring {
      override def associations: Seq[LinkedParameter] = effectWiring.associations
      override def requiredKeys: Set[DIKey] = effectWiring.requiredKeys

      override def replaceKeys(f: DIKey => DIKey): Resource = copy(effectWiring = effectWiring.replaceKeys(f))
    }
  }

}
