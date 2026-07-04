resource "digitalocean_vpc" "main" {
  name     = "${var.project_name}-vpc"
  region   = var.region
  ip_range = "10.10.0.0/16"
}
