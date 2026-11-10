-- 兼容手动初始化入口；通常由应用启动时的 Flyway 自动执行，无需另行运行。
-- 如需手动执行，请从 backend 目录调用 mysql，且勿使用 --force。
SET NAMES utf8mb4;
SOURCE src/main/resources/db/migration/V27_2__seed_demo_training_exams.sql;
