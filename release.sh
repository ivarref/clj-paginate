#!/bin/bash

set -ex

git update-index --refresh
git diff-index --quiet HEAD --

clojure -Spom
clojure -X:test
clojure -M:jar
clojure -X:release ivarref.pom-patch/clojars-repo-only!

LAST_TAG="$(git rev-list --tags --no-walk --max-count=1)"
COMMITS_SINCE_LAST_TAG="$(git rev-list "$LAST_TAG"..HEAD --count)"
echo "Squashing $COMMITS_SINCE_LAST_TAG commits ..."
git reset --soft HEAD~"$COMMITS_SINCE_LAST_TAG"
MSG="$(git log --format=%B --reverse HEAD..HEAD@{1})"
git commit -m"$MSG"

VERSION="$(clojure -X:release ivarref.pom-patch/set-patch-version! :patch :commit-count)"
echo "Releasing $VERSION"
sed -i "s/HEAD/v$VERSION/g" ./README.md
git add pom.xml README.md
git commit -m "Release $VERSION"
git reset --soft HEAD~2
git commit -m"Release $VERSION
$MSG"

git tag -a v"$VERSION" -m "Release v$VERSION
$MSG"
git push --follow-tags --force

clojure -X:deploy
echo "Released $VERSION"
