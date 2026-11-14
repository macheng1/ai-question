-- Manual initialization for an EMPTY MySQL 8 database only.
-- Schema snapshot: commit 1cbbf1a, version 50. Not an automatic application migration.
-- Run with mysql without --force before starting the application.
-- Default administrator: 13800000000 / 12345678. Change password after login.
-- Includes a version 50 BASELINE record so existing V1-V50 migrations/seeds are not replayed.
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `unit_name` varchar(160) DEFAULT NULL,
  `province` varchar(80) DEFAULT NULL,
  `city` varchar(80) DEFAULT NULL,
  `district` varchar(80) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `phone` varchar(20) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role_code` varchar(30) NOT NULL DEFAULT 'CANDIDATE',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`),
  UNIQUE KEY `uk_app_user_name` (`name`),
  KEY `idx_app_user_role` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `answer_text` text,
  `correct_answer` text,
  `is_correct` tinyint(1) DEFAULT NULL,
  `score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `answered_at` timestamp(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_question` (`attempt_id`,`question_id`),
  KEY `fk_answer_question` (`question_id`),
  CONSTRAINT `fk_answer_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `assessment_attempt` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `question_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `participant_id` bigint NOT NULL,
  `attempt_no` int NOT NULL DEFAULT '1',
  `status` varchar(20) NOT NULL DEFAULT 'IN_PROGRESS',
  `total_score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `passed` tinyint(1) NOT NULL DEFAULT '0',
  `started_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `submitted_at` timestamp(3) NULL DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_number` (`participant_id`,`attempt_no`),
  KEY `fk_attempt_task` (`task_id`),
  CONSTRAINT `fk_attempt_participant` FOREIGN KEY (`participant_id`) REFERENCES `assessment_participant` (`id`),
  CONSTRAINT `fk_attempt_task` FOREIGN KEY (`task_id`) REFERENCES `assessment_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_attempt_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_question_snapshot` (`attempt_id`,`question_id`),
  KEY `fk_attempt_question_question` (`question_id`),
  KEY `idx_attempt_question_attempt` (`attempt_id`),
  CONSTRAINT `fk_attempt_question_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `assessment_attempt` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_attempt_question_question` FOREIGN KEY (`question_id`) REFERENCES `question_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `candidate_name` varchar(80) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `employee_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_participant_user` (`user_id`),
  KEY `idx_participant_task` (`task_id`),
  KEY `idx_participant_phone` (`phone`),
  KEY `idx_participant_employee` (`employee_id`,`task_id`),
  CONSTRAINT `fk_participant_task` FOREIGN KEY (`task_id`) REFERENCES `assessment_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_participant_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_roster` (
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `task_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `department_name` varchar(100) NOT NULL,
  `candidate_name` varchar(100) NOT NULL,
  `phone` varchar(50) NOT NULL,
  PRIMARY KEY (`task_id`,`employee_id`),
  UNIQUE KEY `roster_phone` (`task_id`,`phone`),
  CONSTRAINT `assessment_roster_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `assessment_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_scope_target` (
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `task_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  PRIMARY KEY (`task_id`,`target_id`),
  CONSTRAINT `assessment_scope_target_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `assessment_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(160) NOT NULL,
  `paper_id` bigint NOT NULL,
  `start_at` timestamp(3) NOT NULL,
  `end_at` timestamp(3) NOT NULL,
  `duration_minutes` int NOT NULL DEFAULT '30',
  `candidate_scope` varchar(20) NOT NULL DEFAULT 'ALL',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `allow_retake` tinyint(1) NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '1',
  `shuffle_questions` tinyint(1) NOT NULL DEFAULT '0',
  `shuffle_options` tinyint(1) NOT NULL DEFAULT '0',
  `show_result_immediately` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `training_managed` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_assessment_task_paper` (`paper_id`),
  KEY `fk_assessment_task_creator` (`created_by`),
  KEY `idx_assessment_task_time` (`start_at`,`end_at`),
  KEY `idx_assessment_task_status` (`status`),
  CONSTRAINT `fk_assessment_task_creator` FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`),
  CONSTRAINT `fk_assessment_task_paper` FOREIGN KEY (`paper_id`) REFERENCES `test_paper` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `candidate_learning_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `candidate_name` varchar(100) NOT NULL,
  `phone` varchar(50) NOT NULL,
  `token` char(36) NOT NULL,
  `training_id` bigint DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `started_at` timestamp(3) NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` timestamp(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  UNIQUE KEY `candidate_task` (`task_id`,`candidate_name`,`phone`),
  UNIQUE KEY `candidate_training` (`training_id`,`candidate_name`,`phone`),
  KEY `idx_learning_employee` (`employee_id`,`training_id`,`task_id`),
  CONSTRAINT `candidate_learning_session_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `assessment_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_training` FOREIGN KEY (`training_id`) REFERENCES `training` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `candidate_material_progress` (
  `session_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `page_number` int NOT NULL,
  `studied_seconds` double NOT NULL DEFAULT '0',
  `completed` tinyint(1) NOT NULL DEFAULT '0',
  `last_seen` timestamp(3) NULL DEFAULT NULL,
  `first_seen` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` timestamp(3) NULL DEFAULT NULL,
  `heartbeat_token` char(36) DEFAULT NULL,
  PRIMARY KEY (`session_id`,`material_id`,`page_number`),
  KEY `material_id` (`material_id`,`page_number`),
  CONSTRAINT `candidate_material_progress_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `candidate_learning_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `candidate_material_progress_ibfk_2` FOREIGN KEY (`material_id`, `page_number`) REFERENCES `training_material_page` (`material_id`, `page_number`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `candidate_video_progress` (
  `session_id` bigint NOT NULL,
  `video_id` bigint NOT NULL,
  `position_seconds` double NOT NULL DEFAULT '0',
  `completed` tinyint(1) NOT NULL DEFAULT '0',
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `baseline_position` double NOT NULL DEFAULT '0',
  `baseline_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `studied_seconds` double DEFAULT NULL,
  `last_play_position` double DEFAULT NULL,
  `first_studied_at` timestamp(3) NULL DEFAULT NULL,
  `completed_at` timestamp(3) NULL DEFAULT NULL,
  PRIMARY KEY (`session_id`,`video_id`),
  KEY `video_id` (`video_id`),
  CONSTRAINT `candidate_video_progress_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `candidate_learning_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `candidate_video_progress_ibfk_2` FOREIGN KEY (`video_id`) REFERENCES `training_video` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `original_name` varchar(255) NOT NULL,
  `object_key` varchar(500) NOT NULL,
  `object_url` varchar(1000) DEFAULT NULL,
  `content_type` varchar(120) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `storage_type` varchar(20) NOT NULL DEFAULT 'OSS',
  `uploaded_by` bigint DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_file_object_uploader` (`uploaded_by`),
  CONSTRAINT `fk_file_object_uploader` FOREIGN KEY (`uploaded_by`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kaoshi_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kaoshi_name` varchar(160) NOT NULL,
  `candidate_name` varchar(80) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `score` int NOT NULL DEFAULT '0',
  `total_score` int NOT NULL DEFAULT '100',
  `pass_score` int NOT NULL DEFAULT '60',
  `passed` tinyint(1) NOT NULL DEFAULT '0',
  `submitted_at` timestamp(3) NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kaoshi_record_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learner_paper_binding` (
  `owner_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `paper_id` bigint NOT NULL,
  `training_ids` json NOT NULL,
  `legacy_conflict` tinyint(1) NOT NULL DEFAULT '0',
  `bound_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`owner_id`,`employee_id`,`paper_id`),
  KEY `ix_binding_owner_paper` (`owner_id`,`paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_clock` (
  `phone` varchar(50) NOT NULL,
  `credited_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `module_name` varchar(60) NOT NULL,
  `operation_type` varchar(30) NOT NULL,
  `request_uri` varchar(500) DEFAULT NULL,
  `request_method` varchar(10) DEFAULT NULL,
  `trace_id` varchar(80) DEFAULT NULL,
  `result_code` varchar(40) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `fk_operation_log_user` (`user_id`),
  KEY `idx_operation_log_created_at` (`created_at`),
  KEY `idx_operation_log_trace_id` (`trace_id`),
  CONSTRAINT `fk_operation_log_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_department` (
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_by` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  KEY `idx_department_owner` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_employee` (
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_by` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `phone` varchar(50) NOT NULL,
  `job_title` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `email` varchar(254) DEFAULT NULL,
  `employee_no` varchar(100) DEFAULT NULL,
  `data_source` varchar(30) NOT NULL DEFAULT 'MANUAL',
  `learner_type` varchar(20) NOT NULL DEFAULT 'EXTERNAL',
  `gender` varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  `remark` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `employee_owner_phone` (`created_by`,`phone`),
  UNIQUE KEY `uk_employee_phone` (`phone`),
  KEY `idx_employee_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_external_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL,
  `source_system` varchar(50) NOT NULL,
  `entity_type` varchar(20) NOT NULL,
  `external_id` varchar(191) NOT NULL,
  `local_id` bigint NOT NULL,
  `external_updated_at` datetime(3) DEFAULT NULL,
  `last_synced_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_identity` (`enterprise_id`,`source_system`,`entity_type`,`external_id`),
  UNIQUE KEY `uk_external_local` (`enterprise_id`,`source_system`,`entity_type`,`local_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_self_test` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bank_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `single_count` int NOT NULL DEFAULT '30',
  `multiple_count` int NOT NULL DEFAULT '10',
  `true_false_count` int NOT NULL DEFAULT '10',
  `random_order` tinyint(1) NOT NULL DEFAULT '1',
  `published` tinyint(1) NOT NULL DEFAULT '1',
  `sort_no` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_self_test_bank` (`bank_id`),
  CONSTRAINT `fk_public_self_test_bank` FOREIGN KEY (`bank_id`) REFERENCES `question_bank` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(120) NOT NULL,
  `question_count` int NOT NULL DEFAULT '0',
  `category` varchar(40) NOT NULL,
  `source` varchar(255) DEFAULT NULL,
  `published` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_question_bank_category` (`category`),
  KEY `idx_question_bank_published` (`published`),
  KEY `idx_question_bank_name` (`name`),
  KEY `idx_question_bank_created_by` (`created_by`),
  CONSTRAINT `fk_question_bank_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_bank_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_bank_category_name_parent` (`name`,`parent_id`),
  KEY `idx_question_bank_category_parent` (`parent_id`),
  KEY `idx_question_bank_category_created_by` (`created_by`),
  CONSTRAINT `fk_question_bank_category_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_question_bank_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `question_bank_category` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bank_id` bigint NOT NULL,
  `question_type` varchar(20) NOT NULL,
  `stem` text NOT NULL,
  `answer_text` text,
  `analysis` text,
  `tags` varchar(500) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `difficulty` varchar(20) NOT NULL DEFAULT 'MEDIUM',
  `score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `fk_question_item_creator` (`created_by`),
  KEY `idx_question_item_bank` (`bank_id`),
  KEY `idx_question_item_type` (`question_type`),
  KEY `idx_question_item_status` (`status`),
  CONSTRAINT `fk_question_item_bank` FOREIGN KEY (`bank_id`) REFERENCES `question_bank` (`id`),
  CONSTRAINT `fk_question_item_creator` FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `option_key` varchar(10) NOT NULL,
  `option_text` text NOT NULL,
  `is_correct` tinyint(1) NOT NULL DEFAULT '0',
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_option_key` (`question_id`,`option_key`),
  CONSTRAINT `fk_question_option_question` FOREIGN KEY (`question_id`) REFERENCES `question_item` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_news` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `content` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `published_at` timestamp(3) NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_site_news_status_published` (`status`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_paper` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(160) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `mode` varchar(20) NOT NULL DEFAULT 'FIXED',
  `total_score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `pass_score` decimal(8,2) NOT NULL DEFAULT '60.00',
  `duration_minutes` int NOT NULL DEFAULT '30',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `fk_test_paper_creator` (`created_by`),
  CONSTRAINT `fk_test_paper_creator` FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_paper_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paper_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`paper_id`,`question_id`),
  KEY `fk_paper_question_question` (`question_id`),
  KEY `idx_paper_question_paper` (`paper_id`),
  CONSTRAINT `fk_paper_question_paper` FOREIGN KEY (`paper_id`) REFERENCES `test_paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_paper_question_question` FOREIGN KEY (`question_id`) REFERENCES `question_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_paper_random_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paper_id` bigint NOT NULL,
  `bank_id` bigint NOT NULL,
  `single_count` int NOT NULL DEFAULT '0',
  `single_score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `multiple_count` int NOT NULL DEFAULT '0',
  `multiple_score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `true_false_count` int NOT NULL DEFAULT '0',
  `true_false_score` decimal(8,2) NOT NULL DEFAULT '0.00',
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_random_paper_bank` (`paper_id`,`bank_id`),
  KEY `fk_random_bank_bank` (`bank_id`),
  CONSTRAINT `fk_random_bank_bank` FOREIGN KEY (`bank_id`) REFERENCES `question_bank` (`id`),
  CONSTRAINT `fk_random_bank_paper` FOREIGN KEY (`paper_id`) REFERENCES `test_paper` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(160) NOT NULL,
  `description` varchar(2000) NOT NULL DEFAULT '',
  `survey_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `questions_json` json NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `assessment_id` bigint DEFAULT NULL,
  `category` varchar(30) NOT NULL DEFAULT 'UNCATEGORIZED',
  `requirement_type` varchar(20) NOT NULL DEFAULT 'REQUIRED',
  `audience_type` varchar(20) NOT NULL DEFAULT 'USERS',
  `purpose` varchar(20) NOT NULL DEFAULT 'STANDALONE',
  `end_at` datetime(3) DEFAULT NULL COMMENT '培训结束时间，独立于考试时间',
  `start_at` datetime(3) DEFAULT NULL,
  `copied_assessment_id` bigint DEFAULT NULL COMMENT 'Exam generated for this training copy',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_assessment` (`assessment_id`),
  KEY `idx_training_owner` (`created_by`),
  KEY `fk_training_copied_assessment` (`copied_assessment_id`),
  CONSTRAINT `fk_training_assessment` FOREIGN KEY (`assessment_id`) REFERENCES `assessment_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_training_copied_assessment` FOREIGN KEY (`copied_assessment_id`) REFERENCES `assessment_task` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_allocation_lock` (
  `owner_id` bigint NOT NULL,
  PRIMARY KEY (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_audience` (
  `training_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `candidate_name` varchar(100) NOT NULL,
  `phone` varchar(50) NOT NULL,
  `department_id` bigint NOT NULL,
  `department_name` varchar(100) NOT NULL,
  `assigned_at` timestamp(3) NULL DEFAULT NULL,
  PRIMARY KEY (`training_id`,`employee_id`),
  UNIQUE KEY `audience_phone` (`training_id`,`phone`),
  CONSTRAINT `training_audience_ibfk_1` FOREIGN KEY (`training_id`) REFERENCES `training` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `training_id` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `material_type` varchar(10) NOT NULL,
  `storage_key` varchar(255) NOT NULL,
  `page_count` int NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `training_id` (`training_id`),
  CONSTRAINT `training_material_ibfk_1` FOREIGN KEY (`training_id`) REFERENCES `training` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_material_page` (
  `material_id` bigint NOT NULL,
  `page_number` int NOT NULL,
  `required_seconds` int NOT NULL DEFAULT '120',
  PRIMARY KEY (`material_id`,`page_number`),
  CONSTRAINT `training_material_page_ibfk_1` FOREIGN KEY (`material_id`) REFERENCES `training_material` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_reminder_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `training_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `request_id` varchar(36) NOT NULL,
  `email` varchar(254) NOT NULL,
  `status` varchar(16) NOT NULL,
  `message` varchar(255) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reminder_request` (`training_id`,`employee_id`,`request_id`),
  KEY `idx_reminder_history` (`training_id`,`employee_id`,`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_scope_target` (
  `training_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  PRIMARY KEY (`training_id`,`target_id`),
  CONSTRAINT `training_scope_target_ibfk_1` FOREIGN KEY (`training_id`) REFERENCES `training` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_survey_response` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `training_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `answers_json` json NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `employee_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_response` (`training_id`,`user_id`),
  UNIQUE KEY `uk_training_employee` (`training_id`,`employee_id`),
  CONSTRAINT `fk_training_response` FOREIGN KEY (`training_id`) REFERENCES `training` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_video` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `training_id` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `storage_path` varchar(100) NOT NULL,
  `content_type` varchar(50) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `training_id` (`training_id`),
  CONSTRAINT `training_video_ibfk_1` FOREIGN KEY (`training_id`) REFERENCES `training` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_video_progress` (
  `video_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `position_seconds` double NOT NULL DEFAULT '0',
  `completed` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`video_id`,`user_id`),
  CONSTRAINT `training_video_progress_ibfk_1` FOREIGN KEY (`video_id`) REFERENCES `training_video` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_question_bank_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `bank_id` bigint NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_bank` (`user_id`,`bank_id`),
  KEY `fk_favorite_bank` (`bank_id`),
  KEY `idx_favorite_user` (`user_id`),
  CONSTRAINT `fk_favorite_bank` FOREIGN KEY (`bank_id`) REFERENCES `question_bank` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;




/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

INSERT INTO `app_user` (`id`, `name`, `unit_name`, `province`, `city`, `district`, `address`, `phone`, `password_hash`, `role_code`, `status`, `created_at`, `updated_at`) VALUES (1,'默认企业管理员','默认演示企业',NULL,NULL,NULL,NULL,'13800000000','$2a$10$cQTudbrttVss2dmHaAK.beFCFLjHcalef6osNDdX2XDThuotMeh5e','CANDIDATE','ACTIVE','2026-11-13 12:16:11.116','2026-11-13 12:16:11.116');
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO flyway_schema_history
(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (1, '50', 'Manual schema baseline', 'BASELINE', 'Manual schema baseline', NULL, CURRENT_USER(), 0, 1);
