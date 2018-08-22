#!/usr/bin/env bash -xe

export SOURCEDIR=$(pwd)
export CNAME="izumi-build-$(date +%s)"
export IMAGE="septimalmind/izumi-env:7c81ec8403e16fef66234822d42f56512389fbfc"
export RESOURCEDIR="$( cd "$(dirname "$0")" ; pwd -P )"
export HOMEDIR="$( cd ~ ; pwd -P )"

docker pull $IMAGE

docker run --rm --name $CNAME \
    -e TRAVIS_SCALA_VERSION=$TRAVIS_SCALA_VERSION \
    -e TRAVIS_BRANCH=$TRAVIS_BRANCH \
    -e TRAVIS_TAG=$TRAVIS_TAG \
    -e TRAVIS_PULL_REQUEST=$TRAVIS_PULL_REQUEST \
    --volume "${SOURCEDIR}":/work:z \
    --volume "${HOMEDIR}":/root:z \
    $IMAGE bash -xe travis.sh $1

    #--volume $SSH_AUTH_SOCK:/ssh-agent --env SSH_AUTH_SOCK=/ssh-agent \
    #--volume $(cat $SOURCEDIR/.git/objects/info/alternates):/work/.git/objects:z \
