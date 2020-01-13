#!/bin/bash -xe

# `++ 2.13.0 compile` has a different semantic than `;++2.13.0;compile`
# Strict aggregation applies ONLY to former, and ONLY if crossScalaVersions := Nil in root project
# see https://github.com/sbt/sbt/issues/3698#issuecomment-475955454
# and https://github.com/sbt/sbt/pull/3995/files
# TL;DR strict aggregation in sbt is broken; this is a workaround

function scala213 {
  echo "Using Scala 2.13..."
  VERSION_COMMAND="++ $SCALA213"
}

function scala212 {
  echo "Using Scala 2.12..."
  VERSION_COMMAND="++ $SCALA212"
}

function csbt {
  COMMAND="time sbt -Dsbt.ivy.home=$IVY_CACHE_FOLDER -Divy.home=$IVY_CACHE_FOLDER -Dcoursier.cache=$COURSIER_CACHE -batch -no-colors -v $*"
  eval $COMMAND
}

# function versionate {
#   if [[ "$CI_BRANCH" != "master" &&  "$CI_BRANCH" != "develop" && ! ( "$CI_TAG" =~ ^v.*$ ) ]] ; then
#     echo "Setting version suffix to $CI_BRANCH"
#     csbt "\"addVersionSuffix $CI_BRANCH\""
#   else
#     echo "No version suffix required"
#   fi
# }

function coverage {
  csbt clean coverage "'$VERSION_COMMAND test'" "'$VERSION_COMMAND coverageReport'" || exit 1
  bash <(curl -s https://codecov.io/bash)
}

# function scripted {
#   csbt clean publishLocal '"scripted sbt-izumi-plugins/*"' || exit 1
# }

function site {
  if [[ "$CI_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi
  if [[ "$CI_BRANCH" == "develop" || "$CI_TAG" =~ ^v.*$ ]] ; then
    echo "Publishing site from branch=$CI_BRANCH; tag=$CI_TAG"
    chown -R root:root ~/.ssh
    chmod 600 .secrets/travis-deploy-key
    eval "$(ssh-agent -s)"
    ssh-add .secrets/travis-deploy-key

    csbt +clean doc/ghpagesSynchLocal doc/ghpagesPushSite || exit 1
  else
    echo "Not publishing site, because $CI_BRANCH is not 'develop'"
  fi
}

function publishIDL {
  #copypaste
  if [[ "$CI_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi

  if [[ ! -f .secrets/credentials.sonatype-nexus.properties ]] ; then
    return 0
  fi

  if [[ ! ("$CI_BRANCH" == "develop" || "$CI_TAG" =~ ^v.*$ ) ]] ; then
    return 0
  fi
  #copypaste

  echo "PUBLISH IDL RUNTIMES..."

  echo "//registry.npmjs.org/:_authToken=${NPM_TOKEN}" > ~/.npmrc
  npm whoami

  ./idealingua-v1/idealingua-v1-runtime-rpc-typescript/src/npmjs/publish.sh || exit 1
  ./idealingua-v1/idealingua-v1-runtime-rpc-csharp/src/main/nuget/publish.sh || exit 1
}

function publishScala {
  #copypaste
  if [[ "$CI_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi

  if [[ ! -f .secrets/credentials.sonatype-nexus.properties ]] ; then
    return 0
  fi

  if [[ ! ("$CI_BRANCH" == "develop" || "$CI_TAG" =~ ^v.*$ ) ]] ; then
    return 0
  fi

  echo "PUBLISH SCALA LIBRARIES..."

  if [[ "$CI_BRANCH" == "develop" ]] ; then
    csbt "'$VERSION_COMMAND clean'" "'$VERSION_COMMAND package'" "'$VERSION_COMMAND publishSigned'" || exit 1
  else
    csbt "'$VERSION_COMMAND clean'" "'$VERSION_COMMAND package'" "'$VERSION_COMMAND publishSigned'" sonatypeBundleRelease || exit 1
  fi
}

function init {
    echo "=== INIT ==="
    export LC_ALL="C.UTF-8"
    
    if [[ "$SYSTEM_PULLREQUEST_PULLREQUESTNUMBER" == ""  ]] ; then
        export CI_PULL_REQUEST=false
    else
        export CI_PULL_REQUEST=true
    fi

    export CI_BRANCH=${BUILD_SOURCEBRANCHNAME}
    export CI_TAG=`git describe --contains | grep v | grep -v '~' | head -n 1 || true`
    export CI_BUILD_NUMBER=${BUILD_BUILDID}
    export CI_COMMIT=${BUILD_SOURCEVERSION}

    export NPM_TOKEN=${TOKEN_NPM}
    export NUGET_TOKEN=${TOKEN_NUGET}
    export CODECOV_TOKEN=${TOKEN_CODECOV}
    export USERNAME=${USER:-`whoami`}
    export COURSIER_CACHE=${COURSIER_CACHE:-`~/.coursier`}
    export IVY_CACHE_FOLDER=${IVY_CACHE_FOLDER:-`~/.ivy2`}

    export IZUMI_VERSION=$(cat version.sbt | sed -r 's/.*\"(.*)\".**/\1/' | sed -E "s/SNAPSHOT/build."${CI_BUILD_NUMBER}"/")
    export SCALA212=$(cat project/Deps.sc | grep 'val scala212 ' |  sed -r 's/.*\"(.*)\".**/\1/')
    export SCALA213=$(cat project/Deps.sc | grep 'val scala213 ' |  sed -r 's/.*\"(.*)\".**/\1/')

    printenv

    git config --global user.name "$USERNAME"
    git config --global user.email "$CI_BUILD_NUMBER@$CI_COMMIT"
    git config --global core.sshCommand "ssh -t -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"

    echo "pwd: `pwd`"
    echo "Current directory:"
    ls -la .
    echo "Home:"
    ls -la ~

    echo "=== END ==="
}

function secrets {
    if [[ "$CI_PULL_REQUEST" == "false"  ]] ; then
        openssl aes-256-cbc -K ${OPENSSL_KEY} -iv ${OPENSSL_IV} -in secrets.tar.enc -out secrets.tar -d
        tar xvf secrets.tar
        ln -s .secrets/local.sbt local.sbt
    fi
}

init


for i in "$@"
do
case $i in
    nothing)
        echo "Doing nothing..."
    ;;

    2.13)
        scala213
    ;;

    2.12)
        scala212
    ;;

    # versionate)
    #     versionate
    # ;;

    coverage)
        coverage
    ;;

    # scripted)
    #     scripted
    # ;;

    publishIDL)
        publishIDL
    ;;

    publishScala)
        publishScala
    ;;

    sonatypeRelease)
        sonatypeRelease
    ;;

    site)
        site
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
