#!/bin/bash

############################################################################################
# Thin wrapper for the Jenkins "Execute shell" post-build step.
# The actual release post-build logic is version-controlled in the repository as
# jenkins-release-post-build-script.sh. This wrapper only locates and runs it
# synchronously, failing loudly if the file is missing or its execution fails.
#
# ==> this is what goes into the Jenkins job's post-build "Execute shell" field.
############################################################################################

SCRIPT_NAME="jenkins-release-post-build-script.sh"

if [[ ! -f "${SCRIPT_NAME}" ]]; then
    echo "ERROR: ${SCRIPT_NAME} not found in $(pwd)" >&2
    exit 1
fi

echo "Running ${SCRIPT_NAME}"
bash "${SCRIPT_NAME}"
status=$?

if [[ ${status} -ne 0 ]]; then
    echo "ERROR: ${SCRIPT_NAME} failed with exit code ${status}" >&2
    exit ${status}
fi

echo "${SCRIPT_NAME} completed successfully"
