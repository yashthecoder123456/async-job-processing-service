resource "digitalocean_project" "main" {
  name        = var.project_name
  description = "Async job processing service infrastructure"
  purpose     = "Web Application"
  environment = "Production"
}

resource "digitalocean_ssh_key" "main" {
  name       = "${var.project_name}-key"
  public_key = file(var.ssh_public_key_path)
}
