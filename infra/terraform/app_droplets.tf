resource "digitalocean_droplet" "api" {
  name     = "${var.project_name}-api"
  region   = var.region
  size     = var.droplet_size
  image    = "docker-24-04"
  vpc_uuid = digitalocean_vpc.main.id
  ssh_keys = [digitalocean_ssh_key.main.id]

  user_data = templatefile("${path.module}/cloud-init/app-node.yaml.tpl", {
    docker_image = var.docker_image
    app_env      = var.app_env
  })
}

resource "digitalocean_droplet" "dispatcher" {
  name     = "${var.project_name}-dispatcher"
  region   = var.region
  size     = var.droplet_size
  image    = "docker-24-04"
  vpc_uuid = digitalocean_vpc.main.id
  ssh_keys = [digitalocean_ssh_key.main.id]

  user_data = templatefile("${path.module}/cloud-init/app-node.yaml.tpl", {
    docker_image = var.docker_image
    app_env      = var.app_env
  })
}

resource "digitalocean_droplet" "workers" {
  count    = var.worker_count
  name     = "${var.project_name}-worker-${count.index + 1}"
  region   = var.region
  size     = var.droplet_size
  image    = "docker-24-04"
  vpc_uuid = digitalocean_vpc.main.id
  ssh_keys = [digitalocean_ssh_key.main.id]

  user_data = templatefile("${path.module}/cloud-init/app-node.yaml.tpl", {
    docker_image = var.docker_image
    app_env      = var.app_env
  })
}

resource "digitalocean_project_resources" "app_droplets" {
  project = digitalocean_project.main.id
  resources = concat(
    [digitalocean_droplet.api.urn, digitalocean_droplet.dispatcher.urn],
    digitalocean_droplet.workers[*].urn
  )
}
