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
        stage('Test') {
            steps {
                echo 'Testing..'
            }
        }

        stage("Deploy") {
            steps {
                echo 'Deploy!'
                sh 'ps aux | grep mypage'
                sh 'ps aux | grep mypage | awk \'{ if($15 == "mypage-engine.main") {print "Kill - " $2}}\''
                sh 'ps aux | grep mypage-engine | awk \'{print $1}\' | xargs kill -9 || true'
                sh 'rm -f /production-area/mypage-engine/mypage-engine-1.0.0-standalone.jar'
                sh 'mv target/mypage-engine-1.0.0-standalone.jar /production-area/mypage-engine/app'
            }
        }
    }
    post {
            always {
                sh "JENKINS_NODE_COOKIE=dontKillMe nohup java -jar /production-area/mypage-engine/app/mypage-engine-1.0.0-standalone.jar -m mypage-engine.main --config /run/mypage-engine/config.edn &"
            }
         }
}