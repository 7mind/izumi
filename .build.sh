#!/bin/bash -xe

function scala213() {
  echo "Using Scala 2.13..."
  export CI_SCALA_VERSION="2.13.0"
}

function csbt {
  if [ -z "$CI_SCALA_VERSION" ]; then
      VERSION_COMMAND=""
  else
      VERSION_COMMAND="++$CI_SCALA_VERSION"
  fi
  COMMAND="time sbt -batch -no-colors -v $VERSION_COMMAND $*"
  eval $COMMAND
}

function versionate {
  if [[ "$CI_BRANCH" != "master" &&  "$CI_BRANCH" != "develop" && ! ( "$CI_TAG" =~ ^v.*$ ) ]] ; then
    echo "Setting version suffix to $CI_BRANCH"
    csbt "\"addVersionSuffix $CI_BRANCH\""
  else
    echo "No version suffix required"
  fi
}

function coverage {
  csbt clean coverage test coverageReport || exit 1
  bash <(curl -s https://codecov.io/bash)
}

function scripted {
  csbt clean publishLocal '"scripted sbt-izumi-plugins/*"' || exit 1
}

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

    csbt clean ghpagesPushSite || exit 1
  else
    echo "Not publishing site, because $CI_BRANCH is not 'develop'"
  fi
}

function publishIDL {
  if [[ "$CI_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi

  if [[ ! -f .secrets/credentials.sonatype-nexus.properties ]] ; then
    return 0
  fi

  if [[ ! ("$CI_BRANCH" == "develop" || "$CI_TAG" =~ ^v.*$ ) ]] ; then
    return 0
  fi

  echo "PUBLISH IDL RUNTIMES..."

  echo "//registry.npmjs.org/:_authToken=${NPM_TOKEN}" > ~/.npmrc
  npm whoami
  export IZUMI_VERSION=$(cat version.sbt | sed -r 's/.*\"(.*)\".**/\1/' | sed -E "s/SNAPSHOT/build."${CI_BUILD_NUMBER}"/")
  ./idealingua-v1/idealingua-v1-runtime-rpc-typescript/src/npmjs/publish.sh
  ./idealingua-v1/idealingua-v1-runtime-rpc-c-sharp/src/main/nuget/publish.sh
}

function publishScala {
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

  csbt clean package publishSigned || exit 1

  if [[ "$CI_TAG" =~ ^v.*$ ]] ; then
    csbt sonatypeRelease || exit 1
  fi

}

function init {
    echo "=== INIT ==="
    if [[ "$SYSTEM_PULLREQUEST_PULLREQUESTNUMBER" == ""  ]] ; then
        export CI_PULL_REQUEST=false
    else
        export CI_PULL_REQUEST=true
    fi

    export CI_BRANCH=${BUILD_SOURCEBRANCHNAME}
    export CI_TAG=`git describe --contains | grep v | head -n 1 || true`
    export CI_BUILD_NUMBER=${BUILD_BUILDID}
    export CI_COMMIT=${BUILD_SOURCEVERSION}

    export NPM_TOKEN=${TOKEN_NPM}
    export NUGET_TOKEN=${TOKEN_NUGET}
    export CODECOV_TOKEN=${TOKEN_CODECOV}
    export USERNAME=${USER:-`whoami`}

    printenv

    git config --global user.name "$USERNAME"
    git config --global user.email "$CI_BUILD_NUMBER@$CI_COMMIT"
    git config --global core.sshCommand "ssh -t -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"

    # coursier is not required since sbt 1.3.0
    #mkdir -p ~/.sbt/1.0/plugins/
    #echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M8")' > ~/.sbt/1.0/plugins/build.sbt

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

PARAMS=()
SOFT=0
SKIP=()
for i in "$@"
do
case $i in
    nothing)
        echo "Doing nothing..."
    ;;

    2.13)
        scala213
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

    publishIDL)
        publishIDL
    ;;

    publishScala)
        publishScala
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
