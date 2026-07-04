#cloud-config
package_update: true
packages:
  - docker.io
runcmd:
  - systemctl enable docker
  - systemctl start docker
