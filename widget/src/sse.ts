export interface ServerSentEvent {
  event: string;
  data: string;
}

/**
 * Reads server-sent events from a response body. EventSource cannot be used because it only sends GET requests
 * without custom headers, and questions are POSTed with the conversation token in a header.
 *
 * Follows the parsing rules of the HTML standard that matter here: events end at a blank line, several data lines
 * are joined with a line break, comment lines (starting with ':') are ignored, and lines may end in \n, \r\n or \r.
 */
export async function* readServerSentEvents(body: ReadableStream<Uint8Array>): AsyncGenerator<ServerSentEvent> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  const parser = new EventParser();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      // stream: true keeps a multi-byte character split across chunks intact
      yield* parser.push(decoder.decode(value, { stream: true }));
    }
    yield* parser.push(decoder.decode(), true);
    // An event is only complete at a blank line; anything after the last one was cut off and is dropped
  } finally {
    reader.releaseLock();
  }
}

export class EventParser {
  private buffer = '';
  private eventType = '';
  private dataLines: string[] = [];

  /** @param final whether the stream ended, so a trailing \r is a complete line ending */
  *push(text: string, final = false): Generator<ServerSentEvent> {
    this.buffer += text;
    for (;;) {
      const match = /\r\n|\r|\n/.exec(this.buffer);
      // A trailing \r may be the first half of \r\n; wait for the next chunk to know
      if (match === null || (!final && match[0] === '\r' && match.index === this.buffer.length - 1)) {
        return;
      }
      const line = this.buffer.slice(0, match.index);
      this.buffer = this.buffer.slice(match.index + match[0].length);
      const event = this.line(line);
      if (event !== null) {
        yield event;
      }
    }
  }

  private line(line: string): ServerSentEvent | null {
    if (line === '') {
      if (this.dataLines.length === 0) {
        this.eventType = '';
        return null;
      }
      const event = { event: this.eventType || 'message', data: this.dataLines.join('\n') };
      this.eventType = '';
      this.dataLines = [];
      return event;
    }
    if (line.startsWith(':')) {
      return null;
    }
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
    if (field === 'event') {
      this.eventType = value;
    } else if (field === 'data') {
      this.dataLines.push(value);
    }
    return null;
  }
}
