output "project_id" {
  value = digitalocean_project.main.id
}

output "vpc_id" {
  value = digitalocean_vpc.main.id
}

output "postgres_host" {
  value = digitalocean_database_cluster.postgres.host
}

output "postgres_port" {
  value = digitalocean_database_cluster.postgres.port
}

output "postgres_database" {
  value = digitalocean_database_db.asyncjobs.name
}

output "postgres_user" {
  value = digitalocean_database_user.asyncjobs.name
}

output "postgres_password" {
  value     = digitalocean_database_user.asyncjobs.password
  sensitive = true
}

output "rabbitmq_droplet_ip" {
  value = digitalocean_droplet.rabbitmq.ipv4_address
}

output "api_droplet_ip" {
  value = digitalocean_droplet.api.ipv4_address
}

output "dispatcher_droplet_ip" {
  value = digitalocean_droplet.dispatcher.ipv4_address
}

output "worker_droplet_ips" {
  value = digitalocean_droplet.workers[*].ipv4_address
}
