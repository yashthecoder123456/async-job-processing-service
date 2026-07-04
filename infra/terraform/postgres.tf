resource "digitalocean_database_cluster" "postgres" {
  name       = "${var.project_name}-pg"
  engine     = "pg"
  version    = "16"
  size       = var.postgres_size
  region     = var.region
  node_count = var.postgres_node_count
  private_network_uuid = digitalocean_vpc.main.id
}

resource "digitalocean_database_db" "asyncjobs" {
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = "asyncjobs"
}

resource "digitalocean_database_user" "asyncjobs" {
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = "asyncjobs"
}

resource "digitalocean_project_resources" "postgres" {
  project = digitalocean_project.main.id
  resources = [
    digitalocean_database_cluster.postgres.urn
  ]
}
