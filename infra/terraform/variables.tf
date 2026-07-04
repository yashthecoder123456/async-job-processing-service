variable "do_token" {
  type      = string
  sensitive = true
}

variable "region" {
  type    = string
  default = "nyc3"
}

variable "project_name" {
  type    = string
  default = "async-job-processing-service"
}

variable "ssh_public_key_path" {
  type    = string
  default = "~/.ssh/id_rsa.pub"
}

variable "ssh_allowed_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

variable "postgres_size" {
  type    = string
  default = "db-s-1vcpu-1gb"
}

variable "postgres_node_count" {
  type    = number
  default = 1
}

variable "droplet_size" {
  type    = string
  default = "s-1vcpu-1gb"
}

variable "worker_count" {
  type    = number
  default = 2
}

variable "docker_image" {
  type    = string
  default = "ghcr.io/your-org/async-job-processing-service:latest"
}

variable "rabbitmq_username" {
  type    = string
  default = "asyncjobs"
}

variable "rabbitmq_password" {
  type      = string
  sensitive = true
}

variable "app_env" {
  type    = string
  default = "prod"
}
