// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { Box, Button } from '@mui/material';

/**
 * One piece of copyable text (a clone command, a URL) with a copy button that admits
 * when it could not copy.
 *
 * The clipboard API does not exist on an insecure origin and can be refused on a
 * secure one, and both arrive here as a click that did nothing at all. On the
 * registration page this is the first thing a student sees and the clone command is
 * the one thing they came away with, so a button that quietly fails sends them
 * looking for a command they think they already have.
 */
export function CopyableCode({ text }: { text: string }) {
  const [outcome, setOutcome] = useState<'idle' | 'copied' | 'failed'>('idle');

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setOutcome('copied');
    }
    catch {
      setOutcome('failed');
    }
  };

  const label = outcome === 'copied' ? 'Copied' : outcome === 'failed' ? 'Copy it by hand' : 'Copy';
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', minWidth: 0 }}>
      <Box
        component="code"
        // A clone URL is longer than a phone is wide, and these boxes sit on the
        // first page a student sees after registering and on instructor pages
        // reached from a narrow viewport.
        sx={{
          bgcolor: 'action.hover',
          px: 0.5,
          py: 0.25,
          borderRadius: 1,
          fontFamily: 'monospace',
          overflowWrap: 'anywhere',
          minWidth: 0
        }}
      >
        {text}
      </Box>
      <Button size="small" onClick={() => void copy()}>
        {label}
      </Button>
      {/* The announcement lives in its own region rather than on the button. A live
          region that is also the focused control is announced inconsistently, and here
          it is the only confirmation that the text was copied at all. */}
      <Box component="span" role="status" sx={{ position: 'absolute', width: '1px', height: '1px', padding: 0, margin: '-1px', overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap', border: 0 }}>
        {outcome === 'copied' ? 'Copied to the clipboard.' : outcome === 'failed' ? 'The text could not be copied. Select it and copy it by hand.' : ''}
      </Box>
    </Box>
  );
}
