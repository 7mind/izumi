---
out: index.html
---
LogStage
========

LogStage is a zero-cost structural logging framework.

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

```scala mdoc:silent
import logstage._
import scala.util.Random

val logger = IzLogger.DebugLogger

val justAnArg = "example"
val justAList = List[Any](10, "green", "bottles")

logger.trace(s"Argument: $justAnArg, another arg: $justAList")
logger.info(s"Named expression: ${Random.nextInt() -> "random number"}")
logger.warn(s"Invisible argument: ${Random.nextInt() -> "random number" -> null}")

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

Dependencies
------------

```scala
// LogStage API, you need it to use the logger
libraryDependencies += Izumi.R.logstage_core

// LogStage machinery
libraryDependencies ++= Seq(
  // json output
    Izumi.R.logstage_rendering_circe
  // router from Slf4j to LogStage
  , Izumi.R.logstage_adapter-slf4j
)
```

or

@@@vars
```scala
val izumi_version = "$izumi.version$"
// LogStage API, you need it to use the logger
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "logstage-core" % izumi_version

// LogStage machinery
libraryDependencies ++= Seq(
  // json output
    "com.github.pshirshov.izumi.r2" %% "logstage-rendering-circe" % izumi_version
  // router from Slf4j to LogStage
  , "com.github.pshirshov.izumi.r2" %% "logstage-adapter-slf4j" % izumi_version    
)
```
@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Basic setup
-----------

```scala mdoc:reset:silent
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

SLF4J Router
------------

By default `logstage_adapter-slf4j` will route into `stderr`, due to the global mutable nature of `slf4j` you'll have to
configure a global singleton to use the same `LogRouter` as your logger:

```scala mdoc:reset
import logstage._
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter

val myLogger = IzLogger()

// configure SLF4j to use the same router as `myLogger`
StaticLogRouter.instance.setup(myLogger.router)
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

`logstage-zio` module adds an alternative `LogBIO` that logs the current fiber ID in addition to usual logging of thread ID:

Example: 

```scala mdoc:invisible:reset
import logstage._

val logger = IzLogger()
```

```scala mdoc
import logstage.LogstageZIO
import scalaz.zio.{IO, RTS}

val log = LogstageZIO.withFiberId(logger)

val rts = new RTS {}
rts.unsafeRun {
  log.info(s"Hey! I'm logging with ${log}stage!")
}
```

```
I 2019-03-29T23:21:48.760Z[Europe/Dublin] r.S.App9.res10 ...main-12:5384  (00_logstage.md:123) {fiberId=0} Hey! I'm logging with log=logstage.LogstageZIO$$anon$1@c39104astage!
```

To add use:

```scala
libraryDependencies += Izumi.R.logstage_zio
```

or

@@@vars
```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "logstage-zio" % izumi_version
```
@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.


@@@ index

* [Rendering policy](policy.md)
* [Configuration](config.md)
* [Logging contexts](custom_ctx.md)

@@@
