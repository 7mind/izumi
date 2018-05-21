#!/bin/bash -xe

function csbt {
sbt ++$TRAVIS_SCALA_VERSION -jvm-opts ./.sbtopts.travis $*
}

function versionate {
  if [[ "$TRAVIS_BRANCH" != "master" &&  "$TRAVIS_BRANCH" != "develop" && ! ( "$TRAVIS_TAG" =~ ^v.*$ ) ]] ; then
    echo "Setting version suffix to $TRAVIS_BRANCH"
    csbt "addVersionSuffix $TRAVIS_BRANCH"
  else
    echo "No version suffix required"
  fi
}

function coverage {
  echo "COVERAGE..."
  csbt clean coverage test coverageReport || exit 1
  bash <(curl -s https://codecov.io/bash)
}

function scripted {
  echo "SCRIPTED..."
  csbt clean "scripted sbt-izumi-plugins/*" || exit 1
}

function deploy {
  if [[ -f .secrets/credentials.sonatype-nexus.properties ]] ; then
    echo "PUBLISH..."
    csbt +clean +publishSigned || exit 1

    if [[ "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
        csbt sonatypeRelease || exit 1
    fi
  fi
}

PARAMS=()
SOFT=0
SKIP=()
for i in "$@"
do
case $i in
    versionate)
        versionate
    ;;

    coverage)
        coverage
    ;;

    scripted)
        scripted
    ;;

    deploy)
        deploy
    ;;

    *)
        echo "Unknown option"
        exit 1
    ;;
esac
done
