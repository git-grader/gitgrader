# Release process

GitGrader uses semantic versioning. A breaking public API, configuration, or
data-compatibility change increments the major version; backward-compatible
features increment the minor version; fixes increment the patch version.

## Preparing the release

1. Start from a clean, reviewed branch and update `CHANGELOG.md`. Keep an
   `[Unreleased]` section until release; list user-visible behavior, migration,
   security, and operational changes under Keep a Changelog headings. When the
   release is cut, rename the section to the version and date, add the new
   comparison links, and leave a fresh empty `[Unreleased]` section behind.
2. Set the release version everywhere it is named:
   - `pom.xml` and `backend/pom.xml`, without a `-SNAPSHOT` suffix;
   - `GITGRADER_VERSION` defaults in `compose.yaml` and `.env.example`;
   - the supported-versions table in `SECURITY.md`.
3. Run `./mvnw -Plicense clean verify`. The `license` profile is not part of a
   plain `verify`; it is what CI runs, so a plain `verify` can pass locally and
   fail the build.
4. Commit the release, then create and push an annotated tag:

   ```sh
   git commit -am "release: GitGrader X.Y.Z"
   git tag -a "vX.Y.Z" -m "GitGrader X.Y.Z"
   git push origin main "vX.Y.Z"
   ```

## Publishing the release

The tag triggers two workflows:

- **Container image** builds with
  `./mvnw -B -Plicense verify spring-boot:build-image -pl backend`, publishes a
  `linux/amd64` image to GHCR, scans it, emits its digest, and creates a
  provenance attestation. `-pl backend` is required: the aggregator skips the
  Boot goals. There is no `arm64` image: buildpacks build for the runner's
  architecture only. A stable release moves `:latest`; a pre-release does not.
- **SBOM** generates the aggregate CycloneDX report
  (`gitgrader-sbom.json` and `gitgrader-sbom.xml`) and attaches it to the GitHub
  release for the same tag, creating the release with generated notes if one
  does not exist yet.

Publish release notes from the changelog, including upgrade and rollback
requirements, as soon as the tag is pushed:

```sh
gh release create "vX.Y.Z" --verify-tag \
  --title "GitGrader X.Y.Z" --notes-file release-notes.md
```

Review dependency and secret scans before announcing the release.

## After the release

Immediately bump the default branch to the next development version so it can
never publish an image that calls itself released:

1. Change the version in `pom.xml` and `backend/pom.xml` to the next
   `-SNAPSHOT` version (for example, `0.2.0-SNAPSHOT` after `0.1.0`), and update
   the `GITGRADER_VERSION` defaults in `compose.yaml` and `.env.example` to
   match.
2. Confirm the tree still validates: `./mvnw -q -DskipTests validate`.
3. Commit and push the bump. Every green build of the default branch publishes
   a snapshot under `:snapshot`, `:snapshot-<short-sha>`, and the project's own
   `-SNAPSHOT` version tag that Compose and `scripts/install.sh` default to.
   Snapshot tags track `main` and are overwritten; `latest` moves only with a
   stable release.

Operators should deploy by versioned image tag and record the published digest.
For high-assurance deployments, pin the image by digest in local Compose or an
orchestrator only after the release workflow has published it. Do not invent a
digest or use `latest`. Establish the exact tag-to-digest mapping from the
workflow output or GHCR before changing a deployment.
