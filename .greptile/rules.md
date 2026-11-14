# Review rules for solana-foundation/solana-keychain, from its own Greptile reviews

## Cross-language parity and integration testing

- Existing signatures lack coverage (P2, 1×): The two-signer test starts with no signatures, so it checks slot placement but not preservation of another signer's work. Add a round-trip test where one signer signs first and the other signs the returned bytes. Assert that the first signature stays unchanged, both signatures verify, and the final result is complete. This protects the main flow for combining signers from changes that erase an earlier signature.

## Go core signing primitives

- Extra signatures look complete (P2, 1×): `signWireTransaction()` keeps extra signature slots. For a message requiring one signature, an input with two zero-filled slots returns both slots and reports `isComplete = true` after filling the first. Solana requires the signature count to match the message header, so the returned transaction cannot be submitted. Reject counts above `numRequired` before calling `sign`; keep the existing support for growing a short signature list.

## Other findings

- Contributor example no longer compiles (P2, 1×): Removing `simulateTransaction` leaves the integration-test template in `docs/ADDING_SIGNERS.md:766–768` calling a scenario that `TestScenario` no longer accepts. Contributors copying that template now get a TypeScript error. Remove the obsolete test from the guide alongside this change.
- Unused device handles accumulate (P2, 1×): The map keeps every named device's session until that same path fails or a caller uses `None`. Dropping a signer does not release its cached handle. A long-running app that connects to different paths therefore retains unused handles for its entire lifetime, rather than keeping at most one as before. Consider a bounded cache or idle-session cleanup that runs on the device thread.
- Confirmation skips device-count check (P1, 1×): With Ledger A already cached through an unnamed signer, attach Ledger B and call `connect(None, true, None)`. `Sessions::key` returns A's cached path before `connect` closes its session for address confirmation. The call therefore confirms and registers A instead of returning the documented multiple-device error.
- Older signers switch Ledgers (P1, 1×): Connect to Ledger A with `None`, unplug A, then connect to Ledger B with `None`. The second connection overwrites `sole`. After A is plugged back in, A's signer still uses B's open session because the unnamed branch of `targets` ignores `last_run_on(binding)`. Transaction signing prompts on B and rejects its signature, even though A is available.
- Older Node releases still qualify (P2, 1×): `node-version: '24'` accepts any Node 24 release, not just 24.22.1 or later. If the runner has an older cached 24.x release, that version still satisfies this selector, so the intended security update is not guaranteed. Use a range such as `^24.22.1` in all six changed steps to require the stated patched minimum.
- Earlier updates break later selections (P1, 1×): Each `cargo update` call uses a version captured before any updates. When updating one vulnerable crate also upgrades another vulnerable crate, the later `name@old-version` no longer matches the lockfile. That call fails, so `xargs` fails and the workflow skips verification and PR creation—even if the earlier update fixed both advisories.
- Required recipes are bypassed (P2, 1×): The new workflow runs `pnpm` and `cargo` directly. `CLAUDE.md` requires “Always use `just`, never raw `cargo`/`pnpm`/`pytest`/`go test`” because its recipes carry the required flags. The same pattern appears in the crate update and verification steps.
- Production use looks test-only (P2, 1×): The new ignore reason describes `crypto-bigint` as dev-only, but production backends also pull it through `p256` → `elliptic-curve`. `p256` is a regular optional dependency, and Turnkey uses it to sign requests with an API private key. This wording could lead maintainers to treat the exception as affecting only tests. Update the reason to include the production paths and explain why accepting the advisory is appropriate there.
- Invalid lengths get signed (P2, 1×): `compactU16()` accepts overlong encodings such as `80 00` for zero. It also lets the third byte contribute more than the two bits allowed by a 16-bit value. An overlong instruction count can pass the reader and remain unchanged in the signed message, leaving the caller with a transaction Solana cannot decode. Reject redundant encoding bytes and values above 65535 before signing, and add refusal tests.
- Kotlin tests cannot start (P1, 1×): The `test` task uses JUnit Platform, but its runtime dependencies do not include `org.junit.platform:junit-platform-launcher`. Gradle 9 no longer supplies this dependency automatically, so the CI job pinned to Gradle 9.7.1 fails when it starts the tests. Add the launcher with `testRuntimeOnly`, using a version that matches the resolved JUnit dependencies.
- Vault test loses its timeout (P2, 1×): This bare builder does not set a request timeout, and `with_client_builder` does not add one. If the dev Vault accepts a request but stops responding, a signing or availability test can hang indefinitely. The previous `VaultSigner::new` path set a 30-second request timeout. Please set a timeout on this builder before merging: the repository guidance requires timeouts to always be set.
- Manual signing is skipped (P1, 1×): 
- Manual mode lacks an example (P2, 1×): Note: If this suggestion doesn't match your team's coding style, reply to this and let me know. I'll remember it for next time!
- Template omits development dependencies (P2, 1×): 
- Feature example names nonexistent feature (P2, 1×): 
- Home path can be undefined (P2, 1×): 

