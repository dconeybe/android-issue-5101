import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';

import dedent from 'dedent';
import type { App as FirebaseApp } from 'firebase-admin/app';
import { initializeApp as initializeFirebaseApp } from 'firebase-admin/app';
import type { AppCheck as FirebaseAppCheck } from 'firebase-admin/app-check';
import { getAppCheck as getFirebaseAppCheck } from 'firebase-admin/app-check';
import {
  getReasonPhrase,
  getStatusCode,
  ReasonPhrases,
  StatusCodes
} from 'http-status-codes';
import * as signale from 'signale';
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';

const logger = new signale.Signale({
  config: {
    displayTimestamp: true
  }
});

const MILLIS_PER_SECOND = 1000;
const MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60;
const MILLIS_FOR_30_MINUTES = MILLIS_PER_MINUTE * 30;

async function main() {
  logger.info('Initializing firebase-admin sdk');
  const { host, port, forcedResponse } = await parseArgs();
  const app = initializeFirebaseApp();
  const appCheck = getFirebaseAppCheck(app);
  const projectId = projectIdFromFirebaseApp(app);
  logger.info(`Initialized firebase-admin sdk for project: ${projectId}`);
  await runServer({ appCheck, projectId, host, port, forcedResponse });
}

function projectIdFromFirebaseApp(app: FirebaseApp): string | undefined {
  const options = app.options;
  if (options.projectId) {
    return options.projectId;
  }

  const credential = app.options.credential;
  if (
    credential &&
    'projectId' in credential &&
    typeof credential.projectId === 'string'
  ) {
    return credential.projectId;
  }

  return undefined;
}

async function runServer(settings: {
  appCheck: FirebaseAppCheck;
  host: string;
  port: number;
  projectId?: string | undefined;
  forcedResponse?: ForcedResponse | undefined;
}) {
  const { appCheck, host, port, projectId, forcedResponse } = settings;

  const httpServer = createServer((request, response) => {
    const requestId = generateRandomAlphaString(6);
    logger.info(
      `[requestId_${requestId}] Request received from: ` +
        descriptionForAddress(request.socket.address())
    );

    const respondWithError = (
      code: number,
      reason: string,
      message: string
    ) => {
      logger.warn(
        `[requestId_${requestId}] Request failed: ` +
          `${code} (${reason}): ${message}`
      );
      response.writeHead(code, reason, {
        'Content-Type': 'text/plain',
        'Access-Control-Allow-Origin': '*'
      });
      response.end(message);
    };

    if (forcedResponse) {
      const { code, reason } = forcedResponse;
      respondWithError(
        code,
        reason,
        `Unconditionally returned HTTP response code ${code} (${reason})`
      );
      return;
    }

    const requestMethod = request.method;
    if (requestMethod !== 'POST') {
      respondWithError(
        StatusCodes.METHOD_NOT_ALLOWED,
        ReasonPhrases.METHOD_NOT_ALLOWED,
        `Only POST requests are supported, but got: ${requestMethod}`
      );
      return;
    }

    const contentType = request.headers['content-type'];
    if (
      contentType !== 'application/json' &&
      contentType !== 'application/x-www-form-urlencoded'
    ) {
      respondWithError(
        StatusCodes.UNSUPPORTED_MEDIA_TYPE,
        ReasonPhrases.UNSUPPORTED_MEDIA_TYPE,
        `Content-Type must be application/json or ` +
          `application/x-www-form-urlencoded, but got: ${contentType}`
      );
      return;
    }

    const chunks: Array<Uint8Array> = [];
    request.on('data', chunk => {
      chunks.push(chunk);
    });

    request.on('end', () => {
      let bodyText: string;
      try {
        bodyText = Buffer.concat(chunks).toString();
      } catch (e: unknown) {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `Decoding the request body as UTF-8 failed: ${e}`
        );
        return;
      }

      logger.debug(`[requestId_${requestId}] Request body: ${bodyText}`);
      let body: unknown;
      try {
        if (contentType === 'application/json') {
          body = JSON.parse(bodyText);
        } else {
          const searchParams = new URLSearchParams(bodyText);
          body = {
            appId: searchParams.get('appId'),
            projectId: searchParams.get('projectId')
          };
        }
      } catch (e: unknown) {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `Parsing the ${contentType} request body failed: ${e}`
        );
        return;
      }

      if (body === null) {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `The request body must be an object, but got: null`
        );
        return;
      }
      if (typeof body !== 'object') {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `The request body must be an object, but got: ${typeof body}`
        );
        return;
      }

      if (!('appId' in body)) {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          "The request body must have an 'appId' property, " +
            'but got properties: ' +
            Object.getOwnPropertyNames(body).sort().join(', ')
        );
        return;
      }

      const appId = body['appId'];
      if (typeof appId !== 'string') {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `The 'appId' property of the JSON request body must be a string, ` +
            `but got: ${typeof appId}`
        );
        return;
      }

      if (!('projectId' in body)) {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          "The JSON request body must have an 'projectId' property, " +
            'but got properties: ' +
            Object.getOwnPropertyNames(body).sort().join(', ')
        );
        return;
      }

      const projectIdFromRequest = body['projectId'];
      if (typeof projectId !== 'string') {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `The 'projectId' property of the JSON request body must be a string, ` +
            `but got: ${typeof projectId}`
        );
        return;
      }

      if (projectId && projectIdFromRequest !== projectId) {
        respondWithError(
          StatusCodes.BAD_REQUEST,
          ReasonPhrases.BAD_REQUEST,
          `The 'projectId' property of the JSON request body ` +
            `was expected to be "${projectId}", ` +
            `but got: "${projectIdFromRequest}"`
        );
        return;
      }

      logger.debug(
        `[requestId_${requestId}] Creating App Check token for appId=${appId}`
      );
      appCheck
        .createToken(appId, { ttlMillis: MILLIS_FOR_30_MINUTES })
        .then(appCheckToken => {
          const responseBody = JSON.stringify({
            token: appCheckToken.token,
            ttlMillis: appCheckToken.ttlMillis
          });
          logger.debug(
            `[requestId_${requestId}] Got App Check token: ${responseBody}`
          );
          response.writeHead(StatusCodes.OK, ReasonPhrases.OK, {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          });
          response.end(responseBody);
        })
        .catch((err: unknown) => {
          respondWithError(
            StatusCodes.INTERNAL_SERVER_ERROR,
            ReasonPhrases.INTERNAL_SERVER_ERROR,
            `Creating App Check token failed: ${err}`
          );
        });
    });
  });

  httpServer.listen(port, host, () => {
    logger.info('Listening on ' + descriptionForAddress(httpServer.address()));
    if (forcedResponse) {
      logger.note(
        'HTTP server will unconditionally return HTTP status code ' +
          `${forcedResponse.code} (${forcedResponse.reason})`
      );
    }
  });

  return new Promise((resolve, reject) => {
    httpServer.on('close', resolve);
    httpServer.on('error', reject);
  });
}

