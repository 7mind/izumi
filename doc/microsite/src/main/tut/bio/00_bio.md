---
out: index.html
---
BIO
===

BIO is a set of typeclasses and algebras

Overview
--------

The following snippet:

```scala mdoc:reset
import logstage.IzLogger
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


Basic setup
-----------

```scala mdoc:reset
import logstage.{ConsoleSink, IzLogger, Trace}
import logstage.circe.LogstageCirceRenderingPolicy

val textSink = ConsoleSink.text(colored = true)
val jsonSink = ConsoleSink(LogstageCirceRenderingPolicy(prettyPrint = true))

val sinks = List(jsonSink, textSink)

val logger: IzLogger = IzLogger(Trace, sinks)
val contextLogger: IzLogger = logger("key" -> "value")

logger.info("Hey")

contextLogger.info(s"Hey")
```

Log algebras
------------

`LogIO` and `LogBIO` algebras provide a purely-functional API for one- and two-parameter effect types respectively:

```scala mdoc:reset
import logstage.{IzLogger, LogIO}
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
import zio.IO

val log = LogstageZIO.withFiberId(IzLogger())

zio.Runtime.default.unsafeRun {
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
import logstage.LogIO
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
import logstage.IzLogger
import izumi.logstage.api.routing.StaticLogRouter

val myLogger = IzLogger()

// configure SLF4j to use the same router that `myLogger` uses
StaticLogRouter.instance.setup(myLogger.router)
```
