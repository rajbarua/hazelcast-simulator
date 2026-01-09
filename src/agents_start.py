#!/usr/bin/env python3

import yaml
import sys

from simulator.log import info
from simulator.util import run_parallel
from simulator.hosts import public_ip
from simulator.remote import remote_for_host


def __start_agent(agent):
    info(f"     {public_ip(agent)} starting")
    remote = remote_for_host(agent)
    agent_start = "hazelcast-simulator/bin/hidden/agent_start"
    agent_port = agent.get("agent_port", "9000")
    remote.exec(f"{agent_start} {agent['agent_index']} {public_ip(agent)} {agent_port}")


agents_yaml = yaml.safe_load(sys.argv[1])
info(f"Starting agents")
run_parallel(__start_agent, [(agent,) for agent in agents_yaml])
info(f"Starting agents: done")
