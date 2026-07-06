import assert from 'node:assert/strict';
import test from 'node:test';

import { renderLine, renderText, stripAnsi } from '../bin/coco-log-renderer.mjs';

test('renders coco log line compactly without colors', () => {
  const line = '2026-07-06 00:21:53.738 INFO  coco lifecycle  [           main] ▸ startup';

  assert.equal(renderLine(line, { colors: false }),
    '2026-07-06 00:21:53.738 INFO  lifecycle  main ▸ startup');
});

test('keeps lightweight unicode banner readable', () => {
  assert.equal(renderLine('◆ coco spring', { colors: false }), '◆ coco spring');
  assert.equal(renderLine('   version     1.0.0-SNAPSHOT', { colors: false }),
    '   version     1.0.0-SNAPSHOT');
});

test('keeps stack trace lines visible', () => {
  assert.equal(renderLine('Caused by: java.lang.IllegalStateException: failed', { colors: false }),
    'Caused by: java.lang.IllegalStateException: failed');
  assert.equal(renderLine('\tat io.github.coco.Sample.main(Sample.java:12)', { colors: false }),
    '\tat io.github.coco.Sample.main(Sample.java:12)');
});

test('strips existing ansi codes before rendering', () => {
  assert.equal(stripAnsi('\u001b[31mERROR\u001b[0m'), 'ERROR');
});

test('renders multiline text', () => {
  const text = [
    '◆ coco spring',
    '2026-07-06 00:21:53.738 WARN  coco access     [nio-8080-exec-1] slow request',
  ].join('\n');

  assert.equal(renderText(text, { colors: false }), [
    '◆ coco spring',
    '2026-07-06 00:21:53.738 WARN  access     nio-8080-exec-1 slow request',
  ].join('\n'));
});

test('renders old ansi log chunk as clean text', () => {
  const text = '2026-07-06 00:21:53.738 \u001b[34mINFO \u001b[0;39m coco lifecycle  [           main] ▸ startup';

  assert.equal(renderText(text, { colors: false }),
    '2026-07-06 00:21:53.738 INFO  lifecycle  main ▸ startup');
});

test('renders current uppercase coco prefix with readable logger names', () => {
  const text = '2026-07-06 00:21:53.738 INFO  COCO io.github.coco.lifecycle [main] : ◂ ready';

  assert.equal(renderText(text, { colors: false }),
    '2026-07-06 00:21:53.738 INFO  io.github.coco.lifecycle main ◂ ready');
});