function generateRandomAlphaString(length: number): string {
  const characters = 'bcdefghjkmnpqrstvwxyz';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * characters.length));
  }
  return result;
}

function descriptionForAddress(
  address: AddressInfo | object | string | null
): string {
  if (address === null) {
    return 'null';
  } else if (typeof address === 'string') {
    return address;
  } else if ('address' in address && 'port' in address) {
    return `${address.address}:${address.port}`;
  } else {
    return `${address}`;
  }
}

interface ForcedResponse {
  code: number;
  reason: string;
}

interface ParsedArgs {
  host: string;
  port: number;
  forcedResponse: ForcedResponse | undefined;
}

async function parseArgs(): Promise<ParsedArgs> {
  const yargsResult = await yargs(hideBin(process.argv))
    .usage(
      dedent`
      Usage: $0 [options]

      Runs an HTTP server that can be used as a custom AppCheck provider.
      `
    )
    .epilogue(
      dedent`
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
      `
    )
    .option('host', {
      alias: 'h',
      string: true,
      default: '127.0.0.1',
      describe: 'The network interface on which the HTTP server will listen.'
    })
    .option('port', {
      alias: 'p',
      string: true,
      default: 0,
      coerce: (value: string): number => {
        const parsedValue = Number.parseInt(value);
        if (!Number.isInteger(parsedValue)) {
          throw new Error(`invalid port: ${value} (must be a number)`);
        } else if (parsedValue < 0) {
          throw new Error(
            `invalid port: ${value} (must be greater than or equal to zero)`
          );
        }
        return parsedValue;
      },
      describe:
        'The TCP port to which the HTTP server will bind; ' +
        'if 0 (zero) a random available port will be chosen.'
    })
    .option('responseCode', {
      alias: 'r',
      string: true,
      coerce: (value: string): ForcedResponse => {
        try {
          const statusCode = getStatusCode(value);
          return { code: statusCode, reason: value };
        } catch (_) {
          // value is not an HTTP status code.
        }

        try {
          const reasonPhrase = getReasonPhrase(value);
          return { code: Number.parseInt(value), reason: reasonPhrase };
        } catch (_) {
          // value is not an HTTP reason phrase.
        }

        throw new Error(
          'invalid HTTP response code or reason phrase: ' + value
        );
      },
      describe:
        'The HTTP response code to unconditionally return. ' +
        'Specifying an HTTP response code will cause the HTTP server to ' +
        'not "do" anything and unconditionally return the specified response ' +
        'code. This is useful for simulating error conditions for testing ' +
        'purposes. This can be specified as a number (e.g. 200, 418) or a ' +
        'reason phrase (e.g. "OK", "I\'m a teapot").'
    })
    .help()
    .version(false)
    .showHelpOnFail(false, 'Run with --help for help')
    .strict()
    .parseAsync();

  return {
    host: yargsResult.host,
    port: yargsResult.port,
    forcedResponse: yargsResult.responseCode
  };
}

main();
