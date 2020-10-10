pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                echo 'Building..'
                sh 'rm -rf target; lein do clean; lein uberjar'
                echo 'Done building'
            }
        }
        stage('Lint') {
                    steps {
                        echo 'Linting..'
                        sh "lein lint"
                    }
                }
        stage('Test') {
            steps {
                echo 'Testing..'
                sh "lein test"
            }
        }
        stage("Deploy") {
            steps {
                echo 'Deploy!'
                sh 'rm -f /production-area/mypage-engine/mypage-engine-1.0.0-standalone.jar'
                sh 'mv target/mypage-engine-1.0.0-standalone.jar /production-area/mypage-engine/app'
            }
        }
    }
    post {
            always {
                sh "sudo systemctl restart mypage-engine"
            }
         }
}