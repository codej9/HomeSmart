-- Run once on an existing HomeSmart schema before deploying the timestamp-aware application.
ALTER TABLE `devices`
    ADD COLUMN `last_updated` datetime(6) DEFAULT NULL
        COMMENT 'UTC time of the last successful device update';
