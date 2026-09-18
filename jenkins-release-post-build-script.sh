#!/bin/bash

############################################################################################
# This script is executed after a successful release build on Jenkins.
# It creates a GitHub release and uploads the build artifacts (JAR files) to the release.
# If the release is a final release (no pre-release/test suffix), it also updates the
# gh-pages branch with the generated JavaDoc.
#
# ==> The script is called from the wrapper script (jenkins-release-post-build-wrapper.sh)
#     after a successful release build. The wrapper script must be copies into the actual
#     post build step on Jenkins.
############################################################################################

owner=`echo ${GIT_URL} | cut -d/ -f4`
echo "Owner: ${owner}"
repo=`echo ${GIT_URL} | cut -d/ -f5`
echo "Repo: ${repo}"
tagName=$(git tag --sort taggerdate | tail -1)
echo "Git tag: ${tagName}"
echo "Creating GitHub release from tag: ${tagName}"

# Only a bare "<name>-<major>.<minor>.<patch>" tag counts as a final release. Any
# suffix (-RC1, -BETA2, -TEST6, ...) is a pre-release/test build and must not
# update the public gh-pages Javadoc - only the GitHub Release entry below.
finalReleaseRegex="^(.*)-([0-9]+\.[0-9]+\.[0-9]+)$"
if [[ ${tagName} =~ $finalReleaseRegex ]]; then
    isFinalRelease=true
    baseName="${BASH_REMATCH[1]}"
    version="${BASH_REMATCH[2]}"
    echo "Final release detected. Base name: ${baseName}, version: ${version}"
else
    isFinalRelease=false
    echo "Tag ${tagName} is not a final release (has a pre-release/test suffix, or does not match the expected <name>-<major>.<minor>.<patch> pattern)"
fi

request="{\"tag_name\":\"${tagName}\",\"name\":\"${tagName}\"}"
echo "Release request: ${request}"

echo "Create github release"
response=$(curl -X POST -u $GITHUB_TOKEN "https://api.github.com/repos/${owner}/${repo}/releases" -d $request)
echo "Github release created"

echo "Response: ${response}"
releaseId=$(echo $response | jq -r .id)
echo "ReleaseId: ${releaseId}"


files="*/target/*.jar"
regex1="original-.*\.jar"
regex2=".*-tests\.jar"
for f in $files
do
    fileName=$(basename ${f})
    if ! [[ ${fileName} =~ ${regex1} ]] && ! [[ ${fileName} =~ ${regex2} ]]
    then
        echo "Uploading ${fileName}"
        uploadResponse=$(curl -s -w "\n%{http_code}" -u $GITHUB_TOKEN -H "Accept: application/vnd.github.manifold-preview" -H "Content-Type: application/zip" --data-binary @${f} "https://uploads.github.com/repos/${owner}/${repo}/releases/${releaseId}/assets?name=${fileName}")
        uploadStatus=$(echo "${uploadResponse}" | tail -n1)
        uploadBody=$(echo "${uploadResponse}" | sed '$d')
        echo "Upload response: ${uploadBody}"
        echo "Uploading ${fileName} done (status=${uploadStatus})"
    fi
done

###########################################################
# Handle gh-pages (JavaDoc) update for final releases only
###########################################################
runGhPagesUpdate=${isFinalRelease}

### >>> TEMPORARY TEST OVERRIDE - also run gh-pages update for -TEST tags; remove after verifying the fix <<<
testReleaseRegex="^(.*)-([0-9]+\.[0-9]+\.[0-9]+)-TEST[0-9]*$"
if [[ ${runGhPagesUpdate} != true ]] && [[ ${tagName} =~ $testReleaseRegex ]]; then
    runGhPagesUpdate=true
    baseName="${BASH_REMATCH[1]}"
    version="${BASH_REMATCH[2]}"
    echo "TEMPORARY OVERRIDE: treating test tag ${tagName} as eligible for gh-pages update (version=${version})"
fi
### >>> END TEMPORARY TEST OVERRIDE <<<

if [[ ${runGhPagesUpdate} == true ]]; then
    echo "Final release ${version}: checkout gh-pages for JavaDoc update"
    mkdir -p target/gh-pages
    git clone https://${GITHUB_TOKEN}@github.com/${owner}/${repo} --branch gh-pages --single-branch target/gh-pages
    mkdir -p target/gh-pages/${version}
    echo "Remove old content for ${version}"
    rm -R target/gh-pages/${version}/* 2> /dev/null

    files="target/checkout/*/target/reports/apidocs"
    for f in $files
    do
        echo "Found JavaDoc in ${f}"
        regex="target/checkout/(.*)/target/reports/apidocs"
        [[ ${f} =~ $regex ]]
        name="${BASH_REMATCH[1]}"
        echo "Name for JavaDoc is ${name}"
        echo "Copy ${f}/* -> target/gh-pages/${version}/${name}/"
        mkdir -p target/gh-pages/${version}/${name}
        cp -R ${f}/* target/gh-pages/${version}/${name}/
        echo "Create index-${name}.html to newest JavaDoc for ${name}"
        echo "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"><html><head><meta http-equiv=Refresh content=\"0;url=${version}/${name}\"></head></html>" > target/gh-pages/index-${name}.html
    done
    echo "Redirect index.html to newest JavaDoc"
    echo "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"><html><head><meta http-equiv=Refresh content=\"0;url=index-njams-sdk.html\"></head></html>" > target/gh-pages/index.html
    cd target/gh-pages
    git add -A
    git commit -m "update javadoc"
    git push origin gh-pages
	echo "JavaDoc pushed to GitHub"
else
    echo "Skipping gh-pages JavaDoc update: ${tagName} is not a final release."
fi
