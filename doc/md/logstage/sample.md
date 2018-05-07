# Examples

## Logstage config

## Rendering policy

Input rendering policy (defined in config) is:

`policy="${level}:${ts} ${thread}${location} ${custom-ctx} ${msg}"`

Let's send some logs

```scala
val loggerWithContext = logger("userId" -> "xxx")
val loggerWithSubcontext = loggerWithContext("custom" -> "value")

val arg = "this is an argument"

loggerWithContext.trace(s"This would be automatically extended")
logger.debug(s"Service started. argument: $arg, Random value: ${Random.self.nextInt()}")
loggerWithSubcontext.info("Just a string")
logger.warn("Just an integer: " + 1)
val arg1 = 5
logger.crit(s"This is an expression: ${2 + 2 == 4} and this is an other one: ${5 * arg1 == 25}")
val t = new RuntimeException("Oy vey!")
logger.crit(s"A failure happened: $t")
```


The final output will be:

`T:2018-05-07T14:31:43.045+03:00 ...leSinkTest:1 (LoggingMacroTest.scala:21) {userId=xxx} This would be automatically extended
D:2018-05-07T14:31:43.083+03:00 ...leSinkTest:1 (LoggingMacroTest.scala:22)  Service started. argument: arg=this is an argument, Random value: EXPRESSION:scala.util.Random.self.nextInt()=-1725949704
I:2018-05-07T14:31:43.086+03:00 ...leSinkTest:1 (LoggingMacroTest.scala:23) {userId=xxx, custom=value} Just a string; @type=const
W:2018-05-07T14:31:43.090+03:00 ...leSinkTest:1 (LoggingMacroTest.scala:24)  Just an integer: 1; @type=expr; @expr="Just an integer: ".+(1)
C:2018-05-07T14:31:43.093+03:00 ...leSinkTest:1 (LoggingMacroTest.scala:26)  This is an expression: UNNAMED:true=true and this is an other one: EXPRESSION:5.*(arg1).==(25)=true
C:2018-05-07T14:31:43.095+03:00 ...leSinkTest:1 (LoggingMacroTest.scala:28)  A failure happened: t=java.lang.RuntimeException: Oy vey!
java.lang.RuntimeException: Oy vey!
	at com.github.pshirshov.izumi.logstage.api.routing.ExampleService.start(LoggingMacroTest.scala:27)
	at com.github.pshirshov.izumi.logstage.sink.console.LoggingConsoleSinkTest.$anonfun$new$3(LoggingConsoleSinkTest.scala:23)
	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:12)
`


## Contexts defintion 

## Sinks configuration

