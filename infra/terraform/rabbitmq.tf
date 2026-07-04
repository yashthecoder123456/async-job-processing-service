resource "digitalocean_droplet" "rabbitmq" {
  name     = "${var.project_name}-rabbitmq"
  region   = var.region
  size     = var.droplet_size
  image    = "docker-24-04"
  vpc_uuid = digitalocean_vpc.main.id
  ssh_keys = [digitalocean_ssh_key.main.id]

  user_data = templatefile("${path.module}/cloud-init/rabbitmq.yaml.tpl", {
    rabbitmq_username = var.rabbitmq_username
    rabbitmq_password = var.rabbitmq_password
  })
}

resource "digitalocean_project_resources" "rabbitmq" {
  project = digitalocean_project.main.id
  resources = [
    digitalocean_droplet.rabbitmq.urn
  ]
}
