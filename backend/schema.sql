-- PostgreSQL schema, generated from app/models.py by scripts/export_contract.py. Do not edit by hand.
-- Identified zone: devices, athletes, consents, attempts, results, media, sync_log.
-- De-identified zone: deid_subjects, deid_results. See README.md for giving each zone its own schema and role.

CREATE TABLE deid_subjects (
	pseudonym VARCHAR(32) NOT NULL, 
	age_years INTEGER NOT NULL, 
	age_band VARCHAR(8) NOT NULL, 
	gender VARCHAR(1) NOT NULL, 
	assessment_month VARCHAR(7) NOT NULL, 
	mother_cm INTEGER, 
	father_cm INTEGER, 
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	PRIMARY KEY (pseudonym)
);

CREATE TABLE devices (
	id VARCHAR(64) NOT NULL, 
	assessor VARCHAR(120) NOT NULL, 
	app_version VARCHAR(32) NOT NULL, 
	first_seen TIMESTAMP WITH TIME ZONE NOT NULL, 
	last_seen TIMESTAMP WITH TIME ZONE NOT NULL, 
	PRIMARY KEY (id)
);

CREATE TABLE sync_log (
	id SERIAL NOT NULL, 
	device_id VARCHAR(64) NOT NULL, 
	received_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	athletes_accepted INTEGER NOT NULL, 
	athletes_rejected INTEGER NOT NULL, 
	attempts_upserted INTEGER NOT NULL, 
	PRIMARY KEY (id)
);
CREATE INDEX ix_sync_log_device_id ON sync_log (device_id);

CREATE TABLE athletes (
	uid VARCHAR(36) NOT NULL, 
	device_id VARCHAR(64) NOT NULL, 
	local_id VARCHAR(40) NOT NULL, 
	pseudonym VARCHAR(32) NOT NULL, 
	name VARCHAR(160) NOT NULL, 
	age_years INTEGER NOT NULL, 
	gender VARCHAR(1) NOT NULL, 
	school VARCHAR(200) NOT NULL, 
	id_type VARCHAR(10) NOT NULL, 
	id_number VARCHAR(40) NOT NULL, 
	assessment_date DATE NOT NULL, 
	mother_cm INTEGER, 
	mother_source VARCHAR(16) NOT NULL, 
	father_cm INTEGER, 
	father_source VARCHAR(16) NOT NULL, 
	created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	PRIMARY KEY (uid), 
	CONSTRAINT uq_athlete_device_local UNIQUE (device_id, local_id), 
	FOREIGN KEY(device_id) REFERENCES devices (id)
);
CREATE INDEX ix_athletes_device_id ON athletes (device_id);
CREATE INDEX ix_athletes_pseudonym ON athletes (pseudonym);

CREATE TABLE deid_results (
	id SERIAL NOT NULL, 
	pseudonym VARCHAR(32) NOT NULL, 
	test_key VARCHAR(32) NOT NULL, 
	status VARCHAR(10) NOT NULL, 
	best_value FLOAT, 
	unit VARCHAR(10) NOT NULL, 
	confidence VARCHAR(5) NOT NULL, 
	attempts_count INTEGER NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_deid_result UNIQUE (pseudonym, test_key), 
	FOREIGN KEY(pseudonym) REFERENCES deid_subjects (pseudonym) ON DELETE CASCADE
);
CREATE INDEX ix_deid_results_pseudonym ON deid_results (pseudonym);

CREATE TABLE attempts (
	id SERIAL NOT NULL, 
	athlete_uid VARCHAR(36) NOT NULL, 
	test_key VARCHAR(32) NOT NULL, 
	attempt_id VARCHAR(120) NOT NULL, 
	attempt_no INTEGER NOT NULL, 
	valid BOOLEAN NOT NULL, 
	value FLOAT, 
	unit VARCHAR(10) NOT NULL, 
	confidence VARCHAR(5) NOT NULL, 
	method TEXT NOT NULL, 
	audit JSON NOT NULL, 
	video_key VARCHAR(120), 
	video_duration_s FLOAT, 
	video_size_bytes INTEGER, 
	declaration JSON, 
	received_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_attempt UNIQUE (athlete_uid, test_key, attempt_id), 
	FOREIGN KEY(athlete_uid) REFERENCES athletes (uid) ON DELETE CASCADE
);
CREATE INDEX ix_attempt_athlete_test ON attempts (athlete_uid, test_key);
CREATE INDEX ix_attempts_athlete_uid ON attempts (athlete_uid);

CREATE TABLE consents (
	id SERIAL NOT NULL, 
	athlete_uid VARCHAR(36) NOT NULL, 
	version INTEGER NOT NULL, 
	signed_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	signer_name VARCHAR(160) NOT NULL, 
	relationship VARCHAR(10) NOT NULL, 
	minor BOOLEAN NOT NULL, 
	item_assess BOOLEAN NOT NULL, 
	item_video BOOLEAN NOT NULL, 
	item_data BOOLEAN NOT NULL, 
	item_parent_height BOOLEAN NOT NULL, 
	assessor VARCHAR(120) NOT NULL, 
	signature_png BYTEA, 
	received_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_consent_athlete_time UNIQUE (athlete_uid, signed_at), 
	FOREIGN KEY(athlete_uid) REFERENCES athletes (uid) ON DELETE CASCADE
);
CREATE INDEX ix_consents_athlete_uid ON consents (athlete_uid);

CREATE TABLE media (
	id SERIAL NOT NULL, 
	athlete_uid VARCHAR(36) NOT NULL, 
	test_key VARCHAR(32) NOT NULL, 
	attempt_id VARCHAR(120), 
	kind VARCHAR(10) NOT NULL, 
	key VARCHAR(120) NOT NULL, 
	mime VARCHAR(60) NOT NULL, 
	size_bytes INTEGER NOT NULL, 
	sha256 VARCHAR(64) NOT NULL, 
	path TEXT NOT NULL, 
	uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	retain_until TIMESTAMP WITH TIME ZONE, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_media UNIQUE (athlete_uid, key), 
	FOREIGN KEY(athlete_uid) REFERENCES athletes (uid) ON DELETE CASCADE
);
CREATE INDEX ix_media_athlete_uid ON media (athlete_uid);

CREATE TABLE results (
	id SERIAL NOT NULL, 
	athlete_uid VARCHAR(36) NOT NULL, 
	test_key VARCHAR(32) NOT NULL, 
	status VARCHAR(10) NOT NULL, 
	best_value FLOAT, 
	unit VARCHAR(10) NOT NULL, 
	confidence VARCHAR(5) NOT NULL, 
	attempts_count INTEGER NOT NULL, 
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_result UNIQUE (athlete_uid, test_key), 
	FOREIGN KEY(athlete_uid) REFERENCES athletes (uid) ON DELETE CASCADE
);
CREATE INDEX ix_results_athlete_uid ON results (athlete_uid);
