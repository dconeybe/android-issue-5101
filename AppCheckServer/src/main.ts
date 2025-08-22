import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';

import type { App as FirebaseApp } from 'firebase-admin/app';
import { initializeApp as initializeFirebaseApp } from 'firebase-admin/app';
import type { AppCheck as FirebaseAppCheck } from 'firebase-admin/app-check';
import { getAppCheck as getFirebaseAppCheck } from 'firebase-admin/app-check';
import { ReasonPhrases, StatusCodes } from 'http-status-codes';
import ms from 'ms';
import * as signale from 'signale';

import { type ForcedResponse, parseArgs } from './argparse';

const logger = new signale.Signale({
  config: {
    displayTimestamp: true
  }
});

const MILLIS_PER_SECOND = 1000;
const MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60;
const MILLIS_FOR_30_MINUTES = MILLIS_PER_MINUTE * 30;

async function main() {
  const { host, port, forcedResponse, forcedToken, forcedTtlMillis } =
    await parseArgs();
  logger.info('Initializing firebase-admin sdk');
  const app = initializeFirebaseApp();
  const appCheck = getFirebaseAppCheck(app);
  const projectId = projectIdFromFirebaseApp(app);
  logger.info(`Initialized firebase-admin sdk for project: ${projectId}`);
  await runServer({
    appCheck,
    projectId,
    host,
    port,
    forcedResponse,
    forcedToken,
    forcedTtlMillis
  });
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
  forcedToken?: string | undefined;
  forcedTtlMillis?: number | undefined;
}) {
  const {
    appCheck,
    host,
    port,
    projectId,
    forcedResponse,
    forcedToken,
    forcedTtlMillis
  } = settings;

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
    } else if (forcedToken) {
      const ttlMillis = forcedTtlMillis ?? MILLIS_FOR_30_MINUTES;
      const responseBody = JSON.stringify({
        token: forcedToken,
        ttlMillis
      });
      logger.info(
        `[requestId_${requestId}] Sending response: ${responseBody} ` +
          `(ttlMillis=${ms(ttlMillis, { long: true })})`
      );
      response.writeHead(StatusCodes.OK, ReasonPhrases.OK, {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*'
      });
      response.end(responseBody);
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

      logger.info(`[requestId_${requestId}] Request body: ${bodyText}`);
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

      logger.info(
        `[requestId_${requestId}] Creating App Check token for appId=${appId}`
      );
      appCheck
        .createToken(appId, { ttlMillis: MILLIS_FOR_30_MINUTES })
        .then(appCheckToken => {
          logger.info(
            `[requestId_${requestId}] Created App Check token ` +
              `with ttlMillis=${appCheckToken.ttlMillis} ` +
              `(${ms(appCheckToken.ttlMillis, { long: true })})`
          );
          const ttlMillis = forcedTtlMillis ?? appCheckToken.ttlMillis;
          const responseBody = JSON.stringify({
            token: appCheckToken.token,
            ttlMillis
          });
          logger.info(
            `[requestId_${requestId}] Sending response: ${responseBody} ` +
              `(ttlMillis=${ms(ttlMillis, { long: true })})`
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
    } else {
      if (forcedToken) {
        logger.note(
          `HTTP server will unconditionally return token=${forcedToken}`
        );
      }
      if (forcedTtlMillis) {
        logger.note(
          `HTTP server will unconditionally return ` +
            `ttlMillis=${forcedTtlMillis} (${ms(forcedTtlMillis, { long: true })})`
        );
      }
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

main();
