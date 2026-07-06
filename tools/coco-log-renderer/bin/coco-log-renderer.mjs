#!/usr/bin/env node

import { pathToFileURL } from 'node:url';

const ANSI_PATTERN = /\u001b\[[0-9;]*m/g;
const LOG_PATTERN = /^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(?:coco|COCO)\s+(.+?)\s+\[\s*([^\]]*?)\s*\]\s*:?\s?(.*)$/;
const STACK_PATTERN = /^\s*(at\s+|\.{3}\s+\d+ more|Caused by:|Suppressed:)/;

const COLORS = {
  reset: '\u001b[0m',
  bold: '\u001b[1m',
  dim: '\u001b[2m',
  cyan: '\u001b[36m',
  green: '\u001b[32m',
  yellow: '\u001b[33m',
  red: '\u001b[31m',
  magenta: '\u001b[35m',
  gray: '\u001b[90m',
};

const LEVEL_COLORS = {
  TRACE: COLORS.gray,
  DEBUG: COLORS.magenta,
  INFO: COLORS.green,
  WARN: COLORS.yellow,
  ERROR: COLORS.red,
};

/**
 * Coco 终端日志渲染器。
 * <p>
 * 从标准输入读取 Java 进程日志，将 Coco 默认日志格式转换为更紧凑的终端展示形式。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code tools/coco-log-renderer}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */

/**
 * <p>
 * 移除 ANSI 控制字符。
 * </p>
 * @param {string} value 原始文本
 * @returns {string} 清理后的文本
 */
export function stripAnsi(value) {
  return String(value ?? '').replace(ANSI_PATTERN, '');
}

/**
 * <p>
 * 渲染一行 Coco 日志。
 * </p>
 * @param {string} line 原始日志行
 * @param {{colors?: boolean}} [options] 渲染选项
 * @returns {string} 渲染后的日志行
 */
export function renderLine(line, options = {}) {
  const colors = options.colors ?? supportsColor();
  const cleanLine = stripAnsi(line);
  const match = LOG_PATTERN.exec(cleanLine);
  if (match) {
    return renderLogLine(match, colors);
  }
  if (isBannerLine(cleanLine)) {
    return color(cleanLine, COLORS.cyan, colors);
  }
  if (STACK_PATTERN.test(cleanLine)) {
    return color(cleanLine, COLORS.red, colors);
  }
  if (cleanLine.startsWith('  ')) {
    return color(cleanLine, COLORS.dim, colors);
  }
  return cleanLine;
}

/**
 * <p>
 * 渲染多行 Coco 日志文本。
 * </p>
 * @param {string} text 原始日志文本
 * @param {{colors?: boolean}} [options] 渲染选项
 * @returns {string} 渲染后的日志文本
 */
export function renderText(text, options = {}) {
  return String(text ?? '')
    .split(/\r?\n/)
    .map((line) => renderLine(line, options))
    .join('\n');
}

function renderLogLine(match, colors) {
  const [, date, time, level, logger, thread, message] = match;
  const levelText = color(level.padEnd(5), LEVEL_COLORS[level], colors);
  const timeText = color(time, COLORS.gray, colors);
  const dateText = color(date, COLORS.dim, colors);
  const loggerText = color(logger.padEnd(10), COLORS.cyan, colors);
  const threadText = color(thread || '-', COLORS.gray, colors);
  return `${dateText} ${timeText} ${levelText} ${loggerText} ${threadText} ${message}`.trimEnd();
}

function isBannerLine(line) {
  return line.startsWith('◆ ')
    || line.startsWith('◇ ')
    || line.startsWith('▰▱ ')
    || line.startsWith('▱▰ ')
    || line.startsWith('   version')
    || line.startsWith('   spring boot');
}

function color(value, code, enabled) {
  if (!enabled) {
    return value;
  }
  return `${code}${value}${COLORS.reset}`;
}

function supportsColor() {
  if (process.argv.includes('--no-color') || process.env.NO_COLOR) {
    return false;
  }
  if (process.argv.includes('--color=always') || process.env.FORCE_COLOR) {
    return true;
  }
  return Boolean(process.stdout.isTTY);
}

function printHelp() {
  process.stdout.write(`Usage: coco-log-renderer [--no-color|--color=always]

Pipe Coco Java logs into this command to render compact terminal output.

Examples:
  mvn spring-boot:run | node tools/coco-log-renderer/bin/coco-log-renderer.mjs
  java -jar app.jar 2>&1 | coco-log-renderer --color=always
`);
}

async function main() {
  if (process.argv.includes('--help') || process.argv.includes('-h')) {
    printHelp();
    return;
  }
  process.stdin.setEncoding('utf8');
  let buffer = '';
  for await (const chunk of process.stdin) {
    buffer += chunk;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? '';
    for (const line of lines) {
      process.stdout.write(`${renderLine(line)}\n`);
    }
  }
  if (buffer) {
    process.stdout.write(renderLine(buffer));
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.stack ?? error.message}\n`);
    process.exitCode = 1;
  });
}
