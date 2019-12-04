distage-config
================

`distage-config` library allows summoning case classes and sealed traits from `typesafe-config` configuration

To use it, add `distage-config` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-config" % "$izumi.version$"
```

@@@

Add a configuration file in HOCON format:

```hocon
# resources/application.conf
conf {
    name = "John"
    age = 33
    other = true
}
```

Parse it into case classes and summon into your object graph:

```scala mdoc:reset-object:to-string
import distage.{DIKey, GCMode, ModuleDef, Id, Injector}
import distage.config.{AppConfigModule, ConfigModuleDef}
import com.typesafe.config.ConfigFactory

final case class Conf(name: String, age: Int)

final case class OtherConf(other: Boolean)

final class ConfigPrinter(conf: Conf, otherConf: OtherConf @Id("other")) {
  def print() = {
    println(s"name: ${conf.name}, age: ${conf.age}, other: ${otherConf.other}")
  }
}

// load
val config = ConfigFactory.load()

// declare paths to parse
val configModule = new ConfigModuleDef {
  makeConfig[Conf]("conf")
  makeConfig[OtherConf]("conf").named("other")
}

val appModule = new ModuleDef {
  make[ConfigPrinter]
}

// add config wiring to the graph
val appConfigModule = AppConfigModule(config)

val injector = Injector(appConfigModule)

val objects = injector.produceUnsafe(
  input = Seq(appModule, configModule).merge,
  mode  = GCMode(DIKey.get[ConfigPrinter])
)

objects.get[ConfigPrinter].print()
```

Automatic derivation of config codecs is based on [circe-config](https://github.com/circe/circe-config) & [circe-derivation](https://github.com/circe/circe-derivation). 
[Circe](https://github.com/circe/circe) codecs for a type will be reused if they exist.
