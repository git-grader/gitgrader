// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { NotBuiltYet } from '../components/NotBuiltYet';

export function SubmissionDetailPage() {
  return (
    <NotBuiltYet
      title="Submission"
      explanation="A per-submission view is not available yet. The submissions list shows each attempt's commit,
        status and time, and a student reaches their own graded result through the link issued when they pushed."
    />
  );
}
