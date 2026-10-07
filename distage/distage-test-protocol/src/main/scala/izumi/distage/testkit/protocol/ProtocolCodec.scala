package izumi.distage.testkit.protocol

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, Printer}
import io.circe.parser.parse

object ProtocolCodec {
  final val SchemaVersion = 4
  final val MaxFrameCharacters = 1024 * 1024
  final val MaxJsonDepth = 128
  final val MaxFailureDepth = 32

  def validate(message: ProtocolMessage): Either[ProtocolDecodeError, Unit] = {
    try messageCodec.decodeJson(messageCodec(message)).left.map(error => ProtocolDecodeError(error.message)).map(_ => ())
    catch { case error: IllegalArgumentException => Left(ProtocolDecodeError(error.getMessage)) }
  }

  def encode(message: ProtocolMessage): String = {
    val payload = messageCodec(message)
    val frame = Printer.noSpaces.copy(escapeNonAscii = true).print(Json.obj("schemaVersion" -> Json.fromInt(SchemaVersion), "message" -> payload))
    require(frame.length <= MaxFrameCharacters, "Protocol frame exceeds its character limit")
    messageCodec.decodeJson(payload).fold(error => throw new IllegalArgumentException(s"Invalid protocol message: ${error.message}"), _ => ())
    frame
  }

  def decode(frame: String): Either[ProtocolDecodeError, ProtocolMessage] = parseJson(frame).flatMap { json =>
    val cursor = json.hcursor
    cursor.get[Int]("schemaVersion").left.map(error => ProtocolDecodeError(error.message)).flatMap { version =>
      if (version != SchemaVersion) Left(ProtocolDecodeError(s"Unsupported protocol schema version: $version"))
      else cursor.get[ProtocolMessage]("message").left.map(error => ProtocolDecodeError(error.message))
    }
  }

  private[protocol] def encodeTestArgument(id: TestId): String = encodeArgument(testIdCodec, id)
  private[protocol] def decodeTestArgument(value: String): Either[ProtocolDecodeError, TestId] = decodeArgument(testIdCodec, value)
  private[protocol] def encodeAxisArgument(choice: AxisChoice): String = encodeArgument(axisChoiceCodec, choice)
  private[protocol] def decodeAxisArgument(value: String): Either[ProtocolDecodeError, AxisChoice] = decodeArgument(axisChoiceCodec, value)

  private def encodeArgument[A](codec: Codec[A], value: A): String = {
    val json = codec(value)
    codec.decodeJson(json).fold(error => throw new IllegalArgumentException(error.message), _ => ())
    val encoded = Printer.noSpaces.copy(escapeNonAscii = true).print(json)
    require(encoded.length <= MaxFrameCharacters, "Protocol argument exceeds its character limit")
    encoded
  }

  private def decodeArgument[A](codec: Codec[A], value: String): Either[ProtocolDecodeError, A] = {
    parseJson(value).flatMap(json => codec.decodeJson(json).left.map(error => ProtocolDecodeError(error.message)))
  }

