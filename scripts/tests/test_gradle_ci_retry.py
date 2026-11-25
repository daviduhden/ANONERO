# SPDX-License-Identifier: Apache-2.0
"""Exercise the CI wrapper with a Gradle stub. No build is executed."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

WRAPPER = Path(__file__).resolve().parents[2] / ".github/scripts/run-gradle-ci.sh"
THROTTLED = """A problem occurred configuring root project 'anonero'.
> Could not resolve all artifacts for configuration 'classpath'.
> Could not GET 'https://repo.maven.apache.org/maven2/example.pom'.
  > Received status code 429 from server: Too Many Requests
"""


class GradleCiRetryTest(unittest.TestCase):
    def invoke(self, log, second_exit=0):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stub = root / "gradlew"
            stub.write_text(
                "#!/bin/sh\n"
                'if [ -e attempted ]; then exit "$SECOND_EXIT"; fi\n'
                "touch attempted\n"
                "printf '%s' \"$FIRST_LOG\"\n"
                "exit 17\n"
            )
            stub.chmod(0o700)
            pause = root / "sleep"
            pause.write_text("#!/bin/sh\nprintf '%s' \"$1\" >> pause\n")
            pause.chmod(0o700)
            result = subprocess.run(
                ["bash", str(WRAPPER), ":app:testAnonDebugUnitTest"],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{root}{os.pathsep}{os.environ['PATH']}",
                    "FIRST_LOG": log,
                    "SECOND_EXIT": str(second_exit),
                },
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            waited = (root / "pause").read_text() if (root / "pause").exists() else ""
            return result, waited

    def test_retries_rate_limited_classpath_before_tasks(self):
        result, waited = self.invoke(THROTTLED)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(waited, "60")
        self.assertIn(THROTTLED, result.stdout)

    def test_preserves_failure_of_the_retry(self):
        result, waited = self.invoke(THROTTLED, second_exit=19)
        self.assertEqual(result.returncode, 19)
        self.assertEqual(waited, "60")

    def test_does_not_retry_unrelated_failures(self):
        for log in (
            THROTTLED.replace("429", "404"),
            THROTTLED.replace("repo.maven.apache.org", "other.example"),
            THROTTLED.replace("'classpath'", "'debugRuntimeClasspath'"),
            "> Task :app:testAnonDebugUnitTest FAILED\n" + THROTTLED,
            "> Task :app:compileAnonDebugKotlin FAILED\ne: compilation error\n",
        ):
            with self.subTest(log=log):
                result, waited = self.invoke(log)
                self.assertEqual(result.returncode, 17)
                self.assertEqual(waited, "")
