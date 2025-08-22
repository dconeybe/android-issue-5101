import dedent from 'dedent';
import { getReasonPhrase, getStatusCode } from 'http-status-codes';
import ms from 'ms';
import yargs from 'yargs';
import { type Options } from 'yargs';
import { hideBin } from 'yargs/helpers';

export interface ForcedResponse {
  code: number;
  reason: string;
}

export interface ParsedArgs {
  host: string;
  port: number;
  forcedResponse: ForcedResponse | undefined;
  forcedToken: string | undefined;
  forcedTtlMillis: number | undefined;
}

export async function parseArgs(): Promise<ParsedArgs> {
  const yargsResult = await yargs(hideBin(process.argv))
    .usage(USAGE)
    .epilogue(EPILOGUE)
    .options(OPTIONS)
    .help()
    .version(false)
    .showHelpOnFail(false, 'Run with --help for help')
    .strict()
    .parseAsync();

  return {
    host: yargsResult.host,
    port: yargsResult.port,
    forcedResponse: yargsResult.responseCode,
    forcedToken: yargsResult.token,
    forcedTtlMillis: yargsResult.ttl
  };
}

const USAGE = dedent`
  Usage: $0 [options]

  Runs an HTTP server that can be used as a custom AppCheck provider.
  `;

const EPILOGUE = dedent`
  The GOOGLE_APPLICATION_CREDENTIALS environment variable must be set to the
  path of the JSON file with the project information.

  HTTP requests received by this server must have content type
  application/x-www-form-urlencoded or application/json.
  Using x-www-form-urlencoded is useful from browsers as it avoids CORS
  preflight requests.

  Requests must have two keys:
    projectId: The Firebase project id (e.g. "my-project").
    appId: The ID of the Firebase app (e.g. "1:1234567890:web:a892437b8923")
  Both of these values can be retrieved from the Firebase Console at
  https://console.firebase.google.com. The "projectId" must match the value
  indicated by GOOGLE_APPLICATION_CREDENTIALS and the "appId" will be
  validated by the Firebase App Check backend.

  An example application/json request body is:
    {"projectId":"my-project","appId":"1:1234567890:web:a892437b8923"}
  A JavaScript application can create this string as follows:
    JSON.stringify({"projectId": "my-project", "appId": "1:1234567890:web:a892437b8923"})

  An example application/x-www-form-urlencoded request body is:
    projectId=my-project&appId=1%3A1234567890%3Aweb%3Aa892437b8923
  A JavaScript application can create this string as follows:
    const body = new URLSearchParams();
    body.append("projectId", "my-project");
    body.append("appId", "1:1234567890:web:a892437b8923");

  On success, the response body will have application/json Content-Type and
  will have two keys:
     token: a string whose value is the AppCheck token
     ttlMillis: a number whose value is the amount of time, in milliseconds,
       that the token is valid

  Here is sample JavaScript code that can communicate with this server:
    const body = new URLSearchParams();
    body.append("projectId", "my-project");
    body.append("appId", "1:1234567890:web:a892437b8923");

    const response = await fetch('http://localhost:9392', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body
    });

    const data = await response.json();
    const token: AppCheckToken = {
      token: data.token,
      expireTimeMillis: Date.now() + data.ttlMillis
    };
  `;

function coercePort(value: string): number {
  const port = Number.parseInt(value);
  if (!Number.isInteger(port)) {
    throw new Error(`invalid port: ${value} (must be a number)`);
  } else if (port < 0) {
    throw new Error(
      `invalid port: ${value} (must be greater than or equal to zero)`
    );
  }
  return port;
}

function coerceTtl(value: string): number {
  const ttlMillis: unknown = ms(value as ms.StringValue);
  if (typeof ttlMillis !== 'number' || !Number.isFinite(ttlMillis)) {
    throw new Error(`invalid TTL: "${value}" (unable to parse)`);
  }
  if (ttlMillis < 0) {
    throw new Error(
      collapseWhitespace(`
      invalid TTL: ${value} (${ttlMillis} milliseconds)
      (must be greater than or equal to zero)
    `)
    );
  }
  return ttlMillis;
}

function coerceResponseCode(value: string): ForcedResponse {
  try {
    const statusCode = getStatusCode(value);
    return { code: statusCode, reason: value };
  } catch (_) {
    // value is not an HTTP reason phrase.
  }

  try {
    const reasonPhrase = getReasonPhrase(value);
    return { code: Number.parseInt(value), reason: reasonPhrase };
  } catch (_) {
    // value is not an HTTP status code.
  }

  throw new Error('invalid HTTP response code or reason phrase: ' + value);
}

function collapseWhitespace(s: string): string {
  return s.replaceAll(/\s+/gv, ' ').trim();
}

const OPTIONS = {
  host: {
    alias: 'h',
    string: true,
    default: '127.0.0.1',
    describe: collapseWhitespace(`
      The network interface on which the HTTP server will listen.
    `)
  },
  port: {
    alias: 'p',
    string: true,
    default: 0,
    coerce: coercePort,
    describe: collapseWhitespace(`
      The TCP port to which the HTTP server will bind;
      if 0 (zero) a random available port will be chosen.
    `)
  },
  ttl: {
    string: true,
    coerce: coerceTtl,
    describe: collapseWhitespace(`
      The TTL (time to live) of the AppCheck token to report in the response
      body, overriding the TTL indicated in the response from the App Check
      server.

      The value is specified as a number (in milliseconds)
      or a string (e.g. "5m", "1h").
      See https://github.com/vercel/ms for the list of supported string formats.

      Note that this value does not affect the actual TTL of the token, which is
      always 30 minutes, but, rather, the value sent back in the HTTP response
      bodies from this HTTP server.
    `)
  },
  responseCode: {
    alias: 'r',
    string: true,
    coerce: coerceResponseCode,
    describe: collapseWhitespace(`
      The HTTP response code to unconditionally return.

      Specifying an HTTP response code will cause the HTTP server to not "do"
      anything and unconditionally return the specified response code. This is
      useful for simulating error conditions for testing purposes.

      The response code can be specified either as a number (e.g. 200, 418)
      or a reason phrase (e.g. "OK", "I'm a teapot").
    `)
  },
  token: {
    string: true,
    describe: collapseWhitespace(`
      The token to return instead of getting one from the App Check
      server.

      When specified, a TTL of 30 minutes will be used
      (unless overridden by the --ttl flag).
      Also, when specified, no validation of projectId or appId in HTTP requests
      will be done.
    `)
  }
} as const satisfies Record<string, Options>;
