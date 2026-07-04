resource "digitalocean_firewall" "app" {
  name = "${var.project_name}-fw"

  droplet_ids = concat(
    [digitalocean_droplet.api.id, digitalocean_droplet.dispatcher.id, digitalocean_droplet.rabbitmq.id],
    digitalocean_droplet.workers[*].id
  )

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = [var.ssh_allowed_cidr]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "8080"
    source_addresses = [var.ssh_allowed_cidr]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "5672"
    source_addresses = ["10.10.0.0/16"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "15672"
    source_addresses = [var.ssh_allowed_cidr]
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "udp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}
