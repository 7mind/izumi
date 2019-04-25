Syntax Reference
================

1) Simple variable:
   ```scala
   logger.info(s"My message: $argument")
   ```
2) Chain:
   ```scala
   logger.info(s"My message: ${call.method} ${access.value}")
   ```
3) Named expression:
   ```scala
   logger.info(s"My message: ${Some.expression -> "argname"}")
   ```
4) Invisible name expression:
   ```scala
   logger.info(s"My message: ${Some.expression -> "argname" -> null}")
   ```
5) De-camelcased name:
   ```scala
   logger.info(${camelCaseName-> ' '})
   ```
