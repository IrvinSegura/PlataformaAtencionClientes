import { describe, expect, it } from 'vitest';
import { EventParser, readServerSentEvents } from './sse';

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

async function collect(stream: ReadableStream<Uint8Array>) {
  const events = [];
  for await (const event of readServerSentEvents(stream)) {
    events.push(event);
  }
  return events;
}

describe('readServerSentEvents', () => {
  it('reads named events', async () => {
    const events = await collect(streamOf('event: delta\ndata: {"text":"Hola"}\n\nevent: done\ndata: {}\n\n'));
    expect(events).toEqual([
      { event: 'delta', data: '{"text":"Hola"}' },
      { event: 'done', data: '{}' },
    ]);
  });

  it('joins events split across chunks, including inside a multi-byte character', async () => {
    const bytes = new TextEncoder().encode('event: delta\ndata: {"text":"envío"}\n\n');
    const split = bytes.indexOf(0xc3) + 1;
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(bytes.slice(0, 5));
        controller.enqueue(bytes.slice(5, split));
        controller.enqueue(bytes.slice(split));
        controller.close();
      },
    });
    expect(await collect(stream)).toEqual([{ event: 'delta', data: '{"text":"envío"}' }]);
  });

  it('accepts CRLF and CR line endings, also when CRLF is split between chunks', async () => {
    const events = await collect(streamOf('event: a\r', '\ndata: 1\r\n\r\nevent: b\rdata: 2\r\r'));
    expect(events).toEqual([
      { event: 'a', data: '1' },
      { event: 'b', data: '2' },
    ]);
  });

  it('joins several data lines and ignores comments', async () => {
    const events = await collect(streamOf(': keep-alive\ndata: one\ndata: two\n\n'));
    expect(events).toEqual([{ event: 'message', data: 'one\ntwo' }]);
  });

  it('drops an event cut off before its blank line', async () => {
    expect(await collect(streamOf('event: delta\ndata: {"text":"x"}\n'))).toEqual([]);
  });

  it('resets the event type after an event without data', () => {
    const parser = new EventParser();
    expect([...parser.push('event: ignored\n\ndata: x\n\n')]).toEqual([{ event: 'message', data: 'x' }]);
  });
});
