# Data protection

Athletes are mostly children. The rules the app follows, and the places where a decision belongs to the programme, not to us.

## Principles in the build

1. **Video stays on the phone.** Each attempt is analysed on the phone. Video is not sent anywhere unless the assessor ticks the
   upload option on the Server sync card, which is off by default.
2. **No consent, no assessment.** An athlete can be added to a session only after a signed consent. Under 18 needs a parent or
   guardian. The server also refuses an athlete without one.
3. **Data minimisation.** The analytics zone has a pseudonym, age and age band, gender, assessment month, best results and
   confidence. No name, school, ID, signer, assessor, video key, free text or exact date.
4. **Withdrawal.** `DELETE /v1/athletes/{uid}` (admin) erases the identified record, the signed consent, attempts, uploaded media files
   and the de-identified subject (unless the same person is still held under another phone's record). On the phone, the athlete's
   record can be removed by clearing it from the app's storage.
5. **No commercial cloud.** Inference is on the phone. The sync service is to be hosted by the programme. Tesseract.js (optional OCR)
   and the CDN fallback for the pose files are the only third-party requests, and they carry no athlete data.

## Data inventory

| Data | Where | Why | Sent to the server |
|---|---|---|---|
| Name, age, gender, school, programme ID | Phone (localStorage); server identified zone | Identify the athlete, set the record | On sync, with consent |
| Parental height (mother, father; measured or self-reported) | Same | Optional input to an adult-height estimate (not computed here) | Only if its consent was given |
| Signed consent: statements agreed, signer, relationship, time, signature image | Phone (signature in IndexedDB); server identified zone | Evidence of consent | On sync |
| Attempts: value, unit, confidence, method, rule audit, declaration | Phone; server identified zone, best result in both zones | The result and its audit | On sync |
| Attempt videos | Phone (IndexedDB) | Audit of each attempt | Only if the upload option is ticked |
| Scale display photo | Phone (IndexedDB) | Audit of the weight | Same |
| Assessor name | Phone | Printed on the report | On sync |

## Retention

- On the phone: until the assessor deletes it. There is no automatic deletion yet.
- On the server: results and consent until withdrawn. Media have a retention date from `BFA_RETENTION_DAYS`; `POST
  /v1/admin/purge-expired` deletes those past it. **The real period is set by SAI's service level agreement** (rulings C14 and C36)
  and has not been given to us, so it defaults to 0 (keep until purged by hand). Set it before use.

## Access

Three API key scopes: `device` (the app writes), `export` (reads the de-identified zone), `admin` (identified data, withdrawal,
purge). For a hardened deployment give each zone its own PostgreSQL schema and role, so the export role cannot read an identity.
The pseudonym secret is configuration, kept out of the analytics environment, and must not change once data exists.

## Draft consent wording (for review)

This is what the app shows today. It was written from the rulings only. **It must be reviewed by whoever owns SAI's data-protection
terms before use**, in particular the retention wording, the claim of de-identification before sharing (the gateway is built but
has not been audited), and the statement that video is not uploaded (the optional upload contradicts it if it is switched on).

The athlete, or a parent or guardian for an athlete under 18, agrees to each of the first three statements (required) and may
agree to the fourth:

1. *[Name] may take part in the SAI Battery Fitness Assessment (ten physical fitness tests) and the results may be recorded.*
2. *Each test attempt is recorded on video, and the weight display is photographed, on the assessor's phone. The video is analysed
   on that phone and kept there for audit for the period set by SAI's service level agreement. It is not uploaded to any commercial
   cloud service.*
3. *Name, age, gender, school, programme ID and test results may be shared with SAI and NeGD for the programme through its secure sync
   after de-identification, and with the athlete, guardian and school.*
4. *Optional: the heights of the biological mother and father may be recorded, only as an indicative input for estimating expected adult
   height. If this is not ticked, any parental height already entered is removed.*

*Consent can be withdrawn at any time by telling the assessor or the school. Data is handled under SAI's data-protection procedures.*

The signer's name, relationship (self, parent, guardian), the time, which statements were agreed, the assessor's name and the drawn
signature are stored. The app can produce the signed form as an A4 PDF. A Hindi version is in the app.

## Authenticity declaration

Before recording, the athlete (or parent or guardian) and the coach each confirm that the recordings of that test are live,
unedited attempts that follow the test procedure. The declaration (athlete, assessor, guardian or not, time) is stored with each
video (ruling C43). Official wording, if SAI has any, should replace the draft.

## Not done

- A data-protection impact assessment, a privacy notice for parents, and a breach procedure: these belong to the programme.
- Encryption of the phone's storage beyond what the operating system provides.
- Automatic deletion on the phone and a retention schedule agreed with SAI.
- An audit of the de-identification gateway by a third party.
- eSign for the programme's own signed documents (ruling C35), which are not part of the app.
