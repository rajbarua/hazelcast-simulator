#!/usr/bin/env python3
import os.path
import subprocess
import sys
from typing import Optional, List, Dict

import yaml
from yaml import dump
from simulator.util import exit_with_error

default_inventory_path = 'inventory.yaml'


def find_host_with_public_ip(hosts, public_ip):
    for host in hosts:
        if host["public_ip"] == public_ip:
            return host
    return


def _normalize_host(host: Dict) -> Dict:
    normalized = dict(host)
    if not normalized.get("public_ip"):
        raise Exception(f"Could not find public_ip in host entry {host}")
    if not normalized.get("ssh_user"):
        normalized["ssh_user"] = "simulator"
    if not normalized.get("ssh_options"):
        normalized["ssh_options"] = "-i key -o StrictHostKeyChecking=no -o ConnectTimeout=60"
    if not normalized.get("agent_port"):
        normalized["agent_port"] = "9000"
    return normalized


def _parse_hosts_from_yaml(inventory_yaml) -> List[Dict]:
    result = []
    # Simulator inventory.yaml structure: top-level groups with hosts
    if isinstance(inventory_yaml, dict) and not inventory_yaml.get("all"):
        for group_name, group in inventory_yaml.items():
            if not isinstance(group, dict):
                continue
            hosts_section = group.get("hosts")
            if not hosts_section:
                continue
            if isinstance(hosts_section, dict):
                for hostname, host in hosts_section.items():
                    if isinstance(host, dict):
                        host = {"public_ip": hostname, **host}
                    else:
                        host = {"public_ip": hostname}
                    host["groupname"] = group_name
                    result.append(_normalize_host(host))
            elif isinstance(hosts_section, list):
                for host in hosts_section:
                    if isinstance(host, dict):
                        if not host.get("public_ip"):
                            continue
                        host["groupname"] = group_name
                        result.append(_normalize_host(host))
                    elif isinstance(host, str):
                        result.append(_normalize_host({"public_ip": host, "groupname": group_name}))
        if result:
            return result
    # Ansible-style structure
    if isinstance(inventory_yaml, dict) and inventory_yaml.get("all") and inventory_yaml["all"].get("children"):
        children = inventory_yaml["all"]["children"]
        for group_name, group in children.items():
            hosts = group.get("hosts") or {}
            for hostname, host in hosts.items():
                new_host = _normalize_host({
                    "public_ip": hostname,
                    "private_ip": host.get("private_ip"),
                    "ssh_user": host.get("ansible_user"),
                    "groupname": group_name,
                })
                private_key = host.get("ansible_ssh_private_key_file")
                if private_key:
                    new_host["ssh_options"] = f"-i {private_key} -o StrictHostKeyChecking=no -o ConnectTimeout=60"
                # pass through custom fields (e.g. kubectl)
                for key, value in host.items():
                    if key in ["private_ip", "ansible_user", "ansible_ssh_private_key_file"]:
                        continue
                    new_host[key] = value
                result.append(_normalize_host(new_host))
    elif isinstance(inventory_yaml, list):
        # Simple list of host dicts
        for host in inventory_yaml:
            result.append(_normalize_host(host))
    elif isinstance(inventory_yaml, dict) and inventory_yaml.get("hosts"):
        hosts_section = inventory_yaml["hosts"]
        if isinstance(hosts_section, dict):
            for hostname, host in hosts_section.items():
                if isinstance(host, dict):
                    host = {"public_ip": hostname, **host}
                result.append(_normalize_host(host))
        elif isinstance(hosts_section, list):
            for host in hosts_section:
                result.append(_normalize_host(host))
    return result


def _filter_hosts(hosts: List[Dict], host_pattern: str) -> List[Dict]:
    if not host_pattern:
        return hosts

    tokens = [token for token in host_pattern.split(":") if token]
    selected_ids = set()
    host_id_order = []

    def host_id(h):
        return f"{h.get('public_ip')}|{h.get('groupname')}"

    # If no include token, default to all
    has_include = any(not t.startswith("!") for t in tokens)
    if not tokens or not has_include:
        for h in hosts:
            hid = host_id(h)
            selected_ids.add(hid)
            host_id_order.append(hid)

    for token in tokens:
        exclude = token.startswith("!")
        name = token[1:] if exclude else token
        matching = []
        if name == "all":
            matching = hosts
        else:
            matching = [h for h in hosts if h.get("groupname") == name]

        if exclude:
            for h in matching:
                hid = host_id(h)
                if hid in selected_ids:
                    selected_ids.remove(hid)
        else:
            for h in matching:
                hid = host_id(h)
                if hid not in selected_ids:
                    selected_ids.add(hid)
                    host_id_order.append(hid)

    filtered = []
    for h in hosts:
        hid = host_id(h)
        if hid in selected_ids:
            filtered.append(h)
    return filtered


def load_hosts(inventory_path=None, host_pattern: str = "all"):
    if not inventory_path:
        inventory_path = default_inventory_path

    if not os.path.exists(inventory_path):
        exit_with_error(f"Could not find [{inventory_path}]")

    # First attempt to parse the yaml directly (works for both ansible-style and simple lists)
    try:
        with open(inventory_path) as f:
            inventory_yaml = yaml.safe_load(f)
        hosts = _parse_hosts_from_yaml(inventory_yaml)
        if hosts:
            return _filter_hosts(hosts, host_pattern)
    except Exception:
        # fall back to ansible below
        pass

    # Legacy path: rely on ansible to interpret inventory + patterns
    cmd = f"ansible  -i {inventory_path}  --list-hosts  '{host_pattern}'"
    lines = subprocess.run(cmd, shell=True, check=True, capture_output=True, text=True).stdout.splitlines()

    # first line contains 'hosts ...'. We don't want it.
    lines.pop(0)
    desired_hosts = []
    for line in lines:
        desired_hosts.append(line.strip())
    cmd = f"ansible-inventory -i {inventory_path} -y --list"
    out = subprocess.run(cmd, shell=True, check=True, capture_output=True, text=True).stdout

    inventory_yaml = yaml.safe_load(out)
    result = []
    children = inventory_yaml['all']['children']
    for group_name, group in children.items():
        hosts = group.get('hosts')
        if hosts:
            for hostname, host in hosts.items():
                if not hostname in desired_hosts:
                    continue

                new_host = {
                    'public_ip': hostname,
                    'private_ip': host.get('private_ip'),
                    'ssh_user': host.get('ansible_user'),
                    'groupname': group_name
                }
                private_key = host.get("ansible_ssh_private_key_file")
                if private_key:
                    new_host['ssh_options'] = f"-i {private_key} -o StrictHostKeyChecking=no -o ConnectTimeout=60"
                for key, value in host.items():
                    if key in ['private_ip', 'ansible_user', 'ansible_ssh_private_key_file']:
                        continue
                    new_host[key] = value
                new_host = _normalize_host(new_host)
                result.append(new_host)
    return result


if __name__ == '__main__':
    inventory = load_hosts(host_pattern=sys.argv[1])
    print(dump(inventory))
