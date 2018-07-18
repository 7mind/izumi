---
out: index.html
---
LogStage
========

LogStage is a free structural logging framework.

Key features:

1. LogStage extracts structure out of your log messages. In compile time. For free.
   So, you may write usual logging expressions but they may be automatically converted into meaningful JSON,
2. LogStage uses macro code generation to extract logging context so it does not depend on reflection
    and is cheaper than a typical framework in runtime,
3. Log contexts
4. Console and File sinks included, File sink supports log rotation,
5. Human-readable and JSON formatting included,
6. Method-level logging granularity. You may configure `com.mycompany.Service.start` and `com.mycompany.Service.doSomething` independently,
7. Slf4J adapters: you may stream all the logging made with Slf4J into LogStage router (as well you may route LogStage messages into slf4j but in a typical case you won't need it).


The essence
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

Looks like so with String formatting policy applied:

![logstage-sample-output-string](media/00-logstage-sample-output-string.png)

And like so as JSON:

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
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "logstage-api-logger" % izumi_version

// LogStage machinery
libraryDependencies ++= Seq(
    // console sink
    "com.github.pshirshov.izumi.r2" %% "logstage-sink-console" % izumi_version
    // file sink
    , "com.github.pshirshov.izumi.r2" %% "logstage-sink-file" % izumi_version
    // json formatting
    , "com.github.pshirshov.izumi.r2" %% "logstage-json-json4s" % izumi_version
    // adapter for Slf4J loggers
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
