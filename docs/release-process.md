# Release process

GitGrader uses semantic versioning. A breaking public API, configuration, or
data-compatibility change increments the major version; backward-compatible
features increment the minor version; fixes increment the patch version.

1. Start from a clean, reviewed branch and update `CHANGELOG.md`. Keep an
   `[Unreleased]` section until release; list user-visible behavior, migration,
   security, and operational changes under Keep a Changelog headings.
2. Run `./mvnw clean verify` and perform an installation smoke check against the
   release image.
3. Create and push an annotated `vX.Y.Z` tag. The image workflow builds with
   `./mvnw spring-boot:build-image`, publishes `linux/amd64` and `linux/arm64`
   manifests to GHCR, emits its digest, and creates a provenance attestation.
4. Generate and attach the CycloneDX SBOM (`bom.json` and `bom.xml`) from the
   SBOM workflow. Review dependency and secret scans before announcing a
   release.
5. Publish release notes from the changelog, including upgrade and rollback
   requirements.

Operators should deploy by versioned image tag and record the published digest.
For high-assurance deployments, pin the image by digest in local Compose or an
orchestrator only after the release workflow has published it. Do not invent a
digest or use `latest`.

The current container workflow publishes tag-named images; establish the exact
tag-to-digest mapping from the workflow output or GHCR before changing a
deployment.
