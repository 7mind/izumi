package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}

object PerfTest {
  final val acceptingNullRouter = new LogRouter {
    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = true

    override protected def doLog(entry: Log.Entry): Unit = {}
  }

  def main(args: Array[String]): Unit = {
    val logger = IzLogger(acceptingNullRouter)

    var i0 = 0
    while (i0 < 10000) {
      i0+=1
      logger.debug("jit")
    }

//    val receiver = logger.receiver

    val results = 1 to 1000 map { _ =>
      val start = System.nanoTime()
      var i = 0
      while (i < 100000) {
        i += 1

        logger.debug("heya-ya-hey")
//        if (logger.receiver.acceptable(LoggerId(pos.applicationPointId), Log.Level.Debug)) {
//        }
//
//        val pos = CodePositionMaterializer.materialize.get
//        val logLevel = Log.Level.Debug
////        val loggerId = LoggerId.fromCodePosition(pos)
//        val loggerId = LoggerId(pos.applicationPointId)
////        logger.receiver.log(entry)
//        if (receiver.acceptable(loggerId, logLevel)) {
//
//          val message = Message(StringContext("heya-ya-hey"), List(LogArg(Seq("@type"), "const", hidden = false)))
//
//          val thread = Thread.currentThread()
//          val tsMillis = System.currentTimeMillis()
//          val data = ThreadData(thread.getName, thread.getId)
//          val dynamicContext = DynamicContext(logLevel, data, tsMillis)
//          val extendedStaticContext = StaticExtendedContext(loggerId, pos.position)
//
//          val context = new Log.Context(extendedStaticContext, dynamicContext, logger.customContext)
////          val context = new Log.Context(extendedStaticContext, dynamicContext, logger.contextCustom)
//          val entry = new Log.Entry(message, context)
//
//          receiver.log(entry)
//        }

      }
      val end = System.nanoTime()

      end-start: BigInt
    }

    println(s"Result: avg of 1000 runs ${results.sum[BigInt] / results.size} nanos")

  }
}

// accepting null tests:
// Result: avg of 1000 runs 13581504 nanos
// Result: avg of 1000 runs 15451058 nanos
// Result: avg of 1000 runs 18213739 nanos
// Result: avg of 1000 runs 15764597 nanos



// null tests:

// new:
// Result: avg of 1000 runs 14102548 nanos
// Result: avg of 1000 runs 13658187 nanos

// inline all:
// Result: avg of 1000 runs 12602929 nanos
// Result: avg of 1000 runs 1054526 nanos

// inline constants:
// Result: avg of 1000 runs 6696346 nanos

// val all:
// Result: avg of 1000 runs 832194 nanos

// lambda + no-anyval-codeposition
// Result: avg of 1000 runs 1031065 nanos

// lambda + anyval codeposition
// Result: avg of 1000 runs 461838 nanos
// Result: avg of 1000 runs 483007 nanos

// lambda-abstract + anyval codeposition
// Result: avg of 1000 runs 447830 nanos

// old:
// Result: avg of 1000 runs 1114780 nanos


// old = inlined:
// Result: avg of 1000 runs 14489897 nanos

// old = inlined-with-proper-if:
// Result: avg of 1000 runs 1066996 nanos




// print tests:

// Result: 2733118063 nanos
// Result: 2803161680 nanos
// Result: 2991024260 nanos

// new:
// Result: avg of 10 runs 2285790767 nanos
// Result: avg of 10 runs 2216757380 nanos
// Result: avg of 10 runs 2292143744 nanos
// Result: avg of 10 runs 3205364461 nanos
// Result: avg of 10 runs 1931434831 nanos
// Result: avg of 10 runs 1842183675 nanos
// Result: avg of 10 runs 1871253507 nanos
// Result: avg of 10 runs 1976296972 nanos

// old:
// Result: avg of 10 runs 2188380910 nanos
// Result: avg of 10 runs 2275079815 nanos
// Result: avg of 10 runs 1860807122 nanos
// Result: avg of 10 runs 2013032035 nanos