  private def parseJson(frame: String): Either[ProtocolDecodeError, Json] = {
    if (frame.length > MaxFrameCharacters) Left(ProtocolDecodeError("Protocol frame exceeds its character limit"))
    else if (frame.indexOf('\n') >= 0 || frame.indexOf('\r') >= 0) Left(ProtocolDecodeError("Protocol frame must occupy one channel line"))
    else {
      validateJsonDepth(frame).flatMap(_ => parse(frame).left.map(error => ProtocolDecodeError(error.message)))
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
  private implicit val resolvedSelectionCodec: Codec[ResolvedSelection] = product(
    Decoder.forProduct2("request", "tests")(ResolvedSelection.apply).emap { selection =>
      val tests = selection.tests
      if (tests.isEmpty || tests.map(_.id).distinct.size != tests.size) Left("Resolved selection must contain distinct tests")
      else if (tests.exists(_.id.target != selection.request.identity.target)) Left("Resolved test target differs from the request")
      else if (tests.exists(test => selection.request.selection match {
        case Selection.All => false
        case Selection.Only(suites, ids) => !suites.contains(test.id.suite) && !ids.contains(test.id)
      })) Left("Resolved selection contains an unselected test")
      else if (tests.exists(test => test.settings.axes.map(_.axis).distinct.size != test.settings.axes.size)) Left("Duplicate effective activation axes")
      else if (tests.exists(test => !(selection.request.overrides.axes ++ selection.request.overrides.axisFilters).forall(test.settings.axes.contains))) Left("Effective activation differs from the requested choices")
      else if (tests.exists(test => selection.request.overrides.memoization match {
        case MemoizationOverride.Inherit => false
        case MemoizationOverride.Enabled => !test.settings.memoization
        case MemoizationOverride.Disabled => test.settings.memoization
      })) Left("Effective memoization differs from the request")
      else Right(selection)
    },
    Encoder.forProduct2("request", "tests")(value => (value.request, value.tests)),
  )
  private implicit val dependencyKeyIdCodec: Codec[DependencyKeyId] = product(
    Decoder.decodeInt.emap(value => if (value >= 0) Right(DependencyKeyId(value)) else Left("Invalid plan key identity")),
    Encoder.encodeInt.contramap(_.value),
  )
  private implicit val dependencyKeyCodec: Codec[DependencyKey] = product(
    Decoder.forProduct2("id", "displayName")(DependencyKey.apply),
    Encoder.forProduct2("id", "displayName")(value => (value.id, value.displayName)),
  )
  private implicit val planScopeIdCodec: Codec[PlanScopeId] = product(
    Decoder.decodeVector[Int].emap { path =>
      if (path.isEmpty || path.exists(_ < 0)) Left("Invalid plan scope path") else Right(PlanScopeId(path))
    },
    Encoder.encodeVector[Int].contramap(_.path),
  )
  private implicit val planOperationCodec: Codec[PlanOperation] = enumeration(Vector(
    "import" -> PlanOperation.Import, "locatorReference" -> PlanOperation.LocatorReference,
    "createSet" -> PlanOperation.CreateSet, "callProvider" -> PlanOperation.CallProvider,
    "useInstance" -> PlanOperation.UseInstance, "referenceKey" -> PlanOperation.ReferenceKey,
    "createSubcontext" -> PlanOperation.CreateSubcontext, "executeEffect" -> PlanOperation.ExecuteEffect,
    "allocateResource" -> PlanOperation.AllocateResource, "makeProxy" -> PlanOperation.MakeProxy,
    "initProxy" -> PlanOperation.InitProxy,
  ))
  private implicit val planScopeKindCodec: Codec[PlanScopeKind] = enumeration(Vector(
    "runtime" -> PlanScopeKind.Runtime, "memoization" -> PlanScopeKind.Memoization, "test" -> PlanScopeKind.Test,
  ))
  private implicit val planStepCodec: Codec[PlanStep] = product(
    Decoder.forProduct3("key", "operation", "dependencies")(PlanStep.apply),
    Encoder.forProduct3("key", "operation", "dependencies")(value => (value.key, value.operation, value.dependencies)),
  )
  private implicit val planScopeCodec: Codec[PlanScope] = product(
    Decoder.forProduct4("id", "kind", "tests", "steps")(PlanScope.apply),
    Encoder.forProduct4("id", "kind", "tests", "steps")(value => (value.id, value.kind, value.tests, value.steps)),
  )
  private implicit val phaseCodec: Codec[FailurePhase] = enumeration(Vector(
    "discovery" -> FailurePhase.Discovery, "selection" -> FailurePhase.Selection, "planning" -> FailurePhase.Planning,
    "setup" -> FailurePhase.Setup, "test" -> FailurePhase.Test, "finalization" -> FailurePhase.Finalization, "transport" -> FailurePhase.Transport,
  ))
  private implicit val diagnosticErrorMessageCodec: Codec[DiagnosticErrorMessage] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "available" => cursor.get[String]("value").map(DiagnosticErrorMessage.Available.apply)
      case "unavailable" => Right(DiagnosticErrorMessage.Unavailable)
      case "accessorFailed" => cursor.get[String]("exceptionClass").map(DiagnosticErrorMessage.AccessorFailed.apply)
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case DiagnosticErrorMessage.Available(value) => tagged("available", "value" -> Json.fromString(value))
      case DiagnosticErrorMessage.Unavailable => tagged("unavailable")
      case DiagnosticErrorMessage.AccessorFailed(exceptionClass) => tagged("accessorFailed", "exceptionClass" -> Json.fromString(exceptionClass))
    },
  )
  private implicit val valueCodec: Codec[ObservedValue] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "evaluated" => cursor.get[String]("value").map(ObservedValue.Evaluated.apply)
      case "notEvaluated" => Right(ObservedValue.NotEvaluated)
      case "renderingFailed" => for {
        exceptionClass <- cursor.get[String]("exceptionClass")
        message <- cursor.get[DiagnosticErrorMessage]("message")
      } yield ObservedValue.RenderingFailed(exceptionClass, message)
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case ObservedValue.Evaluated(value) => tagged("evaluated", "value" -> Json.fromString(value))
      case ObservedValue.NotEvaluated => tagged("notEvaluated")
      case ObservedValue.RenderingFailed(exceptionClass, message) => tagged("renderingFailed", "exceptionClass" -> Json.fromString(exceptionClass), "message" -> diagnosticErrorMessageCodec(message))
    },
  )
  private implicit val diagnosticIdentityCodec: Codec[DiagnosticSourceIdentity] = product(
    Decoder.instance { cursor => for {
      kind <- cursor.get[String]("kind")
      path <- cursor.get[String]("path")
      identity <- if (path.isEmpty) Left(DecodingFailure("Diagnostic source identity must not be empty", cursor.history)) else kind match {
        case "relative" => Right(DiagnosticSourceIdentity.Relative(path))
        case "absolute" => Right(DiagnosticSourceIdentity.Absolute(path))
        case "virtual" => Right(DiagnosticSourceIdentity.Virtual(path))
        case other => unknown(cursor, other)
      }
    } yield identity },
    Encoder.instance {
      case DiagnosticSourceIdentity.Relative(path) => tagged("relative", "path" -> Json.fromString(path))
      case DiagnosticSourceIdentity.Absolute(path) => tagged("absolute", "path" -> Json.fromString(path))
      case DiagnosticSourceIdentity.Virtual(path) => tagged("virtual", "path" -> Json.fromString(path))
    },
  )
  private implicit val diagnosticPointCodec: Codec[DiagnosticPoint] = product(
    Decoder.forProduct3("offset", "line", "column")(DiagnosticPoint.apply).emap { point =>
      if (point.offset >= 0 && point.line >= 0 && point.column >= 0) Right(point) else Left("Invalid diagnostic source point")
    },
    Encoder.forProduct3("offset", "line", "column")(point => (point.offset, point.line, point.column)),
  )
  private implicit val diagnosticSpanCodec: Codec[DiagnosticSpan] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "unavailable" => Right(DiagnosticSpan.Unavailable)
      case "point" => cursor.get[DiagnosticPoint]("point").map(DiagnosticSpan.Point.apply)
      case "range" => for {
        start <- cursor.get[DiagnosticPoint]("start")
        end <- cursor.get[DiagnosticPoint]("end")
        range <- if (end.offset >= start.offset && (end.line > start.line || (end.line == start.line && end.column >= start.column))) {
          Right(DiagnosticSpan.Range(start, end))
        } else Left(DecodingFailure("Invalid diagnostic source range", cursor.history))
      } yield range
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case DiagnosticSpan.Unavailable => tagged("unavailable")
      case DiagnosticSpan.Point(point) => tagged("point", "point" -> diagnosticPointCodec(point))
      case DiagnosticSpan.Range(start, end) => tagged("range", "start" -> diagnosticPointCodec(start), "end" -> diagnosticPointCodec(end))
    },
  )
  private implicit val diagnosticSourceCodec: Codec[DiagnosticSource] = product(
    Decoder.forProduct3("identity", "span", "expression")(DiagnosticSource.apply),
    Encoder.forProduct3("identity", "span", "expression")(source => (source.identity, source.span, source.expression)),
  )
  private implicit val diagnosticValidationCodec: Codec[DiagnosticSourceValidation] = product(
    Decoder.instance { cursor => cursor.get[String]("kind").flatMap {
      case "matching" => Right(DiagnosticSourceValidation.Matching)
      case "mismatch" => Right(DiagnosticSourceValidation.Mismatch)
      case "unavailable" => Right(DiagnosticSourceValidation.Unavailable)
      case "rangeUnavailable" => Right(DiagnosticSourceValidation.RangeUnavailable)
      case "textUnavailable" => Right(DiagnosticSourceValidation.TextUnavailable)
      case "providerFailed" => for {
        exceptionClass <- cursor.get[String]("exceptionClass")
        message <- cursor.get[DiagnosticErrorMessage]("message")
      } yield DiagnosticSourceValidation.ProviderFailed(exceptionClass, message)
      case other => unknown(cursor, other)
    } },
    Encoder.instance {
      case DiagnosticSourceValidation.Matching => tagged("matching")
      case DiagnosticSourceValidation.Mismatch => tagged("mismatch")
      case DiagnosticSourceValidation.Unavailable => tagged("unavailable")
      case DiagnosticSourceValidation.RangeUnavailable => tagged("rangeUnavailable")
      case DiagnosticSourceValidation.TextUnavailable => tagged("textUnavailable")
      case DiagnosticSourceValidation.ProviderFailed(exceptionClass, message) => tagged("providerFailed", "exceptionClass" -> Json.fromString(exceptionClass), "message" -> diagnosticErrorMessageCodec(message))
    },
  )
  private implicit val diagnosticKindCodec: Codec[DiagnosticObservationKind] = enumeration(Vector(
    "booleanLeaf" -> DiagnosticObservationKind.BooleanLeaf, "booleanOperator" -> DiagnosticObservationKind.BooleanOperator,
    "comparison" -> DiagnosticObservationKind.Comparison, "operand" -> DiagnosticObservationKind.Operand, "opaque" -> DiagnosticObservationKind.Opaque,
  ))
  private implicit val observationCodec: Codec[DiagnosticObservation] = product(
    Decoder.forProduct4("expression", "span", "kind", "value")(DiagnosticObservation.apply),
    Encoder.forProduct4("expression", "span", "kind", "value")(value => (value.expression, value.span, value.kind, value.value)),
  )
  private implicit val diagnosticCodec: Codec[AssertionDiagnostic] = product(
    Decoder.forProduct4("source", "sourceValidation", "observations", "omittedObservations")(AssertionDiagnostic.apply).emap { diagnostic =>
      if (diagnostic.omittedObservations >= 0) Right(diagnostic) else Left("Omitted observation count must not be negative")
    },
    Encoder.forProduct4("source", "sourceValidation", "observations", "omittedObservations")(value => (value.source, value.sourceValidation, value.observations, value.omittedObservations)),
  )
  private implicit val captureFieldCodec: Codec[FailureCaptureField] = enumeration(Vector(
    "message" -> FailureCaptureField.Message, "cause" -> FailureCaptureField.Cause, "stack" -> FailureCaptureField.Stack,
  ))
  private implicit val captureErrorCodec: Codec[FailureCaptureError] = product(
    Decoder.forProduct2("field", "exceptionClass")(FailureCaptureError.apply).emap { error =>
      if (error.exceptionClass.nonEmpty) Right(error) else Left("Failure capture exception class must not be empty")
    },
    Encoder.forProduct2("field", "exceptionClass")(error => (error.field, error.exceptionClass)),
  )
  private def failureDecoder(depth: Int): Decoder[Failure] = Decoder.instance { cursor =>
    if (depth > MaxFailureDepth) Left(DecodingFailure("Failure graph depth exceeds its limit", cursor.history))
    else for {
      phase <- cursor.get[FailurePhase]("phase")
      exceptionClass <- cursor.get[String]("exceptionClass")
      message <- cursor.get[String]("message")
      stack <- cursor.get[Vector[String]]("stack")
      causes <- cursor.get[Vector[Failure]]("causes")(Decoder.decodeVector(failureDecoder(depth + 1)))
      assertion <- cursor.get[Option[AssertionDiagnostic]]("assertion")
      suppressed <- cursor.get[Vector[Failure]]("suppressed")(Decoder.decodeVector(failureDecoder(depth + 1)))
      captureErrors <- cursor.get[Vector[FailureCaptureError]]("captureErrors")
      _ <- if (captureErrors.map(_.field).distinct.size == captureErrors.size) Right(()) else Left(DecodingFailure("Duplicate failure capture fields", cursor.history))
      _ <- if (captureErrors.forall { error => error.field match {
        case FailureCaptureField.Message => message.isEmpty
        case FailureCaptureField.Cause => causes.isEmpty
        case FailureCaptureField.Stack => stack.isEmpty
      } }) Right(()) else Left(DecodingFailure("Failed capture fields must be unavailable", cursor.history))
    } yield Failure(phase, exceptionClass, message, stack, causes, assertion, suppressed, captureErrors)
  }
  private def failureEncoder(depth: Int): Encoder[Failure] = Encoder.instance { value =>
    require(depth <= MaxFailureDepth, "Failure graph depth exceeds its limit")
    Json.obj(
      "phase" -> phaseCodec(value.phase), "exceptionClass" -> Json.fromString(value.exceptionClass), "message" -> Json.fromString(value.message),
      "stack" -> Encoder.encodeVector[String].apply(value.stack), "causes" -> Json.fromValues(value.causes.map(failureEncoder(depth + 1).apply)),
      "assertion" -> Encoder.encodeOption[AssertionDiagnostic].apply(value.assertion),
      "suppressed" -> Json.fromValues(value.suppressed.map(failureEncoder(depth + 1).apply)),
      "captureErrors" -> Encoder.encodeVector[FailureCaptureError].apply(value.captureErrors),
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
  private implicit val planFailureCodec: Codec[PlanFailure] = product(
    Decoder.forProduct2("tests", "failure")(PlanFailure.apply),
    Encoder.forProduct2("tests", "failure")(value => (value.tests, value.failure)),
  )
  private implicit val planInspectionCodec: Codec[PlanInspection] = product(
    Decoder.forProduct3("keys", "scopes", "failures")(PlanInspection.apply),
    Encoder.forProduct3("keys", "scopes", "failures")(value => (value.keys, value.scopes, value.failures)),
  )
  private implicit val plannedSelectionCodec: Codec[PlannedSelection] = product(
    Decoder.forProduct2("selection", "inspection")(PlannedSelection.apply).emap { plan =>
      plan.inspection.validate(plan.selection.tests.map(_.id)).map(_ => plan)
    },
    Encoder.forProduct2("selection", "inspection")(value => (value.selection, value.inspection)),
  )
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
      case "resolved" => for {
        run <- cursor.get[RunId]("run")
        selection <- cursor.get[ResolvedSelection]("selection")
      } yield ProtocolMessage.Resolved(run, selection)
      case "planned" => for {
        run <- cursor.get[RunId]("run")
        plan <- cursor.get[PlannedSelection]("plan")
      } yield ProtocolMessage.Planned(run, plan)
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
      case ProtocolMessage.Resolved(run, selection) => tagged("resolved", "run" -> runIdCodec(run), "selection" -> resolvedSelectionCodec(selection))
      case ProtocolMessage.Planned(run, plan) => tagged("planned", "run" -> runIdCodec(run), "plan" -> plannedSelectionCodec(plan))
      case ProtocolMessage.Event(sequence, event) => tagged("event", "sequence" -> exactLongCodec(sequence), "event" -> eventCodec(event))
      case ProtocolMessage.Completed(outcome) => tagged("completed", "outcome" -> outcomeCodec(outcome))
      case ProtocolMessage.Rejected(run, failure) => tagged("rejected", "run" -> runIdCodec(run), "failure" -> failureCodec(failure))
    },
  )
}
