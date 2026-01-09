#!/usr/bin/env python3

from simulator.log import info
from simulator.util import run_parallel
from simulator.hosts import public_ip
from simulator.remote import remote_for_host

def _agent_clear(agent):
    info(f"     {public_ip(agent)} Clearing agent")
    remote = remote_for_host(agent)
    remote.exec(f"rm -fr hazelcast-simulator/workers/*")


def agents_clean(agents):
    info(f"Clearing agents: starting")
    run_parallel(_agent_clear, [(agent,) for agent in agents])
    info(f"Clearing agents: done")
