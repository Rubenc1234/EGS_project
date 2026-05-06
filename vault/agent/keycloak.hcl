vault {
  address = "http://vault:8200"
}

auto_auth {
  method "approle" {
    config = {
      role_id_file_path = "/vault/role_id"
      secret_id_file_path = "/vault/secret_id"
    }
  }
  sink "file" {
    config = {
      path = "/vault/token"
    }
  }
}

template {
  source = "/vault/templates/keycloak.env.tmpl"
  destination = "/vault/secrets/keycloak.env"
  perms = "0644"
}

exit_after_auth = false
pid_file = "/vault/agent.pid"
