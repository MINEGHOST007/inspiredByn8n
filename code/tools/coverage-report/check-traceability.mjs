import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const argumentsList = process.argv.slice(2);
const reportOptionIndex = argumentsList.indexOf('--report');
const positionalInput = argumentsList.find((argument, index) =>
  !argument.startsWith('--') && index !== reportOptionIndex + 1);
const input = path.resolve(root, positionalInput ?? 'artifacts/design/srs/traceability.csv');
const report = reportOptionIndex >= 0
  ? path.resolve(root, argumentsList[reportOptionIndex + 1])
  : null;

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    const next = text[index + 1];
    if (character === '"' && quoted && next === '"') {
      field += '"';
      index += 1;
    } else if (character === '"') {
      quoted = !quoted;
    } else if (character === ',' && !quoted) {
      row.push(field);
      field = '';
    } else if ((character === '\n' || character === '\r') && !quoted) {
      if (character === '\r' && next === '\n') index += 1;
      row.push(field);
      if (row.some(value => value.length > 0)) rows.push(row);
      row = [];
      field = '';
    } else {
      field += character;
    }
  }
  if (field || row.length) {
    row.push(field);
    rows.push(row);
  }
  const [header, ...data] = rows;
  return data.map(values => {
    if (values.length <= header.length) {
      return Object.fromEntries(header.map((key, index) => [key, values[index] ?? '']));
    }
    // The existing matrix predates strict CSV quoting. Preserve its last two
    // columns and fold descriptive commas into the test evidence field.
    const normalized = [values[0], values[1], values[2], values[3], values.slice(4, -2).join(','), values.at(-2), values.at(-1)];
    return Object.fromEntries(header.map((key, index) => [key, normalized[index] ?? '']));
  });
}

if (!fs.existsSync(input)) throw new Error(`Traceability file not found: ${input}`);
const rows = parseCsv(fs.readFileSync(input, 'utf8'));
const errors = [];
const ids = new Set();
const allowedStatuses = new Set(['Implemented', 'Partial', 'Not implemented']);
for (const [index, row] of rows.entries()) {
  const line = index + 2;
  if (!row.req_id) errors.push(`line ${line}: req_id is required`);
  if (ids.has(row.req_id)) errors.push(`line ${line}: duplicate req_id ${row.req_id}`);
  ids.add(row.req_id);
  const baseStatus = row.status.replace(/\s*\([^)]*\)\s*$/, '');
  if (!allowedStatuses.has(baseStatus)) errors.push(`line ${line}: unsupported status '${row.status}'`);
  row.status = baseStatus;
  if (!row.test_id && row.status === 'Implemented') errors.push(`line ${line}: test_id/evidence is required`);
  if (!row.module) errors.push(`line ${line}: module is required`);
}
if (errors.length) {
  console.error(errors.join('\n'));
  process.exit(1);
}

const counts = Object.fromEntries([...allowedStatuses].map(status => [status, rows.filter(row => row.status === status).length]));
const byPriority = [...new Set(rows.map(row => row.priority))].map(priority => ({
  priority,
  total: rows.filter(row => row.priority === priority).length,
  implemented: rows.filter(row => row.priority === priority && row.status === 'Implemented').length,
  partial: rows.filter(row => row.priority === priority && row.status === 'Partial').length,
  open: rows.filter(row => row.priority === priority && row.status === 'Not implemented').length,
}));
const open = rows.filter(row => row.status !== 'Implemented');
const markdown = [
  '# W6W Implementation Status',
  '',
  `Generated from [traceability.csv](traceability.csv). Total requirements: **${rows.length}**.`,
  '',
  '| Status | Count |',
  '|---|---:|',
  ...Object.entries(counts).map(([status, count]) => `| ${status} | ${count} |`),
  '',
  '| Priority | Total | Implemented | Partial | Not implemented |',
  '|---|---:|---:|---:|---:|',
  ...byPriority.map(item => `| ${item.priority} | ${item.total} | ${item.implemented} | ${item.partial} | ${item.open} |`),
  '',
  '## Remaining Work',
  '',
  '| Requirement | Priority | Status | Evidence / owning module |',
  '|---|---|---|---|',
  ...open.map(row => `| ${row.req_id} | ${row.priority} | ${row.status} | ${row.module} (${row.test_id}) |`),
  '',
  '> Partial means a local implementation exists but the SRS acceptance criteria are not fully met. Not implemented means the capability is still open; local-demo behavior must not be treated as production support.',
  '',
].join('\n');

if (report) {
  fs.mkdirSync(path.dirname(report), { recursive: true });
  fs.writeFileSync(report, markdown);
}
console.log(`Traceability valid: ${rows.length} requirements; ${counts.Implemented} implemented, ${counts.Partial} partial, ${counts['Not implemented']} not implemented.`);
