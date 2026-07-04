#cloud-config
package_update: true
packages:
  - docker.io
runcmd:
  - systemctl enable docker
  - systemctl start docker
  - docker run -d --name rabbitmq --restart unless-stopped -p 5672:5672 -p 15672:15672 \
      -e RABBITMQ_DEFAULT_USER=${rabbitmq_username} \
      -e RABBITMQ_DEFAULT_PASS=${rabbitmq_password} \
      rabbitmq:3-management