## Contributing

# Contributing

Thanks for contributing to `solana-keychain`.

This library holds signing keys and talks to production key-management services, so the bar for changes is higher than for most repositories. Read the [Making a change](#making-a-change) and [Cross-language parity](#cross-language-parity) sections before you start writing code.

## Before you start

- Search existing issues and pull requests before opening a new one.
- Open an issue first and wait for a maintainer to label it `accepted`. Every pull request has to reference such an issue with `Fixes #<issue>`; CI labels the ones that don't `needs-issue` and closes them, with the exception of typos, broken links, and comment-only fixes, which declare `Linked issue: trivial` in the description instead. Agreeing on the approach before anyone writes code is what keeps a finished change from being rejected on scope. Small PRs are preferred.
- Do not include secrets, private keys, seed phrases, API keys, or production credentials in issues, pull requests, commits, logs, or screenshots. Integration tests read credentials from a local `.env` that is gitignored: keep it that way, and never paste its contents into a PR or an issue.
- All commits into a Solana Foundation repository require [commit signature verification](https://docs.github.com/en/authentication/managing-commit-signature-verification/about-commit-signature-verification) to be enabled. CI requests changes on a PR carrying an unverified commit and dismisses that review once every commit verifies.

## Security vulnerabilities

Do not report security vulnerabilities in public issues. Follow this repository's [security policy](./SECURITY.md).

Parts of this codebase are covered by a third-party audit. Audit status, the audited-through commit, and the current unaudited delta are tracked in [audits/AUDIT_STATUS.md](audits/AUDIT_STATUS.md). If your change touches audited code, say so in the PR description.

## Development setup

Use `just`. The recipes encode flags that are easy to get wrong by hand: `just rust-test` runs the `sdk-v2`, `sdk-v3`, and `sdk-v4` matrices, whereas `cargo test --all-features` fails outright because the SDK features are mutually exclusive.

```sh
just build              # all four languages
just test               # unit tests, all four languages
just fmt                # format and lint, all four languages
just test-integration   # spins up a local Vault, runs integration tests
just test-all           # test + test-integration
```

Run `just` with no arguments to list every recipe, including the per-language ones (`rust-*`, `ts-*`, `py-*`, `go-*`).

Integration recipes spawn a local `vault server -dev` and load `.env` from the repository root themselves. Do not start Vault yourself or pass secrets on the command line.

Use the toolchain versions checked into the repository. Do not bump the Rust toolchain, the Go version in `go.mod`, `pnpm`, the Solana SDK majors, or the Python dependency pins as an incidental part of another change: each of those has cross-language consequences and belongs in its own PR.

## Making a change

Keep changes focused. A pull request should solve one problem and include the tests and documentation needed to keep the repository usable.

Before opening a pull request:

- Format, lint, build, and test the affected code with `just fmt`, `just build`, and `just test`.
- Add or update tests when behavior changes. Unit tests must not make live API calls: mock HTTP with `wiremock` (Rust), `respx` (Python), `httptest` (Go), or the package's fetch mock (TypeScript).
- Update documentation and examples when they are part of the user-facing contract. There are no changelog files: each release's notes are generated from the merged PR titles, so the PR title is the note.
- Explain any new dependency and why the existing dependency set is insufficient. A signing library pays for every transitive dependency it adds.

### Cross-language parity

`solana-keychain` ships one `SolanaSigner` contract over 14 backends in Rust, TypeScript, Python, and Go. A behavior change in one language is a bug in the other three until it lands there too.

If your change alters signing behavior, error codes, validation, or the wire format, either implement it across all four languages in the same PR, or open tracking issues for the rest and say so explicitly in the PR description. Do not let the implementations drift silently.

The golden wire-format vectors (for example `python/tests/test_parity.py`) exist to catch exactly this. Never regenerate them to make a failing suite pass: a changed vector means the serialization changed, which is the thing the test is there to notice.

### Security-sensitive changes

This library sits on a trust boundary. [docs/SECURITY_MODEL.md](docs/SECURITY_MODEL.md) states what Keychain does and does not guarantee; keep it in sync when a change alters the signing or sending shapes. When your change touches one of the following, document the reasoning in the PR:

- Signature verification and public-key binding: anything deciding whether a provider-returned signature belongs to the key you asked to sign with, and at the position you asked for.
- Transaction serialization, or the capability a backend exposes (transaction-signing, modifying, or sending).
- Broadcast-managed backends, where a failure does not mean nothing landed.
- HTTPS enforcement, redirect handling, timeouts, or credential handling in a remote signer.
- Error messages and their redaction. Messages, `Debug`/`repr`, and sanitized remote bodies are redacted deliberately so key material and raw provider responses cannot leak through logs: keep them generic and let callers match on stable codes.

### Adding a signer backend

Adding a new backend touches Rust, TypeScript, Python, Go, the TypeScript umbrella, and CI. [docs/ADDING_SIGNERS.md](docs/ADDING_SIGNERS.md) has code templates and checklists for Rust, TypeScript, and Python. Go has no written guide yet: use an existing backend under `go/signers/` as the pattern, and see [go/README.md](go/README.md) for the package layout. Go deliberately has no umbrella package, so there is no dispatcher to update there.

Open an issue first. New backends need a maintainer-side CI preparation step, handled through the [`add-signer-ci`](.claude/skills/add-signer-ci/SKILL.md) skill, before your PR can run its integration tests.

## Pull requests

Fill in every section of the pull request template: the problem, the approach, how you tested it, and the [AI disclosure](#disclosure). Link related issues and call out behavior changes, compatibility concerns, or follow-up work. CI fails the PR until the disclosure is declared.

Use [Conventional Commits](https://www.conventionalcommits.org/) for commit and PR titles, scoped by backend or language where it helps (`fix(turnkey):`, `feat(python):`).

Branch and target rules:

- Branch from `main` using `feat/*`, `fix/*`, or `chore/*`. Urgent fixes to a released version branch from the stable tag as `hotfix/*`.
- Target `main`. The `release/*` flow is deprecated and CI rejects PRs from those branches.
- `just branch-info` prints the full guidance.

Review and CI:

- [CODEOWNERS](.github/CODEOWNERS) assigns reviewers automatically; Rust and TypeScript directories each have a designated owner.
- By default, [Greptile](https://www.greptile.com) is enabled on all Solana Foundation repositories. Before maintainers review, all Greptile comments must be resolved with either a code fix or an explanation of why no change is needed.
- Once CI is approved to run by maintainers, all CI errors must be addressed before the PR will be merged.
- **Pull requests from forks** additionally require a live-test gate. Integration tests against real signing providers cannot run with fork credentials, so a maintainer runs them manually and posts a marker comment tied to your head commit. Pushing new commits invalidates that marker and the gate must be re-run: batch your changes rather than force-pushing repeatedly once a maintainer has started.

Maintainers may ask you to rebase, split a broad change, add tests, or revise documentation before merging.

## AI use

You may use AI-assisted tools, but you should review the generated code, understand its behavior, and run the same checks expected of any other contribution.

If you are building with AI on Solana, check out the [Solana Dev Skill](https://github.com/solana-foundation/solana-dev-skill) or the [Solana MCP](https://mcp.solana.com/) to aid in your work.

Ensure that the generated code adheres to the project's coding standards and best practices. Maintainers can close PRs if they appear to be low-effort AI slop. In particular, audit your changes for the following AI code smells that increase maintenance burden:

- Comments that explain why the _previous_ behavior was wrong and the new behavior is correct. This can be helpful context for reviewers as a GitHub comment in the review, but we do not need a history of every code change living in the codebase.
- Large blocks of comments with high density of technical jargon; comments should be distilled to clearly explain _why_ this code is doing something (if it's not obvious), not _what_ (the code should speak for itself).
- Drive-by refactoring of code that is not relevant to the actual change being made.

Two more that matter specifically here:

- **Plausible-looking cryptography.** Assistants will happily produce signature handling, key derivation, or padding logic that compiles, passes a happy-path test, and is wrong. Anything touching signatures or key material needs you to have verified it against the provider's documentation yourself.
- **Silently broken parity.** Applying a change to one language and letting the assistant "port" it to the other three without running each suite is how the implementations drift. Run `just test` and the per-language recipes for every language you touched.

You must be able to explain every line of your diff without an LLM. Reviewers may ask you a pointed question about any part of the change; if the answer is pasted from a model or does not come, the PR is closed.

Tool attribution left in a PR (a `Generated with Claude Code` footer, a `Co-Authored-By: Claude` trailer, a `cursor/` or `codex/` branch, and the like) tells us the submission was opened without being read. CI labels these `ai-unreviewed`, fails the check, and explains what to fix. PRs left in that state are closed.

### Repository context for assistants

This repository ships configuration that makes AI-assisted work go better, and you are welcome to use it:

- [CLAUDE.md](CLAUDE.md) carries the commands, the security standing, and the cross-language gotchas that the code alone does not teach.
- [.claude/skills/](.claude/skills/) contains guides for the repository's multi-step workflows: `add-signer-ci` for wiring CI for a forked backend PR, `release` and `complete-release` for cutting a release.

Keep these current. If you learn something during your change that the next contributor would need (a failure mode, a non-obvious constraint), a line in the relevant `CLAUDE.md` is worth more than a comment buried in the diff.

### Disclosure

Disclosure is required. The pull request template has two boxes; check exactly one. If AI tooling was used, name the tool and the extent, for example:

> I wrote all of the code for this feature, and had Claude update the documentation and create tests accordingly

or

> I architected the change and handed all implementation over to Codex

Editor autocomplete of single keywords or short phrases does not count as AI tooling.

### Communication

If maintainers have suggested changes, feedback, or questions about your code, you should not be copy/pasting the questions to an LLM and copy/pasting the response. You being able to distill the information that AI produces is what makes your contribution valuable.

## License

By contributing, you agree that your contributions are licensed under the project's [LICENSE](./LICENSE).

## Pull request template

## Linked issue

Fixes #

<!-- Required. The issue must be labeled `accepted` by a maintainer. For a typo, broken link, or
comment-only fix, replace the line above with `Linked issue: trivial`. -->

## What and why

<!-- The problem, the approach, and any behavior or compatibility changes. Link related issues. -->

## Testing

<!-- What you ran (`just fmt`, `just build`, `just test`, `just test-integration`) and any manual verification. -->

## AI disclosure

Check exactly one. See [CONTRIBUTING.md](https://github.com/solana-foundation/solana-keychain/blob/main/CONTRIBUTING.md#ai-use).

- [ ] No AI tooling was used beyond editor autocomplete.
- [ ] AI tooling was used. Tool and extent: <!-- e.g. Claude Code wrote the tests, I wrote and reviewed everything else -->
