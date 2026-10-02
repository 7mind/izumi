package izumi.distage.testkit.protocol

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.parser.parse

object ProtocolCodec {
  final val SchemaVersion = 1
  final val MaxFrameCharacters = 1024 * 1024
  final val MaxJsonDepth = 128
  final val MaxFailureDepth = 32

  def validate(message: ProtocolMessage): Either[ProtocolDecodeError, Unit] = {
    try messageCodec.decodeJson(messageCodec(message)).left.map(error => ProtocolDecodeError(error.message)).map(_ => ())
    catch { case error: IllegalArgumentException => Left(ProtocolDecodeError(error.getMessage)) }
  }

  def encode(message: ProtocolMessage): String = {
    val payload = messageCodec(message)
    val frame = Json.obj("schemaVersion" -> Json.fromInt(SchemaVersion), "message" -> payload).noSpaces
    require(frame.length <= MaxFrameCharacters, "Protocol frame exceeds its character limit")
    messageCodec.decodeJson(payload).fold(error => throw new IllegalArgumentException(s"Invalid protocol message: ${error.message}"), _ => ())
    frame
  }

  def decode(frame: String): Either[ProtocolDecodeError, ProtocolMessage] = {
    if (frame.length > MaxFrameCharacters) Left(ProtocolDecodeError("Protocol frame exceeds its character limit"))
    else if (frame.indexOf('\n') >= 0 || frame.indexOf('\r') >= 0) Left(ProtocolDecodeError("Protocol frame must occupy one channel line"))
    else {
      validateJsonDepth(frame).flatMap(_ => parse(frame).left.map(error => ProtocolDecodeError(error.message))).flatMap { json =>
        val cursor = json.hcursor
        cursor.get[Int]("schemaVersion").left.map(error => ProtocolDecodeError(error.message)).flatMap { version =>
          if (version != SchemaVersion) Left(ProtocolDecodeError(s"Unsupported protocol schema version: $version"))
          else cursor.get[ProtocolMessage]("message").left.map(error => ProtocolDecodeError(error.message))
        }
      }
    }
  }

  private def validateJsonDepth(frame: String): Either[ProtocolDecodeError, Unit] = {
    var index = 0
    var depth = 0
    var inString = false
    var escaped = false
    while (index < frame.length) {
      val character = frame.charAt(index)
      if (inString) {
        if (escaped) escaped = false
        else if (character == '\\') escaped = true
        else if (character == '"') inString = false
      } else if (character == '"') inString = true
      else if (character == '{' || character == '[') {
        depth += 1
        if (depth > MaxJsonDepth) return Left(ProtocolDecodeError("JSON nesting exceeds its depth limit"))
      } else if (character == '}' || character == ']') {
        depth -= 1
        if (depth < 0) return Left(ProtocolDecodeError("Invalid JSON nesting"))
      }
      index += 1
    }
    Right(())
  }

  private def product[A](decoder: Decoder[A], encoder: Encoder[A]): Codec[A] = Codec.from(decoder, encoder)
  private def named[A](construct: String => A, value: A => String): Codec[A] = product(
    Decoder.decodeString.emap(text => if (text.nonEmpty) Right(construct(text)) else Left("Identity must not be empty")),
    Encoder.encodeString.contramap(value),
  )
  private def enumeration[A](entries: Vector[(String, A)]): Codec[A] = product(
    Decoder.decodeString.emap(name => entries.find(_._1 == name).map(_._2).toRight(s"Unknown protocol value: $name")),
    Encoder.encodeString.contramap(value => entries.find(_._2 == value).get._1),
  )
  private def tagged(kind: String, fields: (String, Json)*): Json = Json.obj((Vector("kind" -> Json.fromString(kind)) ++ fields)*)
  private def unknown[A](cursor: HCursor, kind: String): Decoder.Result[A] = Left(DecodingFailure(s"Unknown protocol kind: $kind", cursor.history))

