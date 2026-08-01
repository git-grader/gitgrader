// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { NotBuiltYet } from '../components/NotBuiltYet';

export function AdminSettingsPage() {
  return (
    <NotBuiltYet
      title="Settings"
      explanation="Runtime settings are not editable from the browser yet. Configure the service through its
        environment variables or the mounted /config/application.yaml, as described in the configuration
        documentation."
    />
  );
}
