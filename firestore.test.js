const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users cannot read or write user projects or files", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("prj_1").set({
      userId: ALICE_UID,
      name: "Unauth Site",
      status: "UPLOADED",
      fileCount: 1,
      totalBytes: 120,
      errorsDetected: 0,
      errorsFixed: 0,
      remainingErrors: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create project, files, issues, update repair status, and log downloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("prj_1");
  await assertSucceeds(
    projRef.set({
      userId: ALICE_UID,
      name: "Portfolio Site",
      status: "UPLOADED",
      fileCount: 2,
      totalBytes: 2048,
      errorsDetected: 0,
      errorsFixed: 0,
      remainingErrors: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid file creation
  await assertSucceeds(
    projRef.collection("files").doc("file_1").set({
      userId: ALICE_UID,
      projectId: "prj_1",
      relativePath: "index.html",
      fileType: "HTML",
      sizeBytes: 512,
      originalContent: "<html><body>Hello</body></html>",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid issue creation
  await assertSucceeds(
    projRef.collection("issues").doc("iss_1").set({
      userId: ALICE_UID,
      projectId: "prj_1",
      code: "HTML_MISSING_VIEWPORT",
      category: "RESPONSIVE",
      severity: "CRITICAL",
      filePath: "index.html",
      lineNumber: 1,
      autoFixable: true,
      fixed: false,
      titleEn: "Missing mobile viewport",
      descEn: "Pages need viewport meta tag for phones.",
      fixEn: "Add meta viewport tag.",
      titleHi: "मोबाइल व्यूपोर्ट गायब है",
      descHi: "फ़ोन के लिए व्यूपोर्ट टैग ज़रूरी है।",
      fixHi: "viewport meta टैग जोड़ें।",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid file repair update
  await assertSucceeds(
    projRef.collection("files").doc("file_1").update({
      repairedContent: "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body>Hello</body></html>",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid project repair summary update
  await assertSucceeds(
    projRef.update({
      status: "REPAIRED",
      errorsDetected: 1,
      errorsFixed: 1,
      remainingErrors: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid download log creation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("downloads").doc("dl_1").set({
      userId: ALICE_UID,
      projectId: "prj_1",
      projectName: "Portfolio Site",
      version: "REPAIRED",
      fileCount: 2,
      createdAt: new Date(Date.now() - 500),
    })
  );
});

test("Rejects @DocumentId 'id' leakage, cross-user access, and plan escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Reject extra 'id' field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("prj_bad").set({
      id: "prj_bad",
      userId: ALICE_UID,
      name: "Bad",
      status: "UPLOADED",
      fileCount: 1,
      totalBytes: 100,
      errorsDetected: 0,
      errorsFixed: 0,
      remainingErrors: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject self-upgrading plan to PRO
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredLanguage: "EN",
      plan: "PRO",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create valid Alice project and test Bob isolation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("prj_alice").set({
      userId: ALICE_UID,
      name: "Alice Site",
      status: "UPLOADED",
      fileCount: 1,
      totalBytes: 100,
      errorsDetected: 0,
      errorsFixed: 0,
      remainingErrors: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("prj_alice").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("prj_alice").delete()
  );
});
