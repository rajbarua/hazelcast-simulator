#!/usr/bin/env python3
import os.path
import sys
import yaml

from simulator.hosts import public_ip, agent_index
from simulator.log import info
from simulator.remote import remote_for_host
from simulator.util import run_parallel


def _exitcode(result):
    return result if isinstance(result, int) else result[0]


def prepare_run_dir(agent):
    remote = remote_for_host(agent)
    remote.exec(f"""
        rm -fr {target_dir}
        mkdir -p {target_dir}
        """)


def upload(agent):
    remote = remote_for_host(agent)
    remote.cp_to(upload_dir, target_dir)


def start_dstat(agent):
    remote = remote_for_host(agent)
    dstat_cmd = None
    check = remote.exec("command -v dstat >/dev/null 2>&1", silent=True, fail_on_error=False)
    if _exitcode(check) == 0:
        dstat_cmd = "dstat"
    else:
        check = remote.exec("command -v pcp-dstat >/dev/null 2>&1", silent=True, fail_on_error=False)
        if _exitcode(check) == 0:
            dstat_cmd = "pcp-dstat"
    if not dstat_cmd:
        info(f"     {public_ip(agent)} dstat not found; skipping")
        return
    remote.exec(f"""
            if command -v killall >/dev/null 2>&1; then
                killall -9 dstat || true
            elif command -v pkill >/dev/null 2>&1; then
                pkill -9 dstat || true
            fi
            nohup {dstat_cmd} --epoch -m --all -l --noheaders --nocolor --output {target_dir}/A{agent_index(agent)}_dstat.csv 1 > /dev/null 2>&1 &
            sleep 1
            """)


upload_dir = sys.argv[1]
run_id = sys.argv[2]
agents_yaml = yaml.safe_load(sys.argv[3])
target_dir = f"hazelcast-simulator/workers/{run_id}"

run_parallel(prepare_run_dir, [(agent,) for agent in agents_yaml])

if os.path.exists(upload_dir):
    run_parallel(upload, [(agent,) for agent in agents_yaml])

run_parallel(start_dstat, [(agent,) for agent in agents_yaml])
