# User review before pull requests

- Finish the proposed code changes and appropriate local checks before asking for review.
- Before creating any pull request, including a draft, give the user a review packet:
  the exact code revision/diff, files and behaviours to inspect, validation results,
  and any unresolved limitations. Wait for the user's explicit clearance.
- Do not treat earlier approval, a request to continue, or elapsed time as clearance
  for a new PR. Material code changes after clearance require renewed review.
- Record review confirmation only after the user actually clears the revision.
  Identify the reviewed code commit and approval date; do not imply that later code
  or the entire historical codebase was reviewed.
- After clearance, add the factual confirmation to every project-authored human-
  readable document, including the README, build spec, guides, ADRs and interview
  guide. Maintain a central review history and a concise latest-review record in
  each document. The documentation-only act of recording an already given approval
  does not require a circular second approval.
- Exclude dependency/vendor/build documentation and generated numerical evidence
  such as JSON reports, fixtures and certificate files from review stamps.
- Never invent, predate or prefill a review confirmation. Until clearance, the
  current change is awaiting user review and no PR may be opened.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`2b9d114ea3999c6224e04daeba6261afb6ca44e8` and cleared PR creation on
2026-11-04 (Europe/London), with “cleared 2b9d114”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
