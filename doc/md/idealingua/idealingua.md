# Idealingua DML/IDL

[Code generation examples](cogen.md)

[Circe codec examples](cogen-circe.md)

## Keywords and aliases

Keyword     | Aliases                | Explanation                                 |
------------| ---------------------- | ------------------------------------------- |
`domain`    | `package`, `namespace` | Namespace containing collection of entities |
`import`    |                        | References a domain by id                   |
`include`   |                        | Includes `*.model` file by name             |
`alias`     | `type`, `using`        | Type alias                                  |
`enum`      |                        | Enumeration                                 |
`mixin`     | `interface`            | Mixin, named collection of fields           |
`data`      | `dto`, `struct`        | Data                                        |
`adt`       | `choice`               | Algebraic Data Type                         |
`id`        |                        | Identifier, named collection of scalars     |
`service`   |                        | Service interface                           |
`def`       | `fn`, `fun`            | Method                                      |

## Inheritance operators

Keyword     | Aliases                | Explanation                                          | Example                       |  
------------| ---------------------- | ---------------------------------------------------- | ------------------------------|
`+`         | `+++`, `...`           | Inherit structure (copy fields)                      | `+ Mixin`                     |
`&`         | `&&&`                  | Inherit interface                                    | `& Mixin`                     | 
`-`         | `---`                  | Drop structure (doesn't work for interfaces)         | `- Mixin`, `- field: str`     |

## Embedded data types

Notes


1. When it's impossible to represent a numeric type with target language we use minimal numeric type with bigger range
2. When it's not possible to represent time type or UUID with target language we use string representation
  

### Scalar types

Type name   | Aliases                | Explanation                                 | Scala mapping                |
------------| ---------------------- | ------------------------------------------- | -----------------------------|
`bool`      | `boolean`              | Boolean                                     | `Boolean`                    |
`str`       | `string`               | String                                      | `String`                     |
`i08`       | `byte`, `int8`         | 8-bit integer                               | `Byte`                       |
`i16`       | `short`, `int16`       | 16-bit integer                              | `Short`                      |
`i32`       | `int`, `int32`         | 32-bit integer                              | `Int`                        |
`i64`       | `long`, `int64`        | 64-bit integer                              | `Long`                       |
`flt`       | `float`                | Single precision floating point             | `Float`                      |
`dbl`       | `double`               | Double precision floating point             | `Double`                     |
`uid`       | `uuid`                 | UUID                                        | `java.util.UUID`             |
`tsz`       | `dtl`, `datetimel`     | Timestamp with timezone                     | `java.time.ZonedDateTime`    |
`tsl`       | `dtz`, `datetimez`     | Local timestamp                             | `java.time.LocalDateTime`    |
`time`      | `time`                 | Time                                        | `java.time.LocalTime`        |
`date`      | `date`                 | Date                                        | `java.time.LocalDate`        |

### Generics

Type name    | Explanation                                 | Scala mapping  | 
------------ | ------------------------------------------- | -------------- |
`list[T]`    | List                                        | `List`         |
`map[K, V]`  | Map (only scalar keys supported)            | `Map`          |
`opt[T]`     | Optional value                              | `scala.Option` |
`set[T]`     | Set (no guarantees for traversal ordering)  | `Set`          |


## Standalone compiler

The compiler is built as an uberjar and published onto central.

You may use [https://github.com/coursier/coursier](Coursier) to run it:

```bash
# release
coursier launch com.github.pshirshov.izumi.r2:idealingua-compiler_2.12:0.5.0 -- --help

# snapshot
coursier launch -r https://oss.sonatype.org/content/repositories/snapshots/ com.github.pshirshov.izumi.r2:idealingua-compiler_2.12:0.5.0-SNAPSHOT -- --help
```

Commandline examples:

```
coursier launch com.github.pshirshov.izumi.r2:idealingua-compiler_2.12:0.5.0 -- -s src -t target -L scala=* -L typescript=*
```

```
coursier launch com.github.pshirshov.izumi.r2:idealingua-compiler_2.12:0.5.0 -- -s src -t target -L scala=-AnyvalExtension -L typescript=*
```

## Http4s Transport

Most likely you would need to use [https://github.com/non/kind-projector](Kind Projector) compiler plugin and partial unification enabled:

```scala
scalacOptions += "-Ypartial-unification"
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.6" cross CrossVersion.binary)
``` 

You may find a test for the whole http4s pipeline [here](blob/develop/idealingua/idealingua-runtime-rpc-http4s/src/test/scala/com/github/pshirshov/izumi/idealingua/runtime/rpc/http4s/Http4sServer.scala).
Please note that service definitons for that test are implemented manually, you may find them [here](https://github.com/pshirshov/izumi-r2/tree/develop/idealingua/idealingua-test-defs/src/main/scala/com/github/pshirshov/izumi/r2/idealingua/test).
