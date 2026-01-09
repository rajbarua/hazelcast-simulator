import os
import subprocess
from typing import Tuple, Union
from simulator.ssh import Ssh
from simulator.util import shell


class Remote:
    """
    Abstracts how we talk to an agent. Implementations should mirror the
    interface exposed by simulator.ssh.Ssh to minimize call-site churn.
    """

    def connect(self, check: bool = True) -> int:
        return 0

    def exec(self, command: str, silent: bool = False, fail_on_error: bool = True) -> Union[int, Tuple[int, str]]:
        raise NotImplementedError()

    def cp_to(self, src: str, dst: str):
        raise NotImplementedError()

    def cp_from(self, src: str, dst: str, exclude: str = None):
        raise NotImplementedError()


class SshRemote(Remote):
    def __init__(self, host: dict):
        self.host = host
        self.ssh = Ssh(host["public_ip"], host.get("ssh_user"), host.get("ssh_options"))

    def connect(self, check: bool = True) -> int:
        return self.ssh.connect(check=check)

    def exec(self, command: str, silent: bool = False, fail_on_error: bool = True) -> Union[int, Tuple[int, str]]:
        result = self.ssh.exec(command, silent=silent, fail_on_error=fail_on_error)
        if isinstance(result, tuple):
            return result
        return result, ""

    def cp_to(self, src: str, dst: str):
        # Use scp for uploads; mirrors previous scp_to_remote
        self.ssh.scp_to_remote(src, dst)

    def cp_from(self, src: str, dst: str, exclude: str = None):
        # Keep rsync-based download for speed and compression
        exclude_arg = f"--exclude '{exclude}'" if exclude else ""
        shell(
            f"""rsync --copy-links -avvz --compress-level=9 {exclude_arg} -e "ssh {self.host.get('ssh_options')}" """
            f"""{self.host.get('ssh_user')}@{self.host.get('public_ip')}:{src} {dst}"""
        )


class KubectlRemote(Remote):
    def __init__(self, host: dict):
        self.host = host
        self.namespace = host.get("namespace")
        self.pod = host.get("pod") or host.get("public_ip")
        self.container = host.get("container")
        self.context = host.get("context")
        self.kubeconfig = host.get("kubeconfig")

    def _base_cmd(self):
        cmd = ["kubectl"]
        if self.kubeconfig:
            cmd.extend(["--kubeconfig", os.path.expanduser(self.kubeconfig)])
        if self.context:
            cmd.extend(["--context", self.context])
        if self.namespace:
            cmd.extend(["-n", self.namespace])
        return cmd

    def _add_container(self, cmd):
        if self.container:
            cmd.extend(["-c", self.container])
        return cmd

    def connect(self, check: bool = True) -> int:
        # Cheap connectivity check
        exitcode = self.exec("true", silent=True, fail_on_error=False)
        if isinstance(exitcode, tuple):
            exitcode = exitcode[0]
        if exitcode != 0 and check:
            raise Exception(f"Failed to connect to pod {self.pod}, exitcode={exitcode}")
        return exitcode

    def exec(self, command: str, silent: bool = False, fail_on_error: bool = True) -> Union[int, Tuple[int, str]]:
        # Use sh -c so multi-line commands behave like SSH usage
        cmd = self._base_cmd() + ["exec", self.pod]
        cmd = self._add_container(cmd)
        cmd.extend(["--", "sh", "-c", command])

        stdout = subprocess.DEVNULL if silent else subprocess.PIPE
        stderr = subprocess.DEVNULL if silent else subprocess.PIPE
        result = subprocess.run(cmd, stdout=stdout, stderr=stderr, text=True)
        output = "" if silent else (result.stdout or "") + (result.stderr or "")
        if result.returncode != 0 and fail_on_error:
            raise Exception(f"Failed to execute {' '.join(cmd)}, exitcode={result.returncode}, output={output}")
        if silent:
            return result.returncode
        return result.returncode, output

    def cp_to(self, src: str, dst: str):
        cmd = self._base_cmd() + ["cp"]
        cmd = self._add_container(cmd)
        cmd.extend([src, f"{self.pod}:{dst}"])
        result = subprocess.run(cmd)
        if result.returncode != 0:
            raise Exception(f"kubectl cp to {self.pod} failed, exitcode={result.returncode}")

    def cp_from(self, src: str, dst: str, exclude: str = None):
        if exclude:
            # kubectl cp doesn't support exclude; fallback to tar streaming
            tar_cmd = self._base_cmd() + ["exec", self.pod]
            tar_cmd = self._add_container(tar_cmd)
            tar_cmd.extend(["--", "sh", "-c", f"tar czf - --exclude='{exclude}' -C {src} ."])
            proc = subprocess.Popen(tar_cmd, stdout=subprocess.PIPE)
            os.makedirs(dst, exist_ok=True)
            extract_cmd = ["tar", "xzf", "-","-C", dst]
            result = subprocess.run(extract_cmd, stdin=proc.stdout)
            proc.stdout.close()
            proc.wait()
            if result.returncode != 0 or proc.returncode != 0:
                raise Exception(f"kubectl exec tar from {self.pod} failed, exitcodes tar:{result.returncode}, remote:{proc.returncode}")
            return

        cmd = self._base_cmd() + ["cp"]
        cmd = self._add_container(cmd)
        cmd.extend([f"{self.pod}:{src}", dst])
        result = subprocess.run(cmd)
        if result.returncode != 0:
            raise Exception(f"kubectl cp from {self.pod} failed, exitcode={result.returncode}")


def remote_for_host(host: dict) -> Remote:
    connection = host.get("connection", "ssh")
    if connection == "kubectl":
        return KubectlRemote(host)
    return SshRemote(host)
