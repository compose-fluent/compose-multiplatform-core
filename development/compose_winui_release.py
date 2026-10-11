#!/usr/bin/env python3
"""Validate Windows publications and assemble a signed Central Portal bundle."""
import argparse
import base64
import copy
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request
import urllib.parse
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_STAGE = ROOT / "out/compose-multiplatform-core/build/compose-winui-release"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
CENTRAL = "https://central.sonatype.com/api/v1/publisher"


def family_for(path):
    for prefix, family in ((":compose:material3:adaptive:", "COMPOSE_MATERIAL3_ADAPTIVE"),
                           (":compose:material3:", "COMPOSE_MATERIAL3"), (":compose:", "COMPOSE"),
                           (":navigation3:", "NAVIGATION_3"), (":navigationevent:", "NAVIGATION_EVENT")):
        if path.startswith(prefix):
            return family
    return path.split(":")[1].upper()


def prepare(stage):
    release = properties()
    modules = json.loads((ROOT / "compose-winui-modules.json").read_text())
    cache = stage / "upstream"
    cache.mkdir(parents=True, exist_ok=True)

    def resolve(item):
        mode, path = item
        family = family_for(path)
        group = ("org.jetbrains." + path.lstrip(":").rsplit(":", 1)[0].replace(":", ".")
                 if path.startswith(":compose:") else
                 "org.jetbrains.androidx." + path.split(":")[1])
        name = path.rsplit(":", 1)[1]
        upstream_group = group.replace("org.jetbrains.androidx.", "androidx.").replace("org.jetbrains.compose.", "androidx.compose.") if mode == "jetpack" else group
        version = release[family + (".jetpack" if mode == "jetpack" else ".cmp")]
        base = "https://dl.google.com/dl/android/maven2/" if mode == "jetpack" else "https://repo.maven.apache.org/maven2/"
        url = base + upstream_group.replace(".", "/") + f"/{name}/{version}/{name}-{version}.module"
        cached = cache / (upstream_group + "_" + name + "_" + version + ".module")
        if not cached.exists():
            with urllib.request.urlopen(url, timeout=60) as response:
                cached.write_bytes(response.read())
        data = json.loads(cached.read_text())
        assert data["component"]["group"] == upstream_group
        assert data["component"]["module"] == name and data["component"]["version"] == version
        variants = data["variants"]
        if mode == "jetpack" and not any(v.get("attributes", {}).get("org.jetbrains.kotlin.native.target") == "mingw_x64" for v in variants):
            mode = "mingw"
        redirects = []
        for original in variants:
            variant = copy.deepcopy(original)
            attributes = variant.get("attributes", {})
            platform = attributes.get("org.jetbrains.kotlin.platform.type")
            if mode != "jetpack" and platform == "common":
                continue
            if mode == "winui" and platform == "jvm":
                continue
            if mode != "jetpack" and attributes.get("org.jetbrains.kotlin.native.target") == "mingw_x64":
                continue
            if "available-at" in variant:
                variant["available-at"]["url"] = urllib.parse.urljoin(url, variant["available-at"]["url"])
            else:
                variant = {"name": variant["name"], "attributes": attributes, "available-at": {
                    "url": url, "group": upstream_group, "module": name, "version": version}}
            redirects.append(variant)
        return path, {"mode": mode, "group": upstream_group, "module": name, "version": version,
                      "sourceUrl": url, "sourceSha256": hashlib.sha256(cached.read_bytes()).hexdigest(),
                      "variants": redirects}

    with ThreadPoolExecutor(max_workers=6) as pool:
        plan = dict(pool.map(resolve, [(mode, path) for mode, paths in modules.items() for path in paths]))
    (stage / "upstream-redirects.json").write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    for mode in ("winui", "mingw", "jetpack"):
        print(f"{mode}: {sum(p['mode'] == mode for p in plan.values())} modules")


