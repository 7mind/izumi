#!/bin/bash -xe

function build {
  if [[ "$TRAVIS_BRANCH" != "master" &&  "$TRAVIS_BRANCH" != "develop" && ! ( "$TRAVIS_TAG" =~ ^v.*$ ) ]] ; then
    sbt ++$TRAVIS_SCALA_VERSION "addVersionSuffix $TRAVIS_BRANCH"
  fi

  echo "PUBLISH..."
  if [[ -f credentials.sonatype-nexus.properties ]] ; then
    sbt +clean +test +publishSigned || exit 1
    if [[ "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
        sbt ++$TRAVIS_SCALA_VERSION sonatypeRelease || exit 1
    fi
  else
    sbt +clean +test +package || exit 1
  fi

  echo "SCRIPTED..."
  sbt clean "scripted sbt-izumi-plugins/*" || exit 1

  echo "COVERAGE..."
  sbt clean coverage test coverageReport || exit 1
  bash <(curl -s https://codecov.io/bash)
}

PARAMS=()
SOFT=0
SKIP=()
for i in "$@"
do
case $i in
    build)
        build
    ;;
    *)
        echo "Unknown option"
        exit 1
    ;;
esac
done
