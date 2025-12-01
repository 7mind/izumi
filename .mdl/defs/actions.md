# Build Actions

# Environment

- `LANG=C.UTF-8`

# Passthrough
- `HOME`
- `USER`
- `OPENSSL_IV`
- `OPENSSL_KEY`
- `SONATYPE_USERNAME`
- `SONATYPE_PASSWORD`
- `NODE_AUTH_TOKEN`
- `CI_BRANCH_TAG`
- `CI_PULL_REQUEST`
- `CI_BRANCH`

# Axis
- `platform`=`{jvm*|js|js-nojvm}`
- `java_version`=`{11|17|21*|25}`
- `scala_version`=`{2.12|2.13*|3}`

# action: setup-jdk

Setup JDK path based on JAVA_VERSION

```bash
JAVA_VERSION_VAL="${sys.axis.java_version}"

case "$JAVA_VERSION_VAL" in
  11)
    JAVA_HOME_VAL="${JDK11:-}"
    ;;
  17)
    JAVA_HOME_VAL="${JDK17:-}"
    ;;
  21)
    JAVA_HOME_VAL="${JDK21:-}"
    ;;
  25)
    JAVA_HOME_VAL="${JDK25:-}"
    ;;
  *)
    echo "Unsupported JAVA_VERSION: $JAVA_VERSION_VAL" >&2
    exit 1
    ;;
esac

if [[ -z "$JAVA_HOME_VAL" ]]; then
  echo "JDK for JAVA_VERSION=$JAVA_VERSION_VAL is not set" >&2
  exit 1
fi

JAVA_BIN="$JAVA_HOME_VAL/bin"
PATH_VAL="$JAVA_BIN:$PATH"

ret java-home:String="$JAVA_HOME_VAL"
ret path:String="$PATH_VAL"
```

# action: setup-jvm-options

Setup JVM options and optimizations

```bash
JAVA_OPTIONS="${_JAVA_OPTIONS:-}"

JAVA_OPTIONS+=" -Duser.home=${env.HOME}"

if [[ -n "${JAVA_OPTIONS_TAIL:-}" ]]; then
  JAVA_OPTIONS+=" $JAVA_OPTIONS_TAIL"
fi

JAVA_OPTIONS+=" -Xmx4000M"
JAVA_OPTIONS+=" -XX:ReservedCodeCacheSize=384M"
JAVA_OPTIONS+=" -XX:NonProfiledCodeHeapSize=256M"
JAVA_OPTIONS+=" -XX:MaxMetaspaceSize=1024M"

JAVA_OPTIONS=$(echo "$JAVA_OPTIONS" | tr '\n' ' ' | tr -s ' ')

ret java-options:String="$JAVA_OPTIONS"
```

# action: setup-scala

Setup Scala version variables

```bash
SCALA_VERSION="${sys.axis.scala_version}"

VERSION_COMMAND="++ $SCALA_VERSION"

ret version-command:String="$VERSION_COMMAND"
```

# action: check-sbtgen-staleness

Decide whether sbt build files need regeneration

```bash
PROJECT_ROOT="${sys.project-root}"

readonly SBTGEN_INPUTS=(
  "$PROJECT_ROOT/sbtgen.sc"
  "$PROJECT_ROOT/sbtgen/Deps.scala"
  "$PROJECT_ROOT/sbtgen/project.scala"
  "$PROJECT_ROOT/project/Settings.scala"
  "$PROJECT_ROOT/project/Versions.scala"
  "$PROJECT_ROOT/project/project/PluginVersions.scala"
)

readonly GENERATED_SBT_FILES=(
  "$PROJECT_ROOT/build.sbt"
  "$PROJECT_ROOT/project/plugins.sbt"
)

readonly EPOCH_START=0

for input_file in "${SBTGEN_INPUTS[@]}"; do
  if [[ ! -f "$input_file" ]]; then
    echo "Missing sbtgen input: $input_file" >&2
    exit 1
  fi
done

for generated_file in "${GENERATED_SBT_FILES[@]}"; do
  if [[ ! -f "$generated_file" ]]; then
    retain
    exit 0
  fi
done

newest_input_mtime="$EPOCH_START"
for input_file in "${SBTGEN_INPUTS[@]}"; do
  modified_at=$(stat -c %Y "$input_file")
  if (( modified_at > newest_input_mtime )); then
    newest_input_mtime="$modified_at"
  fi
done

oldest_generated_mtime=$(stat -c %Y "${GENERATED_SBT_FILES[0]}")
for generated_file in "${GENERATED_SBT_FILES[@]:1}"; do
  modified_at=$(stat -c %Y "$generated_file")
  if (( modified_at < oldest_generated_mtime )); then
    oldest_generated_mtime="$modified_at"
  fi
done

if (( newest_input_mtime > oldest_generated_mtime )); then
  retain
fi
```

