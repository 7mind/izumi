---
out: index.html
---
LogStage
========

LogStage is a zero-cost structural logging framework for Scala & Scala.js

Key features:

1. LogStage extracts structure from ordinary string interpolations in your log messages with zero changes to code.
2. LogStage uses macros to extract log structure, its faster at runtime than a typical reflective structural logging frameworks,
3. Log contexts
4. Console, File and SLF4J sinks included, File sink supports log rotation,
5. Human-readable output and JSON output included,
6. Method-level logging granularity. Can configure methods `com.example.Service.start` and `com.example.Service.doSomething` independently,
7. Slf4J adapters: route legacy Slf4J logs into LogStage router

Overview
--------

The following snippet:

```scala mdoc:reset
import logstage._
import scala.util.Random

val logger = IzLogger()

val justAnArg = "example"
val justAList = List[Any](10, "green", "bottles")

logger.trace(s"Argument: $justAnArg, another arg: $justAList")

// custom name, not based on `val` name

logger.info(s"Named expression: ${Random.nextInt() -> "random number"}")

// print result without a name

logger.warn(s"Invisible argument: ${Random.nextInt() -> "random number" -> null}")

// add following fields to all messages printed by a new logger value

val ctxLogger = logger("userId" -> "user@google.com", "company" -> "acme")
val delta = Random.nextInt(1000)

ctxLogger.info(s"Processing time: $delta")
```

Will look like this in string form:

![logstage-sample-output-string](media/00-logstage-sample-output-string.png)

And like this in JSON:

![logstage-sample-output-string](media/00-logstage-sample-output-json.png)

Note:

1. JSON formatter is type aware!
2. Each JSON message contains `@class` field with holds a unique `event class` identifier.
   All events produced by the same source code line will share the same `event class`.
   
Syntax Reference
------------

1. Simple variable:
   ```scala
   logger.info(s"My message: $argument")
   ```

2. Chain:
   ```scala
   logger.info(s"My message: ${call.method} ${access.value}")
   ```

3. Named expression:
   ```scala
   logger.info(s"My message: ${Some.expression -> "argname"}")
   ```

4. Invisible named expression:
   ```scala
   logger.info(s"My message: ${Some.expression -> "argname" -> null}")
   ```

5) De-camelcased name:
   ```scala
   logger.info(${camelCaseName-> ' '})
   ```

Dependencies
------------

```scala
// LogStage core
libraryDependencies += Izumi.R.logstage_core

// Optional
libraryDependencies ++= Seq(
  // Json output
  Izumi.R.logstage_rendering_circe,
  // Router from Slf4j to LogStage
  Izumi.R.logstage_adapter-slf4j,
  // Configure LogStage with Typesafe Config
  Izumi.R.logstage_config,
  // LogStage integration with DIStage 
  Izumi.R.logstage_di,
  // Router from LogStage to Slf4J
  Izumi.R.logstage_sink_slf4j,
)
```

or

@@@vars
```scala
val izumi_version = "$izumi.version$"
// LogStage core
libraryDependencies += "io.7mind.izumi" %% "logstage-core" % izumi_version

// Optional
libraryDependencies ++= Seq(
  // Json output
  "io.7mind.izumi" %% "logstage-rendering-circe" % izumi_version,
  // Router from Slf4j to LogStage
  "io.7mind.izumi" %% "logstage-adapter-slf4j" % izumi_version,    
  // Configure LogStage with Typesafe Config
  "io.7mind.izumi" %% "logstage-config" % izumi_version,
  // LogStage integration with DIStage 
  "io.7mind.izumi" %% "logstage-di" % izumi_version,
  // Router from LogStage to Slf4J
  "io.7mind.izumi" %% "logstage-sink-slf4j " % izumi_version,
)
```
@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Basic setup
-----------

```scala mdoc:reset
import logstage._
import logstage.circe._

val jsonSink = ConsoleSink.json(prettyPrint = true)
val textSink = ConsoleSink.text(colored = true)

val sinks = List(jsonSink, textSink)

val logger: IzLogger = IzLogger(Trace, sinks)
val contextLogger: IzLogger = logger(Map("key" -> "value"))

logger.info("Hey")

contextLogger.info(s"Hey")
```

Log algebras
------------

`LogIO` and `LogBIO` algebras provide a purely-functional API for one- and two-parameter effect types respectively:

```scala mdoc:reset
import logstage._
import cats.effect.IO

val logger = IzLogger()

val log = LogIO.fromLogger[IO](logger)

log.info(s"Hey! I'm logging with ${log}stage!").unsafeRunSync()
```

```
I 2019-03-29T23:21:48.693Z[Europe/Dublin] r.S.App7.res8 ...main-12:5384  (00_logstage.md:92) Hey! I'm logging with log=logstage.LogIO$$anon$1@72736f25stage!
```

`LogstageZIO.withFiberId` provides a `LogBIO` instance that logs the current ZIO `FiberId` in addition to the thread id:

Example: 

```scala mdoc:reset
import logstage.{IzLogger, LogstageZIO}
import zio.{IO, DefaultRuntime}

val log = LogstageZIO.withFiberId(IzLogger())

val rts = new DefaultRuntime {}
rts.unsafeRun {
  log.info(s"Hey! I'm logging with ${log}stage!")
}
```

```
I 2019-03-29T23:21:48.760Z[Europe/Dublin] r.S.App9.res10 ...main-12:5384  (00_logstage.md:123) {fiberId=0} Hey! I'm logging with log=logstage.LogstageZIO$$anon$1@c39104astage!
```

`LogIO`/`LogBIO` algebras can be extended with custom context using their `.apply` method, same as `IzLogger`:

```scala mdoc:reset
import com.example.Entity

def load(entity: Entity): cats.effect.IO[Unit] = cats.effect.IO.unit
```

```scala mdoc
import cats.effect.IO
import cats.implicits._
import logstage._
import io.circe.Printer
import io.circe.syntax._

def importEntity(entity: Entity)(implicit log: LogIO[IO]): IO[Unit] = {
  val ctxLog = log("ID" -> entity.id, "entityAsJSON" -> entity.asJson.printWith(Printer.spaces2))

  load(entity).handleErrorWith {
    case error =>
      ctxLog.error(s"Failed to import entity: $error.").void
      // JSON message includes `ID` and `entityAsJSON` fields
  }
}
```

SLF4J Router
------------

When not configured, `logstage-adapter-slf4j` will log messages with level `>= Info` to `stdout`.

Due to the global mutable nature of `slf4j`, to configure slf4j logging you'll
have to mutate a global singleton `StaticLogRouter`. Replace its `LogRouter`
with the same one you use elsewhere in your application to use the same configuration for Slf4j. 

```scala mdoc:reset
import logstage._
import izumi.logstage.api.routing.StaticLogRouter

val myLogger = IzLogger()

// configure SLF4j to use the same router that `myLogger` uses
StaticLogRouter.instance.setup(myLogger.router)
```

@@@ index

@@@
