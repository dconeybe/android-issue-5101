import type { AddressInfo, Socket } from 'node:net';
import { Server } from 'node:net';

import type { App as FirebaseApp } from 'firebase-admin/app';
import { initializeApp as initializeFirebaseApp } from 'firebase-admin/app';
import type { AppCheck as FirebaseAppCheck } from 'firebase-admin/app-check';
import { getAppCheck as getFirebaseAppCheck } from 'firebase-admin/app-check';
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
  const appCheck = getFirebaseAppCheck(app);

  await listFirestoreDocuments(app);
  await runServer(appCheck, TCP_PORT);
}

async function runServer(appCheck: FirebaseAppCheck, port: number) {
  logger.info(`Starting the TCP server on port ${port}`);
  const server = new Server();

  server.on('listening', () =>
    logger.debug(
      `Server is now listening on ${descriptionForAddress(server.address())}`
    )
  );
  server.on('drop', data =>
    logger.warn(
      `Server dropped connection from ${data?.remoteAddress}:${data?.remotePort}`
    )
  );
  server.on('connection', socket => handleConnection(appCheck, socket));
  server.listen({ host: '127.0.0.1', port });

  await new Promise((resolve, reject) => {
    server.on('close', resolve);
    server.on('error', reject);
  });
}

function handleConnection(appCheck: FirebaseAppCheck, socket: Socket) {
  const connectionId = `connection_id=${generateRandomAlphaString(8)}`;
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

  logger.info(`Generating AppCheck token for ${connectionId}`);
  appCheck
    .createToken(APP_ID, { ttlMillis: MILLIS_FOR_30_MINUTES })
    .then(token => {
      logger.info(
        `Generated AppCheck token for ${connectionId}: ` +
          `token=${token.token}` +
          `ttlMillis=${token.ttlMillis}`
      );
      socket.end(token.token);
    })
    .catch(err => {
      socket.destroy(new Error(err));
      logger.error(`appCheck.createToken() for ${connectionId} failed`, err);
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

async function listFirestoreDocuments(app: FirebaseApp) {
  const firestore = new Firestore(app);
  logger.info('Getting collection contents');
  const snapshot = await firestore.collection('AndroidIssue5101').get();
  logger.info(`Got ${snapshot.size} documents`);
  for (const document of snapshot.docs) {
    logger.note(document.ref.path);
  }
}

function descriptionForAddress(address: AddressInfo | string | null): string {
  if (address === null) {
    return 'null';
  } else if (typeof address === 'string') {
    return address;
  } else {
    return `${address.address}:${address.port}`;
  }
}

main();