# action: retain.action.check-sbtgen-staleness

Retainer wrapper for sbtgen staleness check

```bash
dep action.check-sbtgen-staleness
```

# action: gen

Generate build files using sbtgen for the selected platform

```bash
dep action.setup-jdk
dep action.setup-jvm-options
dep action.setup-scala
soft action.check-sbtgen-staleness

JAVA_HOME="${action.setup-jdk.java-home}"
PATH="${action.setup-jdk.path}"
JAVA_OPTIONS="${action.setup-jvm-options.java-options}"
_JAVA_OPTIONS="$JAVA_OPTIONS"

PLATFORM="${sys.axis.platform}"

if [[ "$PLATFORM" == "jvm" ]]; then
  ARGS=()
elif [[ "$PLATFORM" == "js" ]]; then
  ARGS=("--js")
elif [[ "$PLATFORM" == "js-nojvm" ]]; then
  ARGS=("--nojvm" "--js")
else
  echo "Unknown platform: $PLATFORM" >&2
  exit 0
fi

bash sbtgen.sc "${ARGS[@]}"
```

# action: test

Run tests and binary compatibility checks

```bash
soft action.gen retain.action.check-sbtgen-staleness

JAVA_HOME="${action.setup-jdk.java-home}"
PATH="${action.setup-jdk.path}"
JAVA_OPTIONS="${action.setup-jvm-options.java-options}"
_JAVA_OPTIONS="$JAVA_OPTIONS"
VERSION_COMMAND="${action.setup-scala.version-command}"

sbt -batch -no-colors -v \
  --java-home "$JAVA_HOME" \
  "$VERSION_COMMAND clean" \
  "$VERSION_COMMAND Test/compile" \
  "$VERSION_COMMAND test"

docker rm "$(docker ps -aq)" || true
```

# action: coverage

Run coverage build

```bash
soft action.gen retain.action.check-sbtgen-staleness

JAVA_HOME="${action.setup-jdk.java-home}"
PATH="${action.setup-jdk.path}"
JAVA_OPTIONS="${action.setup-jvm-options.java-options}"
_JAVA_OPTIONS="$JAVA_OPTIONS"
VERSION_COMMAND="${action.setup-scala.version-command}"

sbt -batch -no-colors -v \
  --java-home "$JAVA_HOME" \
  "$VERSION_COMMAND clean" \
  coverage \
  "$VERSION_COMMAND Test/compile" \
  "$VERSION_COMMAND test" \
  "$VERSION_COMMAND coverageReport"

docker rm "$(docker ps -aq)" || true
```

# action: site-test

Build microsite for validation

```bash
soft action.gen retain.action.check-sbtgen-staleness

JAVA_HOME="${action.setup-jdk.java-home}"
PATH="${action.setup-jdk.path}"
JAVA_OPTIONS="${action.setup-jvm-options.java-options}"
_JAVA_OPTIONS="$JAVA_OPTIONS"
VERSION_COMMAND="${action.setup-scala.version-command}"

sbt -batch -no-colors -v \
  --java-home "$JAVA_HOME" \
  "project docs" \
  "$VERSION_COMMAND clean" \
  "$VERSION_COMMAND makeSite"
```

# action: site-publish

Publish microsite to GitHub Pages (skips on non-release branches)

