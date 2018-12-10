try {
    node('java8') {
        stage ('Setup') {
            checkout([
                    $class: 'GitSCM',
                    branches: scm.branches,
                    extensions: [[$class: 'CleanBeforeCheckout']],
                    userRemoteConfigs: scm.userRemoteConfigs
            ])

            prepareMaven()

            env.POM_VERSION_WITHOUT_SNAPSHOT = getPOMVersionWithoutSnapshot()
            env.VERSION = "${env.POM_VERSION_WITHOUT_SNAPSHOT}-${env.BUILD_NUMBER}"

            echo "Building version: ${env.VERSION}"
            echo "Building branch: ${env.BRANCH_NAME}"
        }

        stage ('Build') {
            invokeMaven("org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${env.VERSION} -DgenerateBackupPoms=false")
            invokeMaven("clean deploy")
        }
    }
}
catch (any) {
    if (currentBuild.result != 'ABORTED') {
        currentBuild.result = 'FAILURE'
        throw any
    }
}
finally {
    if (currentBuild.result == 'NOT_BUILT') {
        currentBuild.result = 'SUCCESS'
    }
    if (currentBuild.result != 'ABORTED') {
        node {
            step([$class: 'Mailer', notifyEveryUnstableBuild: true,
                  recipients: 'tgmuender@inventage.com',
                  sendToIndividuals: false])
        }
    }
}

def prepareMaven() {
    env.PATH = "${tool 'Maven 3.5.x Latest'}/bin:${env.PATH}"
}

def pomVersion(pom='pom.xml') {
    def model = readMavenPom(file: pom)
    return model.getVersion() ?: model.getParent().getVersion()
}

def getPOMVersionWithoutSnapshot() {
    def version = pomVersion()

    if (version.endsWith('-SNAPSHOT')) {
        return version[0..-10]
    }
    else {
        return version
    }
}

String invokeMaven(args) {
    configFileProvider([configFile(fileId: 'settings.xml', targetLocation: "${env.WORKSPACE}/mvn-settings.xml")]) {
        return sh(
                script:  "mvn --settings ${env.WORKSPACE}/mvn-settings.xml --show-version --batch-mode ${args}",
                returnStdout: true
        )
    }
}
