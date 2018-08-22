#!/bin/bash -xe

export GOPATH=$HOME/gopath
export PATH=$HOME/gopath/bin:$PATH

function block_open {
    echo -en "travis_fold:start:$1\\r"
}

function block_close {
    echo -en "travis_fold:end:$1\\r"
}

function bopen {
    block_open ${FUNCNAME[1]}
}

function bclose {
    block_close ${FUNCNAME[1]}
}

function csbt {
    COMMAND="time sbt -v ++$TRAVIS_SCALA_VERSION $*"
    eval $COMMAND
}

function versionate {
  bopen
  if [[ "$TRAVIS_BRANCH" != "master" &&  "$TRAVIS_BRANCH" != "develop" && ! ( "$TRAVIS_TAG" =~ ^v.*$ ) ]] ; then
    echo "Setting version suffix to $TRAVIS_BRANCH"
    csbt "addVersionSuffix $TRAVIS_BRANCH"
  else
    echo "No version suffix required"
  fi
  bclose
}

function coverage {
  bopen
  csbt clean coverage test coverageReport || exit 1
  bash <(curl -s https://codecov.io/bash)
  bclose
}

function scripted {
  bopen
  csbt clean publishLocal '"scripted sbt-izumi-plugins/*"' || exit 1
  bclose
}

function site {
  bopen
  if [[ "$TRAVIS_BRANCH" == "develop" || "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
    echo "Publishing site from branch=$TRAVIS_BRANCH; tag=$TRAVIS_TAG"
    csbt ghpagesPushSite || exit 1
  else
    echo "Not publishing site, because $TRAVIS_BRANCH is not 'develop'"
  fi
  bclose
}

function publish {
  bopen
  if [[ ! -f .secrets/credentials.sonatype-nexus.properties ]] ; then
    return 0
  fi

  if [[ "$TRAVIS_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi

  echo "PUBLISH..."
  csbt clean publishSigned || exit 1

  if [[ "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
    csbt sonatypeRelease || exit 1
  fi
  bclose
}

function deploy {
  publish
  site
}

PARAMS=()
SOFT=0
SKIP=()
for i in "$@"
do
case $i in
    nothing)
        echo "Doing nothing..."
    ;;

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
