Config Injection
================

`distage-config` library parses `typesafe-config` into arbitrary case classes or sealed traits and makes them available
for summoning as a class dependency.

To use it, add `distage-config` library:

```scala
libraryDependencies += Izumi.R.distage_config
```

or

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-config" % "$izumi.version$"
```

@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Write a config in HOCON format:

```hocon
# resources/application.conf
program {
    config {
        different = true
    }
}
```

Add `ConfigModule` into your injector:

```scala
import distage.config._
import com.typesafe.config.ConfigFactory

val config = ConfigFactory.load()

val injector = Injector(new ConfigModuleDef(AppConfig(config)))
```

Now you can automatically parse config entries into case classes and summon them from any class:

```scala
final case class Config(different: Boolean)

class ConfiguredTaglessProgram[F[_]](
  config: Config @ConfPath("program.config"),
  primaryProgram: TaglessProgram[F] @Id("primary"),
  differentProgram: TaglessProgram[F] @Id("different") ) {

    val program = if (config.different) differentProgram else primaryProgram
}

class ConfiguredTryProgram[F[_]: TagK: Monad] extends ModuleDef {
  make[ConfiguredProgram[F]]
  make[TaglessProgram[F]].named("primary")
  make[TaglessProgram[F]].named("different")
}
```