  private implicit val exactLongCodec: Codec[Long] = product(
    Decoder.decodeString.emap { text =>
      try Right(text.toLong)
      catch { case _: NumberFormatException => Left("Invalid decimal 64-bit integer") }
    },
    Encoder.encodeString.contramap(_.toString),
  )
  private implicit val buildIdCodec: Codec[BuildId] = named(BuildId.apply, _.value)
  private implicit val catalogueIdCodec: Codec[CatalogueId] = named(CatalogueId.apply, _.value)
  private implicit val targetIdCodec: Codec[BuildTargetId] = named(BuildTargetId.apply, _.value)
  private implicit val suiteIdCodec: Codec[SuiteId] = named(SuiteId.apply, _.value)
  private implicit val runIdCodec: Codec[RunId] = named(RunId.apply, _.value)
  private implicit val axisIdCodec: Codec[AxisId] = named(AxisId.apply, _.value)
  private implicit val axisValueCodec: Codec[AxisValue] = named(AxisValue.apply, _.value)
  private implicit val identityCodec: Codec[CatalogueIdentity] = product(
    Decoder.forProduct3("build", "target", "catalogue")(CatalogueIdentity.apply),
    Encoder.forProduct3("build", "target", "catalogue")(value => (value.build, value.target, value.catalogue)),
  )
  private implicit val testIdCodec: Codec[TestId] = product(
    Decoder.forProduct4("target", "suite", "path", "variant")(TestId.apply).emap { id =>
      if (id.path.isEmpty) Left("Test path must not be empty")
      else if (id.variant.contains("")) Left("Explicit variant must not be empty")
      else Right(id)
    },
    Encoder.forProduct4("target", "suite", "path", "variant")(value => (value.target, value.suite, value.path, value.variant)),
  )
  private implicit val axisChoiceCodec: Codec[AxisChoice] = product(
    Decoder.forProduct2("axis", "value")(AxisChoice.apply),
    Encoder.forProduct2("axis", "value")(value => (value.axis, value.value)),
  )
  private implicit val effectiveSettingsCodec: Codec[EffectiveSettings] = product(
    Decoder.forProduct2("axes", "memoization")(EffectiveSettings.apply),
    Encoder.forProduct2("axes", "memoization")(value => (value.axes, value.memoization)),
  )
  private implicit val memoizationCodec: Codec[MemoizationOverride] = enumeration(Vector(
    "inherit" -> MemoizationOverride.Inherit, "enabled" -> MemoizationOverride.Enabled, "disabled" -> MemoizationOverride.Disabled,
  ))
  private implicit val selectionCodec: Codec[Selection] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "all" => Right(Selection.All)
      case "only" => for {
        suites <- cursor.get[Vector[SuiteId]]("suites")
        tests <- cursor.get[Vector[TestId]]("tests")
        result <- if (suites.nonEmpty || tests.nonEmpty) Right(Selection.Only(suites, tests)) else Left(DecodingFailure("Explicit selection must not be empty", cursor.history))
      } yield result
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case Selection.All => tagged("all")
      case Selection.Only(suites, tests) => tagged("only", "suites" -> Encoder.encodeVector[SuiteId].apply(suites), "tests" -> Encoder.encodeVector[TestId].apply(tests))
    },
  )
  private implicit val overridesCodec: Codec[RunOverrides] = product(
    Decoder.forProduct3("axes", "axisFilters", "memoization")(RunOverrides.apply),
    Encoder.forProduct3("axes", "axisFilters", "memoization")(value => (value.axes, value.axisFilters, value.memoization)),
  )
  private implicit val requestCodec: Codec[RunRequest] = product(
    Decoder.forProduct3("identity", "selection", "overrides")(RunRequest.apply),
    Encoder.forProduct3("identity", "selection", "overrides")(value => (value.identity, value.selection, value.overrides)),
  )
  private implicit val locationCodec: Codec[SourceLocation] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "unavailable" => Right(SourceLocation.Unavailable)
      case "known" => for {
        path <- cursor.get[String]("path")
        line <- cursor.get[Int]("line")
        column <- cursor.get[Option[Int]]("column")
        result <- if (path.nonEmpty && line >= 0 && column.forall(_ >= 0)) Right(SourceLocation.Known(path, line, column)) else Left(DecodingFailure("Invalid source location", cursor.history))
      } yield result
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case SourceLocation.Unavailable => tagged("unavailable")
      case SourceLocation.Known(path, line, column) => tagged("known", "path" -> Json.fromString(path), "line" -> Json.fromInt(line), "column" -> Encoder.encodeOption[Int].apply(column))
    },
  )
  private implicit val suiteCodec: Codec[SuiteDescriptor] = product(
    Decoder.forProduct2("id", "displayName")(SuiteDescriptor.apply),
    Encoder.forProduct2("id", "displayName")(value => (value.id, value.displayName)),
  )
  private implicit val testCodec: Codec[TestDescriptor] = product(
    Decoder.forProduct4("id", "displayName", "location", "settings")(TestDescriptor.apply),
    Encoder.forProduct4("id", "displayName", "location", "settings")(value => (value.id, value.displayName, value.location, value.settings)),
  )
  private implicit val catalogueCodec: Codec[Catalogue] = product(
    Decoder.forProduct3("identity", "suites", "tests")(Catalogue.apply),
    Encoder.forProduct3("identity", "suites", "tests")(value => (value.identity, value.suites, value.tests)),
  )
  private implicit val phaseCodec: Codec[FailurePhase] = enumeration(Vector(
    "discovery" -> FailurePhase.Discovery, "selection" -> FailurePhase.Selection, "planning" -> FailurePhase.Planning,
    "setup" -> FailurePhase.Setup, "test" -> FailurePhase.Test, "finalization" -> FailurePhase.Finalization, "transport" -> FailurePhase.Transport,
  ))
  private implicit val valueCodec: Codec[ObservedValue] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "evaluated" => cursor.get[String]("value").map(ObservedValue.Evaluated.apply)
      case "notEvaluated" => Right(ObservedValue.NotEvaluated)
      case "renderingFailed" => for {
        exceptionClass <- cursor.get[String]("exceptionClass")
        message <- cursor.get[String]("message")
      } yield ObservedValue.RenderingFailed(exceptionClass, message)
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case ObservedValue.Evaluated(value) => tagged("evaluated", "value" -> Json.fromString(value))
      case ObservedValue.NotEvaluated => tagged("notEvaluated")
      case ObservedValue.RenderingFailed(exceptionClass, message) => tagged("renderingFailed", "exceptionClass" -> Json.fromString(exceptionClass), "message" -> Json.fromString(message))
    },
  )
  private implicit val observationCodec: Codec[DiagnosticObservation] = product(
    Decoder.forProduct3("expression", "location", "value")(DiagnosticObservation.apply),
    Encoder.forProduct3("expression", "location", "value")(value => (value.expression, value.location, value.value)),
  )
  private implicit val diagnosticCodec: Codec[AssertionDiagnostic] = product(
    Decoder.forProduct3("location", "expression", "observations")(AssertionDiagnostic.apply),
    Encoder.forProduct3("location", "expression", "observations")(value => (value.location, value.expression, value.observations)),
  )
  private def failureDecoder(depth: Int): Decoder[Failure] = Decoder.instance { cursor =>
    if (depth > MaxFailureDepth) Left(DecodingFailure("Failure cause depth exceeds its limit", cursor.history))
    else for {
      phase <- cursor.get[FailurePhase]("phase")
      exceptionClass <- cursor.get[String]("exceptionClass")
      message <- cursor.get[String]("message")
      stack <- cursor.get[Vector[String]]("stack")
      causeJson <- cursor.get[Vector[Json]]("causes")
      causes <- causeJson.foldLeft[Decoder.Result[Vector[Failure]]](Right(Vector.empty)) { (previous, json) =>
        for {
          values <- previous
          cause <- failureDecoder(depth + 1).decodeJson(json)
        } yield values :+ cause
      }
      assertion <- cursor.get[Option[AssertionDiagnostic]]("assertion")
    } yield Failure(phase, exceptionClass, message, stack, causes, assertion)
  }
  private def failureEncoder(depth: Int): Encoder[Failure] = Encoder.instance { value =>
    require(depth <= MaxFailureDepth, "Failure cause depth exceeds its limit")
    Json.obj(
      "phase" -> phaseCodec(value.phase), "exceptionClass" -> Json.fromString(value.exceptionClass), "message" -> Json.fromString(value.message),
      "stack" -> Encoder.encodeVector[String].apply(value.stack), "causes" -> Json.fromValues(value.causes.map(failureEncoder(depth + 1).apply)),
      "assertion" -> Encoder.encodeOption[AssertionDiagnostic].apply(value.assertion),
    )
  }
  private implicit val failureCodec: Codec[Failure] = product(failureDecoder(1), failureEncoder(1))
  private implicit val statusCodec: Codec[TestStatus] = enumeration(Vector(
    "succeeded" -> TestStatus.Succeeded, "failed" -> TestStatus.Failed, "cancelled" -> TestStatus.Cancelled, "skipped" -> TestStatus.Skipped,
  ))
  private implicit val resultCodec: Codec[TestResult] = product(
    Decoder.forProduct4("id", "status", "failure", "durationNanos")(TestResult.apply).emap { result =>
      if (result.durationNanos < 0) Left("Test duration must not be negative")
      else if (result.status == TestStatus.Failed && result.failure.isEmpty) Left("Failed test must carry a failure")
      else if (result.status == TestStatus.Succeeded && result.failure.nonEmpty) Left("Successful test must not carry a failure")
      else if (result.status == TestStatus.Skipped && result.failure.nonEmpty) Left("Skipped test must not carry a failure")
      else Right(result)
    },
    Encoder.forProduct4("id", "status", "failure", "durationNanos")(value => (value.id, value.status, value.failure, value.durationNanos)),
  )
  private implicit val outcomeCodec: Codec[RunOutcome] = product(
    Decoder.forProduct4("run", "results", "failures", "cancelled")(RunOutcome.apply),
    Encoder.forProduct4("run", "results", "failures", "cancelled")(value => (value.run, value.results, value.failures, value.cancelled)),
  )
  private implicit val eventCodec: Codec[RunEvent] = product(
    Decoder.instance { cursor => for {
      kind <- cursor.get[String]("kind")
      run <- cursor.get[RunId]("run")
      event <- kind match {
        case "started" => Right(RunEvent.Started(run))
        case "testStarted" => cursor.get[TestId]("test").map(RunEvent.TestStarted(run, _))
        case "testCompleted" => cursor.get[TestResult]("result").map(RunEvent.TestCompleted(run, _))
        case "phaseFailed" => cursor.get[Failure]("failure").map(RunEvent.PhaseFailed(run, _))
        case "finished" => cursor.get[RunOutcome]("outcome").flatMap { outcome =>
          if (outcome.run == run) Right(RunEvent.Finished(run, outcome)) else Left(DecodingFailure("Event and outcome run identities differ", cursor.history))
        }
        case other => unknown(cursor, other)
      }
    } yield event },
    Encoder.instance { event =>
      val run = "run" -> runIdCodec(event.run)
      event match {
        case _: RunEvent.Started => tagged("started", run)
        case RunEvent.TestStarted(_, test) => tagged("testStarted", run, "test" -> testIdCodec(test))
        case RunEvent.TestCompleted(_, result) => tagged("testCompleted", run, "result" -> resultCodec(result))
        case RunEvent.PhaseFailed(_, failure) => tagged("phaseFailed", run, "failure" -> failureCodec(failure))
        case RunEvent.Finished(_, outcome) => tagged("finished", run, "outcome" -> outcomeCodec(outcome))
      }
    },
  )
  private implicit val operationCodec: Codec[RequestOperation] = enumeration(Vector(
    "resolve" -> RequestOperation.Resolve, "plan" -> RequestOperation.Plan, "execute" -> RequestOperation.Execute,
  ))
  private implicit val messageCodec: Codec[ProtocolMessage] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "discover" => for {
        run <- cursor.get[RunId]("run")
        build <- cursor.get[BuildId]("build")
        target <- cursor.get[BuildTargetId]("target")
      } yield ProtocolMessage.Discover(run, build, target)
      case "request" => for {
        operation <- cursor.get[RequestOperation]("operation")
        run <- cursor.get[RunId]("run")
        request <- cursor.get[RunRequest]("request")
      } yield ProtocolMessage.Request(operation, run, request)
      case "cancel" => cursor.get[RunId]("run").map(ProtocolMessage.Cancel.apply)
      case "discovered" => for {
        run <- cursor.get[RunId]("run")
        catalogue <- cursor.get[Catalogue]("catalogue")
      } yield ProtocolMessage.Discovered(run, catalogue)
      case "event" => for {
        sequence <- cursor.get[Long]("sequence")
        event <- cursor.get[RunEvent]("event")
        result <- if (sequence >= 0) Right(ProtocolMessage.Event(sequence, event)) else Left(DecodingFailure("Event sequence must not be negative", cursor.history))
      } yield result
      case "completed" => cursor.get[RunOutcome]("outcome").map(ProtocolMessage.Completed.apply)
      case "rejected" => for {
        run <- cursor.get[RunId]("run")
        failure <- cursor.get[Failure]("failure")
      } yield ProtocolMessage.Rejected(run, failure)
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case ProtocolMessage.Discover(run, build, target) => tagged("discover", "run" -> runIdCodec(run), "build" -> buildIdCodec(build), "target" -> targetIdCodec(target))
      case ProtocolMessage.Request(operation, run, request) => tagged("request", "operation" -> operationCodec(operation), "run" -> runIdCodec(run), "request" -> requestCodec(request))
      case ProtocolMessage.Cancel(run) => tagged("cancel", "run" -> runIdCodec(run))
      case ProtocolMessage.Discovered(run, catalogue) => tagged("discovered", "run" -> runIdCodec(run), "catalogue" -> catalogueCodec(catalogue))
      case ProtocolMessage.Event(sequence, event) => tagged("event", "sequence" -> exactLongCodec(sequence), "event" -> eventCodec(event))
      case ProtocolMessage.Completed(outcome) => tagged("completed", "outcome" -> outcomeCodec(outcome))
      case ProtocolMessage.Rejected(run, failure) => tagged("rejected", "run" -> runIdCodec(run), "failure" -> failureCodec(failure))
    },
  )
}
