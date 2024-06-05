import { initializeApp } from 'firebase-admin/app';
import { Firestore } from 'firebase-admin/firestore';
import * as signale from 'signale';

const logger = new signale.Signale({
  config: {
    displayTimestamp: true
  }
});

async function main() {
  logger.info('Initializing firebase-admin sdk');
  const app = initializeApp();

  const firestore = new Firestore(app);
  logger.info('Getting collection contents');
  const snapshot = await firestore.collection('AndroidIssue5101').get();
  logger.info(`Got ${snapshot.size} documents`);
  for (const document of snapshot.docs) {
    logger.note(document.ref.path);
  }
}

main();
