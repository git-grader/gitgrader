// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { createRoot } from 'react-dom/client';
import { App } from './App';

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(<App />);
}
