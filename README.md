#Hello 
A backend service for my webpage 📯

## Backend as a Systemd service

Systemd is used to control the server.

Inside /etc/systemd/system/mypage-engine.service is the systemd configuration file located with the following content:

    [Unit]
    Description=Mypage engine, backend service for my page!
    After=network.target
    StartLimitIntervalSec=0

    [Service]
    Type=simple
    Restart=always
    RestartSec=1
    User=jenkins
    ExecStart=/usr/bin/env java -jar /production-area/mypage-engine/app/mypage-engine-1.0.0-standalone.jar -m mypage-engine.main --config /production-area/mypage-engine/config.edn

    [Install]
    WantedBy=multi-user.target

To start the service:

    systemctl start mypage-eengine

To enable the service on reboot:
    
    systemctl enable mypage-engine

The crux on running systemctl without the ability to enter a password!
    
    sudo su
    cd /etc/sudoers.d
    touch mypage-engine
    visduo mypage-engine
    ...
    jenkins ALL=NOPASSWD: /usr/bin/systemctl restart mypage-engine
    
With this, the Jenkins file can be simple and don't need to start and stop stuff. We also enable restarting the service when the computer dies. 

