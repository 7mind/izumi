#!/usr/bin/env bash
set -xe

# `++ 2.13.0 compile` has a different semantic than `;++2.13.0;compile`
# Strict aggregation applies ONLY to former,
#   and ONLY if crossScalaVersions := Nil is set in root project and ALL nested aggregates
# see https://github.com/sbt/sbt/issues/3698#issuecomment-475955454
# and https://github.com/sbt/sbt/pull/3995/files
# TL;DR strict aggregation in sbt is broken; this is a workaround

function scala3 {
  echo "Using Scala 3..."
  VERSION_COMMAND="++ $SCALA3 "
}

function scala213 {
  echo "Using Scala 2.13..."
  VERSION_COMMAND="++ $SCALA213 "
}

function scala212 {
  echo "Using Scala 2.12..."
  VERSION_COMMAND="++ $SCALA212 "
}

function scalaall {
  VERSION_COMMAND="+"
}

function csbt {
  COMMAND="time sbt -batch -no-colors -v $*"
  eval $COMMAND
}

function coverage {
  csbt "'${VERSION_COMMAND}clean'" coverage "'${VERSION_COMMAND}Test/compile'" "'${VERSION_COMMAND}test'" "'${VERSION_COMMAND}coverageReport'" || exit 1
}

function test {
  csbt "'${VERSION_COMMAND}clean'" "'${VERSION_COMMAND}Test/compile'" "'${VERSION_COMMAND}test'" || exit 1
}

function site-publish {
  echo "Publishing site from branch=$CI_BRANCH; tag=$CI_BRANCH_TAG"
  csbt "'project docs'" +clean "'${VERSION_COMMAND}ghpagesSynchLocal'" "'${VERSION_COMMAND}ghpagesPushSite'" || exit 1
}

function site-test {
    echo "Not publishing site, because $CI_BRANCH is not 'develop' nor a tag"
    csbt "'project docs'" "'${VERSION_COMMAND}clean'" "'${VERSION_COMMAND}makeSite'" || exit 1
}

function publishScala {
  #copypaste
  if [[ "$CI_PULL_REQUEST" != "false"  ]] ; then
    echo "Skipping publish on pull request"
    return 0
  fi

  if [[ ! -f ./.secrets/credentials.sonatype-nexus.properties ]] ; then
    echo "Skipping publish on missing credentials"
    return 0
  fi

  if [[ ! ("$CI_BRANCH" == "develop" || "$CI_BRANCH_TAG" =~ ^v.*$ ) ]] ; then
    echo "Skipping publish on non-tag / non-develop branch"
    return 0
  fi

  echo "PUBLISH SCALA LIBRARIES..."

  if [[ "$CI_BRANCH_TAG" =~ ^v.*$ ]] ; then
    echo "PUBLISH RELEASE"
    csbt +clean "'${VERSION_COMMAND}package'" "'${VERSION_COMMAND}publishSigned'" sonatypeBundleRelease || exit 1
  else
    echo "PUBLISH SNAPSHOT"
    csbt "'show credentials'" "'${VERSION_COMMAND}clean'" "'${VERSION_COMMAND}package'" "'${VERSION_COMMAND}publishSigned'" || exit 1
  fi
}

function secrets {
    if [[ "$CI_PULL_REQUEST" == "false"  ]] ; then
        echo "Unpacking secrets"
        openssl aes-256-cbc -K ${OPENSSL_KEY} -iv ${OPENSSL_IV} -in secrets.tar.enc -out secrets.tar -d
        tar xvf secrets.tar
        echo "Secrets unpacked"
    else
        echo "Skipping secrets"
    fi
}

function init {
    export SCALA211=$(cat project/Deps.sc | grep 'val scala211 ' |  sed -r 's/.*\"(.*)\".**/\1/')
    export SCALA212=$(cat project/Deps.sc | grep 'val scala212 ' |  sed -r 's/.*\"(.*)\".**/\1/')
    export SCALA213=$(cat project/Deps.sc | grep 'val scala213 ' |  sed -r 's/.*\"(.*)\".**/\1/')
    export SCALA3=$(cat project/Deps.sc | grep 'val scala300 ' |  sed -r 's/.*\"(.*)\".**/\1/')

    # details on github runners: https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources
    export JVM_OPTS="-Xmx6G -XX:ReservedCodeCacheSize=256M -XX:MaxMetaspaceSize=2G"

    printenv

    java -version
}

init


for i in "$@"
do
case $i in
    nothing)
        echo "Doing nothing..."
    ;;

    3)
        scala3
    ;;

    2.13)
        scala213
    ;;

    2.12)
        scala212
    ;;

    2.11)
        scala211
    ;;

    scala-all)
        scalaall
    ;;

    coverage)
        coverage
    ;;

    test)
        test
    ;;

    publishScala)
        publishScala
    ;;

    site-publish)
        site-publish
    ;;

    site-test)
        site-test
    ;;

    secrets)
        secrets
    ;;

    *)
        echo "Unknown option"
        exit 1
    ;;
esac
done
