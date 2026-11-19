// The other half of contract.ts: the types there say what an answer holds, and this checks that every endpoint the
// panel calls is still in the API description with that method, and that every type the panel reads an answer into
// is one contract.ts compares with the API. The endpoints come from ./openapi.ts, generated from docs/openapi.json.
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['./*.ts', '!./*.test.ts', '!./openapi.ts', '!./contract.ts'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;
const contractSource = Object.values(
  import.meta.glob('./contract.ts', { query: '?raw', import: 'default', eager: true }) as Record<string, string>,
)[0]!;
const generated = Object.values(
  import.meta.glob('./openapi.ts', { query: '?raw', import: 'default', eager: true }) as Record<string, string>,
)[0]!;

const PLACEHOLDER = '{}';
// Read with a generic type that names no API schema: nothing to compare
const NOT_SCHEMAS = new Set(['void', 'unknown', 'T']);

interface Call {
  file: string;
  method: string;
  path: string;
  answer: ts.TypeNode | undefined;
}

function parse(file: string, text: string): ts.SourceFile {
  return ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
}

/** String constants of every API file, such as EVENTS_PATH, which calls use instead of a literal. */
function constants(): Map<string, string> {
  const found = new Map<string, string>();
  for (const [file, text] of Object.entries(sources)) {
    parse(file, text).forEachChild(function visit(node) {
      if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer
        && ts.isStringLiteralLike(node.initializer)) {
        found.set(node.name.text, node.initializer.text);
      }
      node.forEachChild(visit);
    });
  }
  return found;
}

/** The path a call goes to, with each value it fills in as {} and without the query string. */
function pathOf(node: ts.Expression, known: Map<string, string>): string | undefined {
  let path: string | undefined;
  if (ts.isStringLiteralLike(node)) {
    path = node.text;
  } else if (ts.isIdentifier(node)) {
    path = known.get(node.text);
  } else if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'url') {
    return node.arguments[0] && pathOf(node.arguments[0], known);
  } else if (ts.isTemplateExpression(node)) {
    path = node.head.text;
    for (const span of node.templateSpans) {
      const constant = ts.isIdentifier(span.expression) ? known.get(span.expression.text) : undefined;
      path += (constant ?? PLACEHOLDER) + span.literal.text;
    }
  }
  if (path === undefined) {
    return undefined;
  }
  // A value glued to the end of the path, like documents${query}, is a query string
  return path.split('?')[0]!.replace(/([^/])\{\}$/, '$1');
}

function methodOf(options: ts.Expression | undefined, fallback: string): string {
  if (!options || !ts.isObjectLiteralExpression(options)) {
    return fallback;
  }
  let sendsBody = false;
  for (const property of options.properties) {
    if (!ts.isPropertyAssignment(property) && !ts.isShorthandPropertyAssignment(property)) {
      continue;
    }
    const name = property.name.getText();
    if (name === 'method' && ts.isPropertyAssignment(property) && ts.isStringLiteralLike(property.initializer)) {
      return property.initializer.text.toUpperCase();
    }
    sendsBody ||= name === 'json' || name === 'body';
  }
  // As http-client's send: a body without a method is a POST
  return sendsBody ? 'POST' : fallback;
}

function calls(): Call[] {
  const known = constants();
  const found: Call[] = [];
  for (const [file, text] of Object.entries(sources)) {
    parse(file, text).forEachChild(function visit(node) {
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.arguments[0]) {
        const callee = node.expression.text;
        const path = pathOf(node.arguments[0], known);
        if (path?.startsWith('/api/')) {
          const answer = node.typeArguments?.[0];
          if (callee === 'request') {
            found.push({ file, method: methodOf(node.arguments[1], 'GET'), path, answer });
          } else if (callee === 'openStream') {
            found.push({ file, method: methodOf(node.arguments[2], 'GET'), path, answer });
          } else if (callee === 'uploadWithProgress') {
            found.push({ file, method: 'POST', path, answer });
          } else if (callee === 'fetch') {
            found.push({ file, method: methodOf(node.arguments[1], 'GET'), path, answer });
          }
        }
      }
      node.forEachChild(visit);
    });
  }
  return found;
}

const METHODS = new Set(['get', 'put', 'post', 'delete', 'patch']);

/** Every operation of the generated `paths` interface; a method the path does not have is typed `never`. */
function describedEndpoints(): { method: string; segments: string[] }[] {
  const endpoints: { method: string; segments: string[] }[] = [];
  parse('openapi.ts', generated).forEachChild((node) => {
    if (!ts.isInterfaceDeclaration(node) || node.name.text !== 'paths') {
      return;
    }
    for (const path of node.members) {
      if (!ts.isPropertySignature(path) || !ts.isStringLiteral(path.name) || !path.type
        || !ts.isTypeLiteralNode(path.type)) {
        continue;
      }
      for (const operation of path.type.members) {
        const method = operation.name?.getText();
        const described = ts.isPropertySignature(operation) && operation.type
          && operation.type.kind !== ts.SyntaxKind.NeverKeyword;
        if (method && METHODS.has(method) && described) {
          endpoints.push({ method: method.toUpperCase(), segments: path.name.text.split('/') });
        }
      }
    }
  });
  return endpoints;
}

function isDescribed(call: Call, endpoints: { method: string; segments: string[] }[]): boolean {
  const segments = call.path.split('/');
  return endpoints.some(
    (endpoint) =>
      endpoint.method === call.method &&
      endpoint.segments.length === segments.length &&
      endpoint.segments.every((segment, index) => {
        const own = segments[index]!;
        return segment === own || (segment.startsWith('{') && own === PLACEHOLDER);
      }),
  );
}

/**
 * The types an answer is read into, as contract.ts writes them: Customer[] reads Customer, and Page<Customer> reads
 * both the page and Customer. An answer typed in place, such as { url: string }, has no name contract.ts could use,
 * so it is reported as it is written.
 */
function namesIn(type: ts.TypeNode | undefined): string[] {
  if (!type || type.kind === ts.SyntaxKind.VoidKeyword) {
    return [];
  }
  if (ts.isArrayTypeNode(type)) {
    return namesIn(type.elementType);
  }
  if (ts.isTypeReferenceNode(type)) {
    const name = type.typeName.getText();
    if (name === 'Page') {
      return [type.getText(), ...namesIn(type.typeArguments?.[0])];
    }
    return NOT_SCHEMAS.has(name) ? [] : [name];
  }
  return [type.getText()];
}

function comparedTypes(): Set<string> {
  const compared = new Set<string>();
  parse('contract.ts', contractSource).forEachChild(function visit(node) {
    if (ts.isTypeReferenceNode(node) && node.typeName.getText() === 'Reads' && node.typeArguments?.[0]) {
      compared.add(node.typeArguments[0].getText());
    }
    node.forEachChild(visit);
  });
  return compared;
}

describe('the API the panel calls', () => {
  const found = calls();

  it('finds the calls it checks', () => {
    expect(found.length).toBeGreaterThan(80);
  });

  it('is described in docs/openapi.json, method and path', () => {
    const endpoints = describedEndpoints();
    expect(endpoints.length).toBeGreaterThan(80);
    const missing = found.filter((call) => !isDescribed(call, endpoints)).map((call) => `${call.method} ${call.path}`);
    expect(missing).toEqual([]);
  });

  it('answers with types contract.ts compares with the API', () => {
    const compared = comparedTypes();
    const unchecked = [
      ...new Set(found.flatMap((call) => namesIn(call.answer)).filter((name) => !compared.has(name))),
    ];
    expect(unchecked).toEqual([]);
  });
});
