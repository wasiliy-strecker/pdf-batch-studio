# ADR 0002: Separate Community and PRO repositories

- Status: accepted
- Date: 2026-07-15

## Decision

Keep Community in a public standalone GitHub repository and future PRO code in
a private standalone GitLab repository. Shared code lives only in Community and
PRO pins released Community artifacts or, temporarily, a Git commit.

## Consequences

Community cannot accidentally require private infrastructure. Private code
review and access remain isolated. Releases require an explicit version update
in PRO, but there are no drifting Free/PRO branches or copied shared sources.
