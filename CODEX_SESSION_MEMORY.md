# Codex Session Memory

This file is a compact working memory for continuing MilitaryCraft work after context compaction.

## Current User Priority

- Preserve original plugin gameplay, visuals, models, hitboxes, controls, items, settings, and feel.
- Exact command syntax is less important than original gameplay behavior and visual/model parity.
- Do not redesign systems just because a cleaner implementation is tempting.
- When restoring original plugins, compare against original sources/jars/configs first, then adapt only where the user explicitly approved changes.

## Clear Thinking Rules To Reapply

- Do not trust the first plausible explanation for a bug.
- Separate the symptom, immediate mechanism, root cause, and aggravating factors.
- Fix root causes instead of piling local patches over symptoms.
- Before implementing a non-trivial change, identify the real goal, hard constraints, key unknown, and what proves success.
- If there is a genuine fork, hold at least two approaches briefly and choose the one that best preserves original behavior.
- After a solution looks good, try to break it: repeated calls, null/empty state, permissions, event cancellation, stale state, plugin reloads, and old behavior compatibility.
- Verify with build/tests when possible, and never claim verification that was not actually run.
- Keep final user-facing explanations plain and concise; show decisions through results, not academic labels.

## Local Workflow Reminder

- Read existing implementation before editing.
- Use `rg` for search.
- Use `apply_patch` for manual file edits.
- Avoid touching unrelated code or restoring/reverting user changes.
- Update `ORIGINAL_PARITY_RESTORE_PLAN.md` when finishing a meaningful restoration or parity check.
