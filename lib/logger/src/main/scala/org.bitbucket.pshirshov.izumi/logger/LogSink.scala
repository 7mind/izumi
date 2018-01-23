package org.bitbucket.pshirshov.izumi.logger

import scala.annotation.tailrec

trait LogSink {

  def messageParser(msg: Log.Message): Map[String, Any] = {
    def truncateKey(s: String) = {
      "(\\w+)=".r.findFirstIn(s).map(_.dropRight(1))
    }
    @tailrec
    def parse(keysIterator: Iterator[String]
              , valuesIterator: Iterator[Any]
              , map: Map[String, Any] = Map.empty
             ): Map[String, Any] = {
      if (valuesIterator.hasNext) {
        val v = valuesIterator.next()
        val k = Option(keysIterator.next()).flatMap(truncateKey).getOrElse(s"unknown_$v")
        parse(keysIterator, valuesIterator, map ++ Map(k -> v))
      } else {
        map
      }
    }

    parse(msg.template.parts.iterator, msg.args.iterator)
  }


  def flush(e: Log.Entry): Unit = {
    val values = messageParser(e.message)
    println(values)
    // here we may:
    // 0) Keep a message buffer
    // 1) Parse log message into values=Map[String, Any], message_template=parts.join('_'), message
    // 2) Make full_values = custom_log_context + context + values + Map(message-> message, message_template->message_template)
    // 3) Perform rendering/save values into database/etc
  }
}