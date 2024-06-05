import type { Socket } from 'node:net';
import { Server } from 'node:net';

import { appCheck as firebaseAppCheck } from 'firebase-admin';
import type { App as FirebaseApp } from 'firebase-admin/app';
import { initializeApp as initializeFirebaseApp } from 'firebase-admin/app';
import type { AppCheck as FirebaseAppCheck } from 'firebase-admin/app-check';
import { Firestore } from 'firebase-admin/firestore';
import * as signale from 'signale';

const logger = new signale.Signale({
  config: {
    displayTimestamp: true
  }
});

const TCP_PORT = 9392;

// The "App ID" of the "com.google.dconeybe" Android app from Firebase console.
const APP_ID = '1:35775074661:android:bda3aad6830ebc96c4d18c';

const MILLIS_FOR_30_MINUTES = 1000 * 60 * 60;

async function main() {
  logger.info('Initializing firebase-admin sdk');
  const app = initializeFirebaseApp();
  const appCheck = firebaseAppCheck(app);

  await listFirestoreDocuments(app);
  await runServer(appCheck, TCP_PORT);
}

async function runServer(appCheck: FirebaseAppCheck, port: number) {
  logger.info(`Starting the TCP server on port ${port}`);
  const server = new Server();
  attachLoggingHooks(server);

  server.on('connection', socket => handleConnection(appCheck, socket));
  server.listen({ host: '127.0.0.1', port });

  await new Promise((resolve, reject) => {
    server.on('close', resolve);
    server.on('error', reject);
  });
}

function handleConnection(appCheck: FirebaseAppCheck, socket: Socket) {
  logger.info('Generating AppCheck token');
  const createTokenPromise = appCheck.createToken(
    '1:35775074661:android:bda3aad6830ebc96c4d18c',
    {
      ttlMillis: MILLIS_FOR_30_MINUTES
    }
  );

  createTokenPromise.then(token => {
    logger.info(`Generated AppCheck token`);
    logger.note(`  token: ${token.token}`);
    logger.note(`  ttlMillis: ${token.ttlMillis}`);
    socket.end(token.token);
  });

  createTokenPromise.catch(err => {
    socket.destroy(new Error(err));
    logger.error(`appCheck.createToken() failed`, err);
  });
}

function attachLoggingHooks(server: Server) {
  server.on('listening', () =>
    logger.debug(`Server is now listening on ${server.address()}`)
  );

  server.on('connection', socket => {
    const connectionId = `con${generateRandomAlphaString(8)}`;
    logger.debug(
      `Server got connection ${connectionId} ` +
        `from ${socket.remoteAddress}:${socket.remotePort}`
    );
    socket.on('close', () => {
      logger.debug(
        `Connection ${connectionId} from ` +
          `${socket.remoteAddress}:${socket.remotePort} closed`
      );
    });
    socket.on('end', () => {
      logger.debug(
        `Connection ${connectionId} from ` +
          `${socket.remoteAddress}:${socket.remotePort} ended`
      );
    });
    socket.on('error', err => {
      logger.warn(
        `Connection ${connectionId} from ` +
          `${socket.remoteAddress}:${socket.remotePort} ERRORED: ${err}`
      );
    });
  });
  server.on('drop', data =>
    logger.warn(
      `Server dropped connection from ${data?.remoteAddress}:${data?.remotePort}`
    )
  );
}

function generateRandomAlphaString(length: number): string {
  const characters = 'bcdefghjkmnpqrstvwxyz';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * characters.length));
  }
  return result;
}

async function listFirestoreDocuments(app: FirebaseApp) {
  const firestore = new Firestore(app);
  logger.info('Getting collection contents');
  const snapshot = await firestore.collection('AndroidIssue5101').get();
  logger.info(`Got ${snapshot.size} documents`);
  for (const document of snapshot.docs) {
    logger.note(document.ref.path);
  }
}

main();
