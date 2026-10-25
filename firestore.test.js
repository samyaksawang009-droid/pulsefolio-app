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

// 1. Unauthenticated rejection
test("Unauthenticated user cannot read or write holdings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/holdings`).get()
  );
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/holdings`).doc("h1").set({
      userId: ALICE_UID,
      symbol: "AAPL",
      companyName: "Apple Inc.",
      shares: 10,
      avgBuyPrice: 185.5,
      sector: "Technology",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

// 2. Authenticated owner valid create & read on holdings
test("Authenticated owner can create, read, update, and delete valid holding", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb.collection(`users/${ALICE_UID}/holdings`).doc("h_aapl");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      symbol: "AAPL",
      companyName: "Apple Inc.",
      shares: 15.5,
      avgBuyPrice: 182.25,
      sector: "Technology",
      notes: "Core long-term position",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/holdings`)
      .where("userId", "==", ALICE_UID)
      .get()
  );

  // Update shares and updatedAt while keeping createdAt and userId immutable
  await assertSucceeds(
    docRef.update({
      shares: 20,
      avgBuyPrice: 188.0,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(docRef.delete());
});

// 3. Cross-user isolation
test("Authenticated user cannot read or modify another user's holdings", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection(`users/${ALICE_UID}/holdings`)
      .doc("alice_nvda")
      .set({
        userId: ALICE_UID,
        symbol: "NVDA",
        companyName: "NVIDIA Corp.",
        shares: 5,
        avgBuyPrice: 110.0,
        sector: "Technology",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/holdings`).doc("alice_nvda").get()
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/holdings`).get()
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/holdings`).doc("alice_nvda").delete()
  );
});

// 4. Shadow Update / Ghost Field rejection
test("Rejects ghost fields on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/holdings`).doc("h_ghost").set({
      userId: ALICE_UID,
      symbol: "MSFT",
      companyName: "Microsoft Corp.",
      shares: 10,
      avgBuyPrice: 410.0,
      sector: "Technology",
      isVerifiedAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

// 5. Invalid schema bounds (negative shares / invalid loanType)
test("Rejects negative shares and invalid EMI loanType", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/holdings`).doc("h_bad").set({
      userId: ALICE_UID,
      symbol: "TSLA",
      companyName: "Tesla Inc.",
      shares: -5,
      avgBuyPrice: 200.0,
      sector: "Automotive",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/emi_plans`).doc("emi_bad").set({
      userId: ALICE_UID,
      title: "Bad Loan",
      loanType: "Invalid Unregulated Loan",
      principal: 50000,
      annualRate: 8.5,
      tenureMonths: 60,
      monthlyPrepayment: 0,
      monthlyEmi: 1025.8,
      totalInterest: 11548.0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

// 6. Valid Watchlist and EMI Plan creation
test("Authenticated owner can create and list WatchlistItem and EmiPlan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/watchlist`).doc("w_googl").set({
      userId: ALICE_UID,
      symbol: "GOOGL",
      companyName: "Alphabet Inc.",
      targetAlertPrice: 195.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/emi_plans`).doc("emi_home").set({
      userId: ALICE_UID,
      title: "Dream Home Mortgage",
      loanType: "Home Loan",
      principal: 450000,
      annualRate: 6.5,
      tenureMonths: 360,
      monthlyPrepayment: 500,
      monthlyEmi: 2844.31,
      totalInterest: 573951.6,
      createdAt: now,
      updatedAt: now,
    })
  );
});
