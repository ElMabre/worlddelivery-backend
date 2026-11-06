#!/usr/bin/env bash
set -euo pipefail
# Run manually on Linux after mvn package, from a checkout containing the JARs.
[[ ${EUID} -eq 0 ]] || { echo 'Run with sudo on the target Linux host.' >&2; exit 1; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$script_dir/../.." && pwd)
for service in ms-pedidos ms-operaciones ms-rabbit-admin ms-usuarios; do
  [[ -f "$repo_dir/$service/target/$service-0.0.1-SNAPSHOT.jar" ]] || { echo "Missing JAR: $service" >&2; exit 1; }
  if systemctl is-active --quiet "$service"; then
    echo "Stop $service before replacing its JAR." >&2; exit 1
  fi
done
id worlddelivery >/dev/null 2>&1 || useradd --system --home-dir /var/lib/worlddelivery --shell /usr/sbin/nologin worlddelivery
install -d -m 755 /opt/worlddelivery
install -d -m 700 /etc/worlddelivery
install -d -o worlddelivery -g worlddelivery -m 750 /var/lib/worlddelivery
for service in ms-pedidos ms-operaciones ms-rabbit-admin ms-usuarios; do
  install -d -o worlddelivery -g worlddelivery -m 750 "/var/lib/worlddelivery/$service"
  if [[ -f "/opt/worlddelivery/$service.jar" ]]; then
    cp -- "/opt/worlddelivery/$service.jar" "/opt/worlddelivery/$service.jar.previous"
  fi
  install -m 644 "$repo_dir/$service/target/$service-0.0.1-SNAPSHOT.jar" "/opt/worlddelivery/$service.jar"
  install -m 644 "$script_dir/systemd/$service.service" "/etc/systemd/system/$service.service"
  if [[ ! -f "/etc/worlddelivery/$service.env" ]]; then
    install -m 600 "$script_dir/env/$service.env.example" "/etc/worlddelivery/$service.env"
  fi
done
systemctl daemon-reload
echo 'Installed. Complete /etc/worlddelivery/*.env before enabling services.'
