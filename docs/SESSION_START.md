# Session Start

Minimal handoff for a fresh coding-agent session. It provides navigation, not a
second product, architecture or historical-evidence summary.

## Read now

1. `AGENTS.md`
2. `docs/PRODUCT-MANIFEST.md`
3. `docs/ARCHITECTURE.md`
4. `docs/CURRENT-STATE.md`
5. current task document, only when required for the scope

Read `README.md` only when build/test/run commands are needed. Read historical
task/run documents only to answer a concrete question raised by the current
task or an evidence claim.

## Working handoff

Local AI Worker is the product; Engineering Review Mentor is a specialized use case.
Structural refactor is complete and OWNER ACCEPTED at `7843b3f`. Resume from
`docs/CURRENT-STATE.md` and actual Git/source state; the final `dev.aiadvent.worker`
package tree is source authority, with capability boundaries in `docs/ARCHITECTURE.md`.
Do not restart architecture cleanup. Preserve unrelated private/uncommitted work;
`docs/**` remains owner-private and must not be staged/committed without explicit
publication authorization.

Day 11 is CLOSED: final video demo PASS, VIDEO RECORDED: YES, and `2ab727f` adds the
live-smoked supported dialog delete UX. Day 12 is CLOSED at product commit `24a11b1`: Profile is
orchestration configuration describing HOW the agent works, not Memory describing WHAT is retained.
`Profile config != Memory`; Profile persistence and per-dialog selection are independent of
history and Memory. Day 13 is NOT STARTED; do not pre-build Task/TaskState, Invariants or controlled
transitions.

For follow-up work in the same session, use delta context rather than repeating
this full handoff.
