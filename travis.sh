#!/bin/bash -xe

if [[ "$SYSTEM_PULLREQUEST_PULLREQUESTNUMBER" == ""  ]] ; then
    export TRAVIS_PULL_REQUEST=false
else
    export TRAVIS_PULL_REQUEST=true
fi

export TRAVIS_BRANCH=${BUILD_SOURCEBRANCHNAME}
export TRAVIS_TAG=${BUILD_SOURCEBRANCH}
export NPM_TOKEN=${TOKEN_NPM}
export NUGET_TOKEN=${TOKEN_NUGET}
export CODECOV_TOKEN=${TOKEN_CODECOV}
export TRAVIS_BUILD_NUMBER=${BUILD_BUILDID}
export TRAVIS_COMMIT=${BUILD_SOURCEVERSION}
export USERNAME=${USER:-`whoami`}
git config --global user.name "$USERNAME"
git config --global user.email "$TRAVIS_BUILD_NUMBER@$TRAVIS_COMMIT"

git config --global user.name
git config --global user.email
printenv

function block_open {
    echo -en "travis_fold:start:$1\\r"
}

function block_close {
    echo -en "travis_fold:end:$1\\r"
}

function bopen {
    #block_open ${FUNCNAME[1]}
    :
}

function bclose {
    #block_close ${FUNCNAME[1]}
    :
}

function csbt {
    COMMAND="time sbt -no-colors -v $*"
    eval $COMMAND
}

function versionate {
  bopen
  if [[ "$TRAVIS_BRANCH" != "master" &&  "$TRAVIS_BRANCH" != "develop" && ! ( "$TRAVIS_TAG" =~ ^v.*$ ) ]] ; then
    echo "Setting version suffix to $TRAVIS_BRANCH"
    csbt "\"addVersionSuffix $TRAVIS_BRANCH\""
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
  if [[ "$TRAVIS_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi
  if [[ "$TRAVIS_BRANCH" == "develop" || "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
    echo "Publishing site from branch=$TRAVIS_BRANCH; tag=$TRAVIS_TAG"
    chown -R root:root ~/.ssh
    chmod 600 .secrets/travis-deploy-key
    eval "$(ssh-agent -s)"
    ssh-add .secrets/travis-deploy-key

    csbt clean makeSite ghpagesPushSite || exit 1
  else
    echo "Not publishing site, because $TRAVIS_BRANCH is not 'develop'"
  fi
  bclose
}

function publish {
  bopen
  if [[ "$TRAVIS_PULL_REQUEST" != "false"  ]] ; then
    return 0
  fi

  if [[ ! -f .secrets/credentials.sonatype-nexus.properties ]] ; then
    return 0
  fi

  if [[ ! ("$TRAVIS_BRANCH" == "develop" || "$TRAVIS_TAG" =~ ^v.*$) ]] ; then
    return 0
  fi

  echo "PUBLISH..."
  echo "//registry.npmjs.org/:_authToken=${NPM_TOKEN}" > ~/.npmrc
  npm whoami
  export IZUMI_VERSION=$(cat version.sbt | sed -r 's/.*\"(.*)\".**/\1/' | sed -E "s/SNAPSHOT/build."${TRAVIS_BUILD_NUMBER}"/")
  ./idealingua/idealingua-runtime-rpc-typescript/src/npmjs/publish.sh
  ./idealingua/idealingua-runtime-rpc-c-sharp/src/main/nuget/publish.sh

  csbt clean package publishSigned || exit 1

  if [[ "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
    csbt sonatypeRelease || exit 1
  fi
  bclose
}

function unpack {
    if [[ "$TRAVIS_PULL_REQUEST" == "false"  ]] ; then
        openssl aes-256-cbc -K ${OPENSSL_KEY} -iv ${OPENSSL_IV} -in secrets.tar.enc -out secrets.tar -d
        tar xvf secrets.tar
        ln -s .secrets/local.sbt local.sbt
    fi
}

function info {
  bopen
  ls -la .
  ls -la ~
  bclose
}

info

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

    publish)
        publish
    ;;

    site)
        site
    ;;

    unpack)
        unpack
    ;;

    *)
        echo "Unknown option"
        exit 1
    ;;
esac
done
