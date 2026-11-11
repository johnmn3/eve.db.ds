"""Harness regression checks only: no DataScript suites are executed."""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ds_suite", Path(__file__).resolve().parents[2] / "scripts/ds_suite.py")
suite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(suite)

class DriverTests(unittest.TestCase):
    def test_timeout_continues_to_next_namespace_and_fails_gate(self):
        seen = []
        def run(cmd, timeout, log):
            seen.append(cmd[-1])
            if cmd[-1] == "slow":
                return 124, "", .01
            return 0, "DS-SUITE-SUMMARY host=bb nses=1 loaded=1 tests=1 pass=1 fail=0 error=0 skipped=0\n", .01
        with tempfile.TemporaryDirectory() as tmp, patch.object(suite.subprocess, "check_output", return_value="Filesystem blocks used available capacity mounted\n/dev/test 99999999 1 99999998 1% /\n"), patch.object(suite, "command", return_value=["fake"]), patch.object(suite, "run", side_effect=run), patch.object(sys, "argv", ["ds_suite.py", "bb", "--timeout", "1", "--out", tmp, "slow", "fast"]), contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(1, suite.main())
            self.assertEqual(["slow", "fast"], seen)
            self.assertIn("slow\ttimeout\t124", output.getvalue())
            self.assertIn("fast\tpass\t0", output.getvalue())

    def test_real_timeout_kills_child_process(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            rc, _, _ = suite.run([sys.executable, "-c", "import time; time.sleep(10)"], .1, Path(tmp)/"timeout.log")
            self.assertEqual(124, rc)

    def test_missing_summary_is_error_not_pass(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(suite.subprocess, "check_output", return_value="Filesystem blocks used available capacity mounted\n/dev/test 99999999 1 99999998 1% /\n"), patch.object(suite, "command", return_value=["fake"]), patch.object(suite, "run", return_value=(0, "no summary", .01)), patch.object(sys, "argv", ["ds_suite.py", "nbb", "--out", tmp, "broken"]), contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(1, suite.main())
            self.assertIn("broken\terror\t0", output.getvalue())

if __name__ == "__main__":
    unittest.main()