```bash
soft action.gen retain.action.check-sbtgen-staleness

JAVA_HOME="${action.setup-jdk.java-home}"
PATH="${action.setup-jdk.path}"
JAVA_OPTIONS="${action.setup-jvm-options.java-options}"
_JAVA_OPTIONS="$JAVA_OPTIONS"
VERSION_COMMAND="${action.setup-scala.version-command}"

CI_PULL_REQUEST_VAL="${env.CI_PULL_REQUEST}"
CI_BRANCH_VAL="${env.CI_BRANCH}"
CI_BRANCH_TAG_VAL="${env.CI_BRANCH_TAG}"

if [[ "$CI_PULL_REQUEST_VAL" == "true" ]]; then
  echo "Publishing not allowed on pull requests"
  exit 0
fi

if [[ "$CI_BRANCH_VAL" != "develop" && ! "$CI_BRANCH_TAG_VAL" =~ ^v ]]; then
  echo "Publishing not allowed (CI_BRANCH=$CI_BRANCH_VAL, CI_BRANCH_TAG=$CI_BRANCH_TAG_VAL)"
  exit 0
fi

sbt -batch -no-colors -v \
  --java-home "$JAVA_HOME" \
  "project docs" \
  "$VERSION_COMMAND clean" \
  "$VERSION_COMMAND makeSite" \
  "$VERSION_COMMAND ghpagesSynchLocal" \
  "$VERSION_COMMAND ghpagesPushSite"
```

# action: publish-scala

Publish Scala artifacts to Sonatype (only on release branches/tags)

```bash
soft action.gen retain.action.check-sbtgen-staleness

JAVA_HOME="${action.setup-jdk.java-home}"
PATH="${action.setup-jdk.path}"
JAVA_OPTIONS="${action.setup-jvm-options.java-options}"
_JAVA_OPTIONS="$JAVA_OPTIONS"
VERSION_COMMAND="${action.setup-scala.version-command}"

SONATYPE_USERNAME_VAL="${env.SONATYPE_USERNAME}"
SONATYPE_PASSWORD_VAL="${env.SONATYPE_PASSWORD}"
CI_PULL_REQUEST_VAL="${env.CI_PULL_REQUEST}"
CI_BRANCH_VAL="${env.CI_BRANCH}"
CI_BRANCH_TAG_VAL="${env.CI_BRANCH_TAG}"

if [[ -z "$SONATYPE_USERNAME_VAL" ]]; then
  echo "Missing SONATYPE_USERNAME, skipping publish"
  exit 0
fi

if [[ -z "$SONATYPE_PASSWORD_VAL" ]]; then
  echo "Missing SONATYPE_PASSWORD, skipping publish"
  exit 0
fi

if [[ "$CI_PULL_REQUEST_VAL" == "true" ]]; then
  echo "Publishing not allowed on pull requests"
  exit 0
fi

if [[ "$CI_BRANCH_VAL" != "develop" && ! "$CI_BRANCH_TAG_VAL" =~ ^v ]]; then
  echo "Publishing not allowed (CI_BRANCH=$CI_BRANCH_VAL, CI_BRANCH_TAG=$CI_BRANCH_TAG_VAL)"
  exit 0
fi

CREDENTIALS_FILE="${sys.project-root}/.secrets/credentials.sonatype-nexus.properties"
mkdir -p "$(dirname "$CREDENTIALS_FILE")"
printf "%s\n" "realm=Sonatype Nexus Repository Manager" "host=central.sonatype.com" "user=${SONATYPE_USERNAME_VAL}" "password=${SONATYPE_PASSWORD_VAL}" > "$CREDENTIALS_FILE"

if [[ "$CI_BRANCH_TAG_VAL" =~ ^v.*$ ]]; then
  sbt -batch -no-colors -v \
      --java-home "$JAVA_HOME" \
      "show credentials" \
      "$VERSION_COMMAND clean" \
      "$VERSION_COMMAND package" \
      "$VERSION_COMMAND publishSigned" \
      "sonaUpload" \
      "sonaRelease"
else
  sbt -batch -no-colors -v \
      --java-home "$JAVA_HOME" \
      "show credentials" \
      "$VERSION_COMMAND clean" \
      "$VERSION_COMMAND package" \
      "$VERSION_COMMAND publishSigned"
fi
```