def properties():
    return dict(line.strip().split("=", 1) for line in
                (ROOT / "compose-winui-release.properties").read_text().splitlines()
                if line.strip() and not line.lstrip().startswith("#"))


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def verify_source(require_tag=False):
    release = properties()
    git("merge-base", "--is-ancestor", release["upstream.commit"], "HEAD")
    assert git("rev-parse", release["upstream.tag"] + "^{commit}") == release["upstream.commit"]
    build = dict(line.strip().split("=", 1) for line in
                 (ROOT / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.lstrip().startswith("#"))
    assert build["composeWinUi.skikoWinUiVersion"] == release["skiko.version"]
    assert build["kotlinWinRt.version"] == release["kotlinWinRt.version"]
    if require_tag:
        assert git("rev-parse", release["release.tag"] + "^{commit}") == git("rev-parse", "HEAD")
        assert not git("status", "--porcelain", "--untracked-files=no"), "Tracked release source is dirty"
        if os.environ.get("GITHUB_REF"):
            assert os.environ["GITHUB_REF"] == "refs/tags/" + release["release.tag"]
    return release


def own(group):
    return group.startswith(("io.github.compose-fluent.compose.", "io.github.compose-fluent.androidx."))


def validate(repository):
    release = properties()
    allowed_versions = {v for k, v in release.items() if k.endswith(".version")}
    poms = sorted(repository.rglob("*.pom"))
    assert poms, "No staged POMs"
    artifacts = []
    for file in poms:
        pom = ET.parse(file).getroot()
        value = lambda key: pom.findtext("m:" + key, namespaces=NS)
        group, artifact, version = (value(k) for k in ("groupId", "artifactId", "version"))
        assert own(group), f"Not a fork publication: {file}"
        assert version in allowed_versions, f"Unpinned version: {file}"
        expected = Path(group.replace(".", "/")) / artifact / version / f"{artifact}-{version}.pom"
        assert file.relative_to(repository) == expected, f"POM identity/path mismatch: {file}"
        for key in ("name", "description", "url", "licenses", "developers", "scm"):
            assert pom.find("m:" + key, NS) is not None, f"Missing {key}: {file}"
        for dependency in pom.findall(".//m:dependency", NS):
            dg = dependency.findtext("m:groupId", namespaces=NS)
            da = dependency.findtext("m:artifactId", namespaces=NS)
            dv = dependency.findtext("m:version", namespaces=NS)
            assert dv and "SNAPSHOT" not in dv and "+" not in dv, f"Unpinned dependency: {file}"
            if own(dg):
                target = repository / dg.replace(".", "/") / da / dv / f"{da}-{dv}.pom"
                assert target.is_file(), f"Unpublished dependency {dg}:{da}:{dv} in {file}"
        for classifier in ("sources", "javadoc"):
            assert file.with_name(f"{artifact}-{version}-{classifier}.jar").is_file(), f"Missing {classifier}: {file}"
        artifacts.append({"group": group, "artifact": artifact, "version": version})
        binary = file.with_suffix(".jar")
        if binary.exists():
            with zipfile.ZipFile(binary) as archive:
                for entry in archive.namelist():
                    if entry.endswith(".class"):
                        data = archive.read(entry)
                        assert not any(marker in data for marker in (b"java/awt/", b"javax/swing/", b"java.awt.", b"javax.swing.", b"org/jetbrains/skiko/awt/")), f"AWT/Swing reference in {binary}: {entry}"
    for file in repository.rglob("*.module"):
        text = file.read_text(encoding="utf-8")
        assert "SNAPSHOT" not in text and "9999.0.0" not in text, f"Development version: {file}"
        data = json.loads(text)
        assert data["variants"], f"No published variants: {file}"
        for variant in data["variants"]:
            link = variant.get("available-at")
            if link and link["url"].startswith("https://"):
                assert link["url"].startswith(("https://repo.maven.apache.org/maven2/", "https://dl.google.com/dl/android/maven2/")), f"Unexpected redirect origin: {link}"
                assert link["group"].startswith(("org.jetbrains.", "androidx.")), f"Unexpected redirect group: {link}"
                assert "files" not in variant, "An upstream redirect must not ship local binaries"
                continue
            attributes = variant.get("attributes", {})
            platform = attributes.get("org.jetbrains.kotlin.platform.type")
            assert platform in ("common", "jvm", "native"), f"Unsupported release platform: {variant}"
            if platform == "native":
                assert attributes.get("org.jetbrains.kotlin.native.target") == "mingw_x64", f"Not MinGW: {variant}"
            if link:
                target = (file.parent / link["url"]).resolve()
                assert target.is_relative_to(repository.resolve()), f"External local redirect: {file}"
                assert target.is_file(), f"Missing variant target {target} in {file}"
            for dep in variant.get("dependencies", []):
                if own(dep["group"]):
                    version = dep["version"].get("requires", dep["version"].get("strictly"))
                    artifact = dep["module"]
                    target = repository / dep["group"].replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"
                    assert target.is_file(), f"Missing Gradle dependency: {dep} in {file}"
            for entry in variant.get("files", []):
                target = file.parent / entry["url"]
                assert target.is_file(), f"Missing artifact file: {target}"
                for algorithm in ("sha256", "sha512", "sha1", "md5"):
                    if algorithm in entry:
                        assert hashlib.new(algorithm, target.read_bytes()).hexdigest() == entry[algorithm], f"Stale hash: {target}"
    return artifacts


def bundle(stage, sign):
    release = verify_source(require_tag=sign)
    repository = stage / "repository"
    artifacts = validate(repository)
    inventory = json.loads((stage / "publications.json").read_text())
    assert len(artifacts) == sum(len(names) for names in inventory.values()), "Incomplete publication inventory"
    files = sorted(f for f in repository.rglob("*") if f.is_file() and f.suffix in (".pom", ".module", ".jar", ".klib"))
    output = stage / f"compose-winui-{release['release.version']}-central.zip"
    hashes = {}
    with tempfile.TemporaryDirectory(prefix="compose-winui-sign-") as home:
        if sign:
            key = os.environ["MAVEN_GPG_KEY_ARMOR"]
            subprocess.run(["gpg", "--homedir", home, "--batch", "--import"], input=key, text=True, check=True, capture_output=True)
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            for file in files:
                name = file.relative_to(repository).as_posix()
                data = file.read_bytes()
                archive.writestr(name, data)
                hashes[name] = hashlib.sha256(data).hexdigest()
                for algorithm in ("md5", "sha1"):
                    archive.writestr(name + "." + algorithm, hashlib.new(algorithm, data).hexdigest())
                if sign:
                    signature = Path(home) / "artifact.asc"
                    subprocess.run(["gpg", "--homedir", home, "--batch", "--yes", "--pinentry-mode", "loopback",
                                    "--passphrase-fd", "0", "--local-user", os.environ["MAVEN_GPG_KEY_ID"],
                                    "--armor", "--detach-sign", "--output", str(signature), str(file)],
                                   input=os.environ.get("MAVEN_GPG_KEY_PASSWORD", ""), text=True, check=True, capture_output=True)
                    archive.writestr(name + ".asc", signature.read_bytes())
    manifest = {"publishable": sign, "commit": git("rev-parse", "HEAD"), "release": release,
                "trackedChangesPresent": bool(git("status", "--porcelain", "--untracked-files=no")),
                "upstreamRedirects": json.loads((stage / "upstream-redirects.json").read_text()),
                "artifacts": artifacts, "files": hashes, "bundle": output.name,
                "bundleSha256": hashlib.sha256(output.read_bytes()).hexdigest()}
    (stage / "release-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Validated {len(artifacts)} publications: {output}")


def upload(stage, deployment_id=None):
    verify_source(require_tag=True)
    manifest = json.loads((stage / "release-manifest.json").read_text())
    assert manifest["publishable"] and manifest["commit"] == git("rev-parse", "HEAD")
    data = (stage / manifest["bundle"]).read_bytes()
    assert hashlib.sha256(data).hexdigest() == manifest["bundleSha256"]
    token = base64.b64encode((os.environ["MAVEN_CENTRAL_USERNAME"] + ":" + os.environ["MAVEN_CENTRAL_PASSWORD"]).encode()).decode()
    headers = {"Authorization": "Bearer " + token}
    receipt = stage / "central-deployment.json"
    if not deployment_id and receipt.exists():
        previous = json.loads(receipt.read_text())
        assert previous["bundleSha256"] == manifest["bundleSha256"], "Deployment belongs to another bundle"
        deployment_id = previous["deploymentId"]
    if not deployment_id:
        boundary = uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; filename="{manifest["bundle"]}"\r\n'
                'Content-Type: application/zip\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
        request = urllib.request.Request(CENTRAL + "/upload?publishingType=AUTOMATIC", data=body,
                                         headers={**headers, "Content-Type": "multipart/form-data; boundary=" + boundary}, method="POST")
        with urllib.request.urlopen(request, timeout=300) as response:
            deployment_id = response.read().decode().strip().strip('"')
        receipt.write_text(json.dumps({"deploymentId": deployment_id, "bundleSha256": manifest["bundleSha256"]}), encoding="utf-8")
    deadline = time.monotonic() + 3600
    while time.monotonic() < deadline:
        request = urllib.request.Request(CENTRAL + "/status?id=" + deployment_id, headers=headers, method="POST")
        with urllib.request.urlopen(request, timeout=60) as response:
            status = json.load(response)
        state = status["deploymentState"]
        print(f"Central {deployment_id}: {state}", flush=True)
        if state == "PUBLISHED":
            return
        if state == "FAILED":
            raise RuntimeError(json.dumps(status))
        time.sleep(30)
    raise RuntimeError(f"Publication pending; resume deployment {deployment_id}, do not re-upload")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "verify", "validate", "bundle", "upload"))
    parser.add_argument("--stage", type=Path, default=DEFAULT_STAGE)
    parser.add_argument("--sign", action="store_true")
    parser.add_argument("--require-tag", action="store_true")
    parser.add_argument("--deployment-id")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.stage)
    elif args.command == "verify":
        verify_source(args.require_tag)
    elif args.command == "validate":
        print(f"Validated {len(validate(args.stage / 'repository'))} publications")
    elif args.command == "bundle":
        bundle(args.stage, args.sign)
    else:
        upload(args.stage, args.deployment_id)
