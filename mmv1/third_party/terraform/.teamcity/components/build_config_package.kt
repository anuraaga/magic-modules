/*
 * Copyright (c) HashiCorp, Inc.
 * SPDX-License-Identifier: MPL-2.0
 */

// this file is copied from mmv1, any changes made here will be overwritten

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.AbsoluteId

class packageDetails(packageName: String, displayName: String, providerName: String, environment: String) {
    val packageName = packageName
    val displayName = displayName
    val providerName = providerName
    val environment = environment

    // buildConfiguration returns a BuildType for a service package
    // For BuildType docs, see https://teamcity.jetbrains.com/app/dsl-documentation/root/build-type/index.html
    fun buildConfiguration(path: String, manualVcsRoot: AbsoluteId, parallelism: Int, triggerConfig: NightlyTriggerConfiguration, timeout: Int = defaultTimeoutDuration) : BuildType {
        return BuildType {
            // TC needs a consistent ID for dynamically generated packages
            id(uniqueID())

            name = "%s - Acceptance Tests".format(displayName)

            vcs {
                root(rootId = manualVcsRoot)
                cleanCheckout = true
            }

            steps {
                SetGitCommitBuildId()
                ConfigureGoEnv()
                DownloadTerraformBinary()
                RunAcceptanceTests()
            }

            failureConditions {
                errorMessage = true
            }

            features {
                Golang()
            }

            params {
                TerraformAcceptanceTestParameters(parallelism, "TestAcc", "12", "", "")
                TerraformAcceptanceTestsFlag()
                TerraformCoreBinaryTesting()
                TerraformShouldPanicForSchemaErrors()
                ReadOnlySettings()
                WorkingDirectory(path, packageName)
            }

            triggers {
                RunNightly(triggerConfig)
            }

            dependencies {
                // Acceptance tests need the Pre-Sweeper step to have completed first
                snapshot(RelativeId(preSweeperBuildConfigId)) {
                    reuseBuilds = ReuseBuilds.ANY
                    onDependencyFailure = FailureAction.IGNORE
                    onDependencyCancel = FailureAction.ADD_PROBLEM
                }
            }

            failureConditions {
                executionTimeoutMin = timeout
            }
        }
    }

    fun uniqueID() : String {
        // Replacing chars can be necessary, due to limitations on IDs
        // "ID should start with a latin letter and contain only latin letters, digits and underscores (at most 225 characters)." 
        var pv = this.providerName.replace("-", "").toUpperCase()
        var env = this.environment.toUpperCase().replace("-", "").replace(".", "").toUpperCase()
        var pkg = this.packageName.toUpperCase()

        return "%s_SERVICE_%s_%s".format(pv, env, pkg)
    }
}
