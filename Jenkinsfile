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
                sh 'ps aux | grep mypage | awk \'{ if($15 == "mypage-engine.main") {print "Killing -" $2}}\''
                sh 'ps aux | grep mypage-engine | awk \'{print $1}\' | xargs killl -9 || true'
                sh 'rm -f /run/mypage-engine/mypage-engine-1.0.0-standalone.jar'
                sh 'mv target/mypage-engine-1.0.0-standalone.jar /run/mypage-engine/app'
                sh "nohup java -jar /run/mypage-engine/app/mypage-engine-1.0.0-standalone.jar -m mypage-engine.main --config /run/mypage-engine/config.edn &"
            }
        }
    }
}