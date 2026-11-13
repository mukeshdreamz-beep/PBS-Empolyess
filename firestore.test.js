const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "admin@pbs.com";
const EMP_UID = "emp_uid_1";
const EMP_EMAIL = "worker@pbs.com";
const OUTSIDER_UID = "outsider_uid_9";
const WORKSPACE_ID = "PBS-MAIN";
const SAMPLE_SELFIE = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8U";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs() {
  return new Date(Date.now() - 1000);
}

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

async function seedWorkspaceAndEmployee() {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const wsRef = adminDb.collection("workspaces").doc(WORKSPACE_ID);
  await assertSucceeds(
    wsRef.set({
      ownerId: ADMIN_UID,
      workspaceName: "PBS Main",
      adminUids: [ADMIN_UID],
      authorizedAdminEmails: [ADMIN_EMAIL],
      policyDefaultInMinutes: 570,
      policyDefaultOutMinutes: 1095,
      policyFullDayThresholdMinutes: 525,
      policyOvertimeStartMinutes: 1140,
      policyBreakDeductionMinutes: 0,
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    wsRef.collection("members").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      role: "ADMIN",
      displayName: "PBS Admin",
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    wsRef.collection("employees").doc("emp_1").set({
      localId: 1,
      adminUids: [ADMIN_UID],
      claimedByUid: "",
      name: "Aarav Sharma",
      employeeCode: "PBS-101",
      site: "Sai Baba Nagar",
      role: "Technician",
      phone: "9876543210",
      dailyWage: 800,
      otHourlyRate: 120,
      loginId: "PBS-101",
      passwordHash: "a".repeat(64),
      passwordUpdatedAt: 1000,
      createdAtMs: 1000,
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    wsRef.collection("employee_auth").doc("PBS-101").set({
      loginId: "PBS-101",
      passwordHash: "a".repeat(64),
      employeeRemoteId: "emp_1",
      employeeLocalId: 1,
      name: "Aarav Sharma",
      site: "Sai Baba Nagar",
      role: "Technician",
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  // Employee joins workspace with role EMPLOYEE and binds verified loginId
  const empDb = testEnv.authenticatedContext(EMP_UID, {
    email: EMP_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    empDb.collection("workspaces").doc(WORKSPACE_ID).collection("members").doc(EMP_UID).set({
      userId: EMP_UID,
      role: "EMPLOYEE",
      employeeLoginId: "PBS-101",
      employeeLocalId: 1,
      displayName: "Aarav Sharma",
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    empDb.collection("workspaces").doc(WORKSPACE_ID).collection("employees").doc("emp_1").update({
      claimedByUid: EMP_UID,
      serverUpdatedAt: serverTs(),
    })
  );

  return { adminDb, empDb };
}

test("Unauthenticated caller is rejected across collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("workspaces").doc(WORKSPACE_ID).get());
  await assertFails(
    unauthDb.collection("workspaces").doc(WORKSPACE_ID).collection("attendance").get()
  );
});

test("Employee cannot self-escalate member role to ADMIN (Privilege Escalation Guard)", async () => {
  await seedWorkspaceAndEmployee();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "spoof@evil.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    outsiderDb.collection("workspaces").doc(WORKSPACE_ID).collection("members").doc(OUTSIDER_UID).set({
      userId: OUTSIDER_UID,
      role: "ADMIN",
      displayName: "Attacker",
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );
});

test("Email spoofing with email_verified=false is blocked from Admin workspace access", async () => {
  await seedWorkspaceAndEmployee();
  const spoofDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("workspaces").doc(WORKSPACE_ID).collection("members").doc(OUTSIDER_UID).set({
      userId: OUTSIDER_UID,
      role: "ADMIN",
      displayName: "Spoofed Admin",
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );
});

test("Employee self attendance requires selfie and location for Check-In and Check-Out and locks afterwards", async () => {
  const { adminDb, empDb } = await seedWorkspaceAndEmployee();
  const attRef = empDb
    .collection("workspaces")
    .doc(WORKSPACE_ID)
    .collection("attendance")
    .doc("att_emp_1_13-11-2026");

  // 1. Fails if selfie or location missing on Check-In
  await assertFails(
    attRef.set({
      adminUids: [ADMIN_UID],
      employeeUid: EMP_UID,
      employeeId: 1,
      employeeRemoteId: "emp_1",
      employeeLoginId: "PBS-101",
      date: "13/11/2026",
      inMinutes: 570,
      outMinutes: 570,
      status: "PRESENT",
      hasCheckedIn: true,
      hasCheckedOut: false,
      markedByRole: "EMPLOYEE",
      checkInTimestamp: 1700000000000,
      checkOutTimestamp: 1700000000000,
      updatedAtMs: 1700000000000,
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  // 2. Succeeds Check-In with selfie + GPS coordinates
  await assertSucceeds(
    attRef.set({
      adminUids: [ADMIN_UID],
      employeeUid: EMP_UID,
      employeeId: 1,
      employeeRemoteId: "emp_1",
      employeeLoginId: "PBS-101",
      date: "13/11/2026",
      inMinutes: 570,
      outMinutes: 570,
      status: "PRESENT",
      note: "Self check-in",
      hasCheckedIn: true,
      hasCheckedOut: false,
      markedByRole: "EMPLOYEE",
      checkInTimestamp: 1700000000000,
      checkOutTimestamp: 1700000000000,
      checkInPhotoBase64: SAMPLE_SELFIE,
      checkInLat: 19.2288,
      checkInLng: 72.8541,
      checkInAddress: "Sai Baba Nagar, Borivali West",
      updatedAtMs: 1700000000000,
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  // 3. Employee cannot alter IN time during Check-Out
  await assertFails(
    attRef.update({
      inMinutes: 540,
      outMinutes: 1140,
      hasCheckedOut: true,
      checkOutTimestamp: 1700030000000,
      checkOutPhotoBase64: SAMPLE_SELFIE,
      checkOutLat: 19.2289,
      checkOutLng: 72.8542,
      checkOutAddress: "Sai Baba Nagar",
      updatedAtMs: 1700030000000,
      serverUpdatedAt: serverTs(),
    })
  );

  // 4. Employee succeeds Check-Out ONCE with selfie + GPS
  await assertSucceeds(
    attRef.update({
      outMinutes: 1140,
      hasCheckedOut: true,
      checkOutTimestamp: 1700030000000,
      checkOutPhotoBase64: SAMPLE_SELFIE,
      checkOutLat: 19.2289,
      checkOutLng: 72.8542,
      checkOutAddress: "Sai Baba Nagar",
      updatedAtMs: 1700030000000,
      serverUpdatedAt: serverTs(),
    })
  );

  // 5. Employee CANNOT update or delete attendance after Check-Out
  await assertFails(
    attRef.update({
      outMinutes: 1200,
      updatedAtMs: 1700035000000,
      serverUpdatedAt: serverTs(),
    })
  );
  await assertFails(attRef.delete());

  // 6. Admin CAN update OT / timings for ANY employee and read full photo+location report
  const adminAttRef = adminDb
    .collection("workspaces")
    .doc(WORKSPACE_ID)
    .collection("attendance")
    .doc("att_emp_1_13-11-2026");

  await assertSucceeds(
    adminAttRef.update({
      outMinutes: 1200,
      note: "Admin verified OT",
      updatedAtMs: 1700040000000,
      serverUpdatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    adminDb
      .collection("workspaces")
      .doc(WORKSPACE_ID)
      .collection("attendance")
      .where("adminUids", "array-contains", ADMIN_UID)
      .get()
  );

  await assertSucceeds(
    empDb
      .collection("workspaces")
      .doc(WORKSPACE_ID)
      .collection("attendance")
      .where("employeeUid", "==", EMP_UID)
      .get()
  );

  // Query Enforcer: unfiltered collection list must fail
  await assertFails(
    empDb.collection("workspaces").doc(WORKSPACE_ID).collection("attendance").get()
  );
});

test("Employee can submit PENDING expense only; Admin can approve and mark claimed", async () => {
  const { adminDb, empDb } = await seedWorkspaceAndEmployee();
  const expRef = empDb
    .collection("workspaces")
    .doc(WORKSPACE_ID)
    .collection("expenses")
    .doc("exp_1");

  // Employee cannot auto-approve own expense
  await assertFails(
    expRef.set({
      adminUids: [ADMIN_UID],
      employeeUid: EMP_UID,
      localId: 1,
      employeeId: 1,
      employeeLoginId: "PBS-101",
      employeeName: "Aarav Sharma",
      site: "Sai Baba Nagar",
      title: "Travel",
      category: "Travel",
      amount: 250,
      date: "13/11/2026",
      status: "APPROVED",
      isClaimed: false,
      approvedAt: 0,
      claimedAt: 0,
      createdAtMs: 1700000000000,
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  // Employee creates PENDING expense
  await assertSucceeds(
    expRef.set({
      adminUids: [ADMIN_UID],
      employeeUid: EMP_UID,
      localId: 1,
      employeeId: 1,
      employeeLoginId: "PBS-101",
      employeeName: "Aarav Sharma",
      site: "Sai Baba Nagar",
      title: "Travel",
      category: "Travel",
      amount: 250,
      date: "13/11/2026",
      status: "PENDING",
      isClaimed: false,
      approvedAt: 0,
      claimedAt: 0,
      createdAtMs: 1700000000000,
      serverCreatedAt: serverTs(),
      serverUpdatedAt: serverTs(),
    })
  );

  // Employee cannot approve or claim it
  await assertFails(
    expRef.update({
      status: "APPROVED",
      approvedAt: 1700010000000,
      serverUpdatedAt: serverTs(),
    })
  );

  // Admin approves & marks claimed
  const adminExpRef = adminDb
    .collection("workspaces")
    .doc(WORKSPACE_ID)
    .collection("expenses")
    .doc("exp_1");

  await assertSucceeds(
    adminExpRef.update({
      status: "APPROVED",
      approvedAt: 1700010000000,
      isClaimed: true,
      claimedAt: 1700020000000,
      serverUpdatedAt: serverTs(),
    })
  );
});
