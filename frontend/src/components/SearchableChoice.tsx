// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Autocomplete, TextField, Tooltip } from '@mui/material';

interface Choice { readonly id: string; readonly label: string }
interface Props {
  readonly label: string;
  readonly options: readonly Choice[];
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly disabled?: boolean;
  readonly loading?: boolean;
}

/** Keeps a saved selection readable even when it is no longer in the published set. */
export function SearchableChoice({ label, options, value, onChange, disabled = false, loading = false }: Props) {
  const selected = options.find((option) => option.id === value) ?? (value ? { id: value, label: `Saved selection (${value})` } : null);
  const choices = selected && !options.some((option) => option.id === selected.id) ? [...options, selected] : options;
  return <Tooltip title={selected?.label ?? ''}>
    <Autocomplete
      fullWidth
      options={choices}
      value={selected}
      onChange={(_event, option) => onChange(option?.id ?? '')}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(option, current) => option.id === current.id}
      disabled={disabled}
      loading={loading}
      noOptionsText="No matching choices"
      renderOption={(props, option) => {
        const { key, ...rest } = props;
        return <li key={key} {...rest} style={{ ...props.style, overflowWrap: 'anywhere' }}>{option.label}</li>;
      }}
      renderInput={(params) => <TextField {...params} label={label} placeholder="Search or select…" helperText="Clear the selection to use none." />}
    />
  </Tooltip>;
}
