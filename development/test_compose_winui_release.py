import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import compose_winui_release as release


class PublicationValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repository = Path(self.temp.name)
        self.directory = self.repository / "io/github/compose-fluent/compose/ui/ui/1.12.0-alpha01"
        self.directory.mkdir(parents=True)
        self.pom = self.directory / "ui-1.12.0-alpha01.pom"
        self.pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
            <groupId>io.github.compose-fluent.compose.ui</groupId><artifactId>ui</artifactId>
            <version>1.12.0-alpha01</version><name>UI</name><description>UI</description>
            <url>https://github.com/compose-fluent/compose-multiplatform-core</url>
            <licenses/><developers/><scm/>
            </project>''', encoding="utf-8")
        for classifier in ("sources", "javadoc"):
            (self.directory / f"ui-1.12.0-alpha01-{classifier}.jar").write_bytes(b"fixture")
        self.module = self.directory / "ui-1.12.0-alpha01.module"

    def test_valid_publication(self):
        self.assertEqual(len(release.validate(self.repository)), 1)

    def test_missing_sources_rejected(self):
        (self.directory / "ui-1.12.0-alpha01-sources.jar").unlink()
        with self.assertRaisesRegex(AssertionError, "Missing sources"):
            release.validate(self.repository)

    def test_wrong_namespace_rejected(self):
        self.pom.write_text(self.pom.read_text().replace("io.github.compose-fluent.compose.ui", "org.jetbrains.compose.ui"))
        with self.assertRaisesRegex(AssertionError, "Not a fork publication"):
            release.validate(self.repository)

    def test_missing_own_dependency_rejected(self):
        self.pom.write_text(self.pom.read_text().replace("</project>", '''<dependencies><dependency>
            <groupId>io.github.compose-fluent.compose.ui</groupId><artifactId>ui-text</artifactId>
            <version>1.12.0-alpha01</version></dependency></dependencies></project>'''))
        with self.assertRaisesRegex(AssertionError, "Unpublished dependency"):
            release.validate(self.repository)

    def test_snapshot_metadata_rejected(self):
        self.module.write_text(json.dumps({"variants": [{"dependencies": [{"version": "1.0-SNAPSHOT"}]}]}))
        with self.assertRaisesRegex(AssertionError, "Development version"):
            release.validate(self.repository)

    def test_missing_variant_rejected(self):
        self.module.write_text(json.dumps({"variants": [{"attributes": {"org.jetbrains.kotlin.platform.type": "jvm"}, "available-at": {"url": "absent.module"}}]}))
        with self.assertRaisesRegex(AssertionError, "Missing variant target"):
            release.validate(self.repository)

    def test_artifact_hash_mismatch_rejected(self):
        self.module.write_text(json.dumps({"variants": [{"attributes": {"org.jetbrains.kotlin.platform.type": "jvm"}, "files": [{
            "url": "ui-1.12.0-alpha01-sources.jar", "sha256": hashlib.sha256(b"different").hexdigest()
        }]}]}))
        with self.assertRaisesRegex(AssertionError, "Stale hash"):
            release.validate(self.repository)

    def test_non_windows_native_rejected(self):
        self.module.write_text(json.dumps({"variants": [{"attributes": {
            "org.jetbrains.kotlin.platform.type": "native", "org.jetbrains.kotlin.native.target": "linux_x64"
        }}]}))
        with self.assertRaisesRegex(AssertionError, "Not MinGW"):
            release.validate(self.repository)

    def test_upstream_non_windows_redirect_allowed(self):
        self.module.write_text(json.dumps({"variants": [{
            "attributes": {"org.jetbrains.kotlin.platform.type": "native", "org.jetbrains.kotlin.native.target": "ios_arm64"},
            "available-at": {"url": "https://repo.maven.apache.org/maven2/org/jetbrains/compose/ui/ui-iosarm64/1.12.1/ui-iosarm64-1.12.1.module", "group": "org.jetbrains.compose.ui", "module": "ui-iosarm64", "version": "1.12.1"}
        }]}))
        self.assertEqual(len(release.validate(self.repository)), 1)


if __name__ == "__main__":
    unittest.main()
