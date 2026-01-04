# PLAN Component Test Case - QA Validation

## APK Location
```
c:\Users\sonic\CascadeProjects\ble-mesh-multiplatform\android\app\build\outputs\apk\debug\app-debug.apk
```

---

## TEST CASE 1: Chimney Inspection Plan

### Step 1: Open Plan Component
1. Navigate to Plan from Dashboard
2. You should see a blank canvas with placeholder text

### Step 2: Enter This Test Plan
Copy and paste this exact text into the Plan canvas:

```
# PLAN
## Scope
Chimney inspection and repair for residential property in Bayside, Queens.
Exterior brick chimney, approximately 20 feet tall.

## Assumptions
- Standard roof access via ladder
- No hazmat conditions
- Weather permits masonry work
- Client has approved inspection

## Tasks
Perform on-site visual inspection
Document cracks and spalling with photos
Assess mortar joint condition
Check chimney crown and flashing
Inspect flue liner from roof level
Generate condition report

## Materials
3 boxes Tuckpointing mortar
20 replacement bricks
1 gallon waterproof sealer
Scaffolding rental

## Labor
4h Lead Mason inspection
8h Tuckpointing work
2h Cleanup and sealing

## Exclusions
- Interior fireplace work
- Unrelated roof repairs
- Chimney sweep services

## Summary
Full chimney inspection with photo documentation.
Repair scope to be determined after inspection.
Estimated 2-3 days for repairs if approved.
```

---

## TEST CASE 2: Verify Button Behavior

### COMPILE Button (Main Path)
1. With plan text entered, status should show `PLAN [DRAFT]`
2. Click `[COMPILE]` button
3. **Expected:**
   - Status changes to `PLAN [COMPILING...]`
   - Then changes to `PLAN [COMPILED]`
   - Right panel shows "EXECUTION ITEMS" with tasks, materials, labor
   - OUTPUTS section shows:
     - `[P] Proposal ● GENERATE →` (available)
     - `[R] Report ● GENERATE →` (available)
     - `[I] Invoice LOCKED` (needs transfer + time)

### TEST Button (Online Path)
1. Clear the plan and enter new text:
```
wire a shed
```
2. Click `[TEST ⧉]` button (orange)
3. **Expected (Online):**
   - If online: Content transforms with scaffolded structure
   - You see trade classification, phases, safety items
   - Compilation proceeds automatically
4. **Expected (Offline/Airplane Mode):**
   - Toggle airplane mode ON
   - Click TEST
   - Falls back to local scaffold (adds `# PLAN` / `## Tasks` wrapper)
   - Compilation still works

---

## TEST CASE 3: Job Board Verification

### Navigate to Job Board
1. Go to Dashboard → Job Board
2. Open any job (or create new one)

### Verify NO Document Buttons
**CRITICAL CHECK:** The job detail dialog should NOT show:
- ❌ Proposal button
- ❌ Report button  
- ❌ Invoice button

Instead, when job is DONE, you should see:
- ✓ "Generate documents in Plan component" text hint
- ✓ Archive button only

**If you see Proposal/Report/Invoice buttons in Job Board → TEST FAILS**

---

## TEST CASE 4: Document Generation from Plan

### Generate Proposal
1. After COMPILE in Plan, click `[P] Proposal GENERATE →`
2. Preview dialog should show:
   - "PROPOSAL" header
   - TASKS section with 6 items
   - MATERIALS section with 4 items
   - LABOR section with 3 items
   - ESTIMATES summary

### Generate Report
1. Click `[R] Report GENERATE →`
2. Preview should show:
   - "WORK REPORT" header
   - Work summary with tasks/materials/labor
   - Observations from execution items

### Invoice (Locked)
1. `[I] Invoice` should show LOCKED status
2. Prerequisites: "Select execution items" → "Transfer to Job Board"

---

## TEST CASE 5: Transfer Flow

1. In COMPILED state, check some execution items
2. Click `[SELECT ALL]` then `[TRANSFER X TO JOBS]`
3. Navigate to Job Board
4. Verify new jobs appear with status BACKLOG
5. Return to Plan - Invoice should still show requirements for time logging

---

## LOGCAT VERIFICATION

Filter by tag `PlanViewModel` to see:
- `COMPILE (MAIN): Starting local-only compile path`
- `TEST ⧉: Starting ISOLATED test compile path`
- `TEST ⧉: ONLINE - invoking OnlineResolver`
- `TEST ⧉: OFFLINE - skipping OnlineResolver`

These logs confirm the two paths are isolated.

---

## PASS/FAIL CRITERIA

| Test | Pass Condition |
|------|----------------|
| Compile works offline | Plan compiles without network |
| Test uses online resolver | Logcat shows OnlineResolver invoked |
| Job Board has no doc buttons | No Proposal/Report/Invoice in Job Board |
| Documents appear in Plan | After compile, OUTPUTS section visible |
| Proposal generates correctly | Shows tasks/materials/labor |
| Invoice is locked | Shows prerequisites until transfer+time |

---

## Report Results
```
DATE: ___________
TESTER: ___________

TEST 1 (Chimney Plan Compile): PASS / FAIL
TEST 2 (Test vs Compile): PASS / FAIL
TEST 3 (Job Board No Docs): PASS / FAIL
TEST 4 (Document Generation): PASS / FAIL
TEST 5 (Transfer Flow): PASS / FAIL

OVERALL: PASS / FAIL

NOTES:
_________________________________________________
_________________________________________________
```
