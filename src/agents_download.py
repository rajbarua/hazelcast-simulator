#!/usr/bin/env python3

import os

from simulator.log import info
from simulator.util import run_parallel
from simulator.hosts import public_ip
from simulator.remote import remote_for_host


def _agent_download(agent, run_path, run_id):
    info(f"     {public_ip(agent)} Download")

    if run_id == "*":
        dst_path = f"hazelcast-simulator/workers/"
    else:
        dst_path = f"hazelcast-simulator/workers/{run_id}/"

    os.makedirs(run_path, exist_ok=True)
    remote = remote_for_host(agent)
    remote.cp_from(dst_path, run_path, exclude="upload")

    info(f"     {public_ip(agent)} Download completed")


def agents_download(agents, run_path: str, run_id: str):
    info(f"Downloading: starting")
    run_parallel(_agent_download, [(agent, run_path, run_id,) for agent in agents])
    info(f"Downloading: done")
