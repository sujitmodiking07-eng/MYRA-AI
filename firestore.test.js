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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write preferences, sessions, memories, or automations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("automations").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("preferences").doc("config").get()
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's sessions, memories, or preferences", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem1").set({
      userId: ALICE_UID,
      content: "Injected",
      category: "general",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("preferences").doc("config").get()
  );
});

test("Owner can create and update valid UserPreferences, but shadow fields and invalid enums fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("config");

  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      orbStyle: "energy",
      language: "hinglish",
      autoSpeak: true,
      hapticsEnabled: true,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    prefRef.update({
      orbStyle: "neon",
      updatedAt: new Date(),
    })
  );

  // Ghost / shadow field rejected
  await assertFails(
    prefRef.update({
      orbStyle: "classic",
      isAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Invalid enum rejected
  await assertFails(
    prefRef.update({
      orbStyle: "invalid_orb",
      updatedAt: new Date(),
    })
  );
});

test("Owner can create session and child messages; orphan message fails; update on message fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  const msgRef = sessionRef.collection("messages").doc("msg_1");

  // Orphan message without existing parent session fails
  await assertFails(
    msgRef.set({
      userId: ALICE_UID,
      role: "user",
      content: "Hello MYRA",
      createdAt: new Date(),
    })
  );

  // Create parent session
  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      title: "Morning Check-in",
      lastMessagePreview: "Hello MYRA",
      language: "en",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Create valid message inside session
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "user",
      content: "Hello MYRA",
      createdAt: new Date(),
    })
  );

  // Update on immutable message fails
  await assertFails(
    msgRef.update({
      content: "Tampered",
    })
  );
});

test("Owner can create, update, list, and delete MemoryNote and AutomationWorkflow", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const memRef = aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1");
  const autoRef = aliceDb.collection("users").doc(ALICE_UID).collection("automations").doc("auto_1");

  await assertSucceeds(
    memRef.set({
      userId: ALICE_UID,
      content: "I am an Android developer",
      category: "work",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    autoRef.set({
      userId: ALICE_UID,
      title: "Daily Tech Briefing",
      triggerPhrase: "tech briefing",
      promptTemplate: "Summarize top 3 Android and AI trends concisely.",
      category: "briefing",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("automations").get());
  await assertSucceeds(memRef.delete());
  await assertSucceeds(autoRef.delete());
});
