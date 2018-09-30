---
out: index.html
---
LogStage
========

LogStage is a zero-cost structural logging framework.

Key features:

1. LogStage extracts structure from ordinary string interpolations in your log messages with zero changes to code.
2. LogStage uses macros to extract log structure, its faster at runtime than typical reflective structural logging frameworks,
3. Log contexts
4. Console, File and SLF4J sinks included, File sink supports log rotation,
5. Human-readable output and JSON output included,
6. Method-level logging granularity. Can configure methods `com.example.Service.start` and `com.example.Service.doSomething` independently,
7. Slf4J adapters: route legacy Slf4J logs into LogStage router


Overview
-----------

The following snippet:

```scala
class ExampleService(logger: IzLogger) {
    val justAnArg = "example"
    val justAList = List[Any](10, "green", "bottles")

    logger.trace(s"Argument: $justAnArg, another arg: $justAList")
    logger.info(s"Named expression: ${Random.nextInt() -> "random number"}")
    logger.warn(s"Invisible argument: ${Random.nextInt() -> "random number" -> null}")

    val ctxLogger = logger("userId" -> "user@google.com", "company" -> "acme")
    val delta = Random.nextInt(1000)

    ctxLogger.info(s"Processing time: $delta")
}
```

Will look like this in string form:

![logstage-sample-output-string](media/00-logstage-sample-output-string.png)

And like this in JSON:

![logstage-sample-output-string](media/00-logstage-sample-output-json.png)

Note:

1. JSON formatter is type aware!
2. Each JSON message contains `@class` field which holds an unique identifier for *event class*.
   So, all the events produced by the same logger line would have the same class despite of argument values.

Dependencies
------------

@@@vars
```scala
val izumi_version = "$izumi.version$"
// LogStage API, you need it to use the logger
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "logstage" % izumi_version

// LogStage machinery
libraryDependencies ++= Seq(
    // file sink
    "com.github.pshirshov.izumi.r2" %% "logstage-sink-file" % izumi_version
    // json output
    , "com.github.pshirshov.izumi.r2" %% "logstage-rendering-circe" % izumi_version
    // router from Slf4j to LogStage
    , "com.github.pshirshov.izumi.r2" %% "logstage-adapter-slf4j" % izumi_version    
)

//

```
@@@


Basic setup
-----------

```scala
    import com.github.pshirshov.izumi.logstage.api._
    import com.github.pshirshov.izumi.logstage.api.Log._
    import com.github.pshirshov.izumi.logstage.api.config._
    import com.github.pshirshov.izumi.logstage.api.logger._
    import com.github.pshirshov.izumi.logstage.api.routing._
    import com.github.pshirshov.izumi.logstage.api.rendering._

    val jsonSink = new ConsoleSink(new JsonRenderingPolicy())
    val textSink = new ConsoleSink(new StringRenderingPolicy(RenderingOptions(withExceptions = true, withColors = true)))

    val sinks = List(jsonSink, textSink)

    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(Log.Level.Trace, sinks))
    val router = new ConfigurableLogRouter(configService)

    val logger = new IzLogger(router, CustomContext.empty)
    val contextLogger = logger(Map("key" -> "value"))
```


@@@ index

* [Rendering policy](policy.md)
* [Configuration](config.md)
* [Logging contexts](custom_ctx.md)

@@@
