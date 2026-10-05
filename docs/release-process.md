# Release process

GitGrader uses semantic versioning. A breaking public API, configuration, or
data-compatibility change increments the major version; backward-compatible
features increment the minor version; fixes increment the patch version.

1. Start from a clean, reviewed branch and update `CHANGELOG.md`. Keep an
   `[Unreleased]` section until release; list user-visible behavior, migration,
   security, and operational changes under Keep a Changelog headings.
2. Run `./mvnw -Plicense clean verify` and perform an installation smoke check
   against the release image. The `license` profile is not part of a plain
   `verify`, and it is what CI runs, so a plain `verify` can pass locally and
   fail the build.
3. Create and push an annotated `vX.Y.Z` tag. The image workflow builds with
   `./mvnw -B -Plicense verify spring-boot:build-image -pl backend`, publishes
   a `linux/amd64` image to GHCR, scans it, emits its digest, and creates a
   provenance attestation. The `-pl backend` is required: the aggregator skips
   the Boot goals. There is no
   `arm64` image: buildpacks build for the runner's architecture only.
   Every green build of the default branch also publishes a snapshot under
   `:snapshot` and `:snapshot-<short-sha>`, plus the project's own `-SNAPSHOT`
   version tag that compose and scripts/install.sh default to.
4. Confirm the POM version matches the tag with no `-SNAPSHOT` suffix. The
   running build reports its own `${project.version}` through `/api/v1/meta`
   and in the image's build info, so a tag cut without bumping the POM
   publishes an image that calls itself a snapshot.
5. The SBOM workflow generates the aggregate CycloneDX SBOM
   (`gitgrader-sbom.json` and `gitgrader-sbom.xml`, named by the plugin's
   `outputName`) and attaches it to the release for the same tag. Review
   dependency
   and secret scans before announcing a release.
6. Publish release notes from the changelog, including upgrade and rollback
   requirements.

Operators should deploy by versioned image tag and record the published digest.
For high-assurance deployments, pin the image by digest in local Compose or an
orchestrator only after the release workflow has published it. Do not invent a
digest or use `latest`.

The container workflow publishes releases from `v*` tags and development
snapshots from every green default-branch build. Snapshot tags track `main` and
are overwritten; `latest` moves only with a stable release. Establish the exact
tag-to-digest mapping from the workflow output or GHCR before changing a
deployment.
